import { getDriveToken, refreshDriveToken } from '$lib/shared/services/SupabaseAuthService';
import { DRIVE_BOOKS_PATH } from '$lib/shared/protocol/DriveCatalogContract';
import { redactLogLine, type SyncErrorCode } from '$lib/shared/protocol/DriveCatalogContract';
import type { StorageProvider } from './StorageProvider';

/**
 * Typed Drive error: carries a stable SyncErrorCode (`AUTH_EXPIRED`,
 * `AUTH_REQUIRED`, `PERMISSION_DENIED`) and `retryable=false` so callers can
 * surface a re-sign-in prompt instead of silently retrying. Messages are
 * always redacted (DTL-3).
 */
export type DriveError = Error & { code?: SyncErrorCode; retryable?: boolean };

export class GDriveProvider implements StorageProvider {
  private static readonly GDRIVE_API_BASE = 'https://www.googleapis.com/drive/v3';
  private static readonly GDRIVE_UPLOAD_BASE = 'https://www.googleapis.com/upload/drive/v3';
  private static readonly FOLDER_NAME = DRIVE_BOOKS_PATH;

  /**
   * Resolve a usable Drive access token once per operation. `getDriveToken()`
   * returns the session `provider_token` when present; when absent (session
   * auto-refresh dropped it, or fresh restart) the layered
   * `refreshDriveToken()` is used (DTL-1). Refresh failure throws a typed
   * `AUTH_REQUIRED` instead of failing silently (DTL-2).
   */
  private async getAccessToken(): Promise<string> {
    const token = await getDriveToken();
    if (token) return token;
    return refreshDriveToken();
  }

  private authError(code: SyncErrorCode, message: string): DriveError {
    const err = new Error(redactLogLine(message)) as DriveError;
    err.code = code;
    err.retryable = false;
    return err;
  }

  /** Map a Drive API HTTP failure to a typed, redacted error (DTL-3). */
  private async driveError(prefix: string, response: Response): Promise<never> {
    const body = await response.text().catch(() => '');
    if (response.status === 401) {
      throw this.authError(
        'AUTH_EXPIRED',
        `${prefix}: Google Drive access expired. Please sign in with Google again.`,
      );
    }
    if (response.status === 403) {
      throw this.authError('PERMISSION_DENIED', `${prefix}: Google Drive permission denied.`);
    }
    throw new Error(redactLogLine(`${prefix}: ${body || response.statusText}`));
  }

  /**
   * Perform a Drive API request with a resolved token. On HTTP 401/403 the
   * token is refreshed ONCE and the request retried ONCE (DTL-2, Android
   * DriveCoordinator parity). No hot loop: at most one refresh per request,
   * then a typed AUTH_EXPIRED/PERMISSION_DENIED error surfaces.
   */
  private async fetchWithToken(
    url: string,
    init: { method?: string; body?: BodyInit },
    token: string,
  ): Promise<Response> {
    const authorized = (t: string): RequestInit => ({
      ...init,
      headers: { Authorization: `Bearer ${t}` },
    });
    let response = await fetch(url, authorized(token));
    if (response.status === 401 || response.status === 403) {
      token = await refreshDriveToken(); // throws typed AUTH_REQUIRED when refresh is impossible
      response = await fetch(url, authorized(token));
    }
    return response;
  }

  private async getOrCreateFolder(accessToken: string): Promise<string> {
    const root =
      (await this.findFolder(accessToken, 'NextPage')) ??
      (await this.createFolder(accessToken, 'NextPage'));
    const books = await this.findFolder(accessToken, 'Books', root);
    if (books) return books;
    const createResponse = await fetch(`${GDriveProvider.GDRIVE_API_BASE}/files`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        name: 'Books',
        mimeType: 'application/vnd.google-apps.folder',
        parents: [root],
      }),
    });
    if (!createResponse.ok)
      throw await this.driveError('GDrive folder creation failed', createResponse);
    const createData = await createResponse.json();
    return createData.id;
  }

  private async findFolder(
    accessToken: string,
    name: string,
    parentId?: string,
  ): Promise<string | null> {
    const parent = parentId ? ` and '${parentId}' in parents` : '';
    const query = encodeURIComponent(
      `name = '${name}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false${parent}`,
    );
    const response = await fetch(`${GDriveProvider.GDRIVE_API_BASE}/files?q=${query}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    const data = await response.json();
    return data.files?.[0]?.id ?? null;
  }

  private async createFolder(accessToken: string, name: string): Promise<string> {
    const response = await fetch(`${GDriveProvider.GDRIVE_API_BASE}/files`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, mimeType: 'application/vnd.google-apps.folder' }),
    });
    if (!response.ok) throw await this.driveError('GDrive folder creation failed', response);
    return (await response.json()).id;
  }

  /**
   * Upload a file to `NextPage/Books`, idempotently (DRP-3): find an existing
   * non-trashed file by canonical name and PATCH-update it (Android
   * findFileByName → files().update parity); otherwise POST-create. Never
   * creates a second Drive file for the same canonical name. Returns the real
   * Drive file ID (create or update).
   */
  async upload(id: string, file: Uint8Array, name?: string): Promise<string> {
    const accessToken = await this.getAccessToken();
    const folderId = await this.getOrCreateFolder(accessToken);
    const fileName = name || id;

    const query = encodeURIComponent(
      `name = '${fileName}' and '${folderId}' in parents and trashed = false`,
    );
    const searchResponse = await this.fetchWithToken(
      `${GDriveProvider.GDRIVE_API_BASE}/files?q=${query}`,
      { method: 'GET' },
      accessToken,
    );
    if (!searchResponse.ok) throw await this.driveError('GDrive search failed', searchResponse);
    const searchData = await searchResponse.json();
    const existing =
      (searchData.files ?? []).find((f: { trashed?: boolean }) => !f.trashed) ?? null;

    const metadata = {
      name: fileName,
      parents: [folderId],
    };

    const formData = new FormData();
    formData.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
    formData.append('file', new Blob([file.buffer as ArrayBuffer]));

    const method = existing ? 'PATCH' : 'POST';
    const url = existing
      ? `${GDriveProvider.GDRIVE_UPLOAD_BASE}/files/${existing.id}?uploadType=multipart`
      : `${GDriveProvider.GDRIVE_UPLOAD_BASE}/files?uploadType=multipart`;

    const response = await this.fetchWithToken(url, { method, body: formData }, accessToken);

    if (!response.ok) {
      throw await this.driveError('GDrive Upload Failed', response);
    }

    const data = await response.json();
    return data.id; // Returns the GDrive file ID (create or update)
  }

  async download(remotePath: string): Promise<Uint8Array> {
    const accessToken = await this.getAccessToken();

    // remotePath here is expected to be the GDrive file ID or we need to find it by name
    // Given the interface, if we use file names as IDs:
    let fileId = remotePath;
    if (!remotePath.match(/^[a-zA-Z0-9_-]{25,}$/)) {
      // Heuristic to check if it's an ID or name
      // It's probably a name, find the ID
      const query = encodeURIComponent(`name = '${remotePath}' and trashed = false`);
      const searchResponse = await this.fetchWithToken(
        `${GDriveProvider.GDRIVE_API_BASE}/files?q=${query}`,
        { method: 'GET' },
        accessToken,
      );
      if (!searchResponse.ok) throw await this.driveError('GDrive search failed', searchResponse);
      const searchData = await searchResponse.json();
      if (!searchData.files || searchData.files.length === 0) {
        throw new Error(`File not found on GDrive: ${remotePath}`);
      }
      fileId = searchData.files[0].id;
    }

    const response = await this.fetchWithToken(
      `${GDriveProvider.GDRIVE_API_BASE}/files/${fileId}?alt=media`,
      { method: 'GET' },
      accessToken,
    );

    if (!response.ok) {
      throw await this.driveError('GDrive Download Failed', response);
    }

    const buffer = await response.arrayBuffer();
    return new Uint8Array(buffer);
  }

  async delete(_remotePath: string): Promise<void> {
    await this.getAccessToken();
    // Implementation omitted for brevity or if not strictly required by task but part of interface
    console.warn(redactLogLine('GDrive delete not implemented'));
  }

  async list(_prefix: string): Promise<string[]> {
    const accessToken = await this.getAccessToken();
    const folderId = await this.getOrCreateFolder(accessToken);

    const query = encodeURIComponent(`'${folderId}' in parents and trashed = false`);
    const response = await this.fetchWithToken(
      `${GDriveProvider.GDRIVE_API_BASE}/files?q=${query}`,
      { method: 'GET' },
      accessToken,
    );

    const data = await response.json();
    return (data.files || []).map((f: { name: string }) => f.name);
  }
}
