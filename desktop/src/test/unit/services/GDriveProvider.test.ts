/**
 * Unit tests for GDriveProvider — Task 2.1
 * Tests NEW behavior: token from GoogleOAuthService, optional `name` on upload.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock SupabaseAuthService BEFORE importing GDriveProvider
vi.mock('$lib/shared/services/SupabaseAuthService', () => ({
  getDriveToken: vi.fn(),
  refreshDriveToken: vi.fn(),
}));

import { GDriveProvider, __resetGDriveFolderCache } from '$lib/shared/services/storage/GDriveProvider';
import { getDriveToken, refreshDriveToken } from '$lib/shared/services/SupabaseAuthService';

function mockDriveApiResponses(
  responses: Array<{
    ok: boolean;
    status?: number;
    json: () => unknown;
    text?: () => Promise<string>;
  }>,
) {
  let callIndex = 0;
  vi.mocked(globalThis.fetch).mockImplementation(async () => {
    const resp = responses[callIndex++] ?? responses[responses.length - 1];
    return {
      ok: resp.ok,
      status: resp.status ?? (resp.ok ? 200 : 401),
      statusText: resp.ok ? 'OK' : 'Unauthorized',
      json: resp.json,
      arrayBuffer: async () => new ArrayBuffer(0),
      text: resp.text ?? (async () => 'error'),
    } as Response;
  });
}

function mockAuth(token: string | null) {
  vi.mocked(getDriveToken).mockResolvedValue(token);
}

function mockAuthError(message: string) {
  vi.mocked(getDriveToken).mockRejectedValue(new Error(message));
}

describe('GDriveProvider — token source swap', () => {
  let provider: GDriveProvider;

  beforeEach(() => {
    vi.clearAllMocks();
    globalThis.fetch = vi.fn();
    __resetGDriveFolderCache();
    provider = new GDriveProvider();
  });

  it('calls getDriveToken for authentication', async () => {
    mockAuth('ya29.test-token');
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-1', name: 'Books' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [] }) },
    ]);

    await provider.list('');

    expect(getDriveToken).toHaveBeenCalled();
  });

  it('throws when getDriveToken rejects (not authenticated)', async () => {
    mockAuthError('No Google Drive token available. Please sign in with Google again.');

    await expect(provider.list('')).rejects.toThrow(
      'No Google Drive token available. Please sign in with Google again.',
    );
    expect(getDriveToken).toHaveBeenCalled();
  });

  it('upload with custom name uses it in Drive API metadata', async () => {
    mockAuth('token-123');
    // Root folder, Books folder, find-by-name (no match), then upload
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'root-abc' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-abc' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [] }) },
      { ok: true, json: () => Promise.resolve({ id: 'file-xyz' }) },
    ]);

    const data = new Uint8Array([1, 2, 3]);
    const result = await provider.upload('book-001', data, 'book-001_state.json');

    expect(result).toBe('file-xyz');
    // Root and Books folder lookups precede the upload.
    const uploadCall = vi.mocked(globalThis.fetch).mock.calls[3];
    expect(uploadCall[0]).toContain('upload/drive/v3/files');
    // Verify the FormData contains the custom name
    const formData = uploadCall[1]?.body as FormData;
    const metadataBlob = formData?.get('metadata') as Blob;
    const metadataText = await metadataBlob?.text();
    const metadata = JSON.parse(metadataText);
    expect(metadata.name).toBe('book-001_state.json');
    expect(metadata.parents).toEqual(['folder-abc']);
  });

  it('upload without name defaults to id as filename', async () => {
    mockAuth('token-456');
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'root-def' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-def' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [] }) },
      { ok: true, json: () => Promise.resolve({ id: 'file-ghi' }) },
    ]);

    const data = new Uint8Array([4, 5, 6]);
    const result = await provider.upload('epub-001', data);

    expect(result).toBe('file-ghi');
    // Verify metadata name defaults to id
    const uploadCall = vi.mocked(globalThis.fetch).mock.calls[3];
    const formData = uploadCall[1]?.body as FormData;
    const metadataBlob = formData?.get('metadata') as Blob;
    const metadataText = await metadataBlob?.text();
    const metadata = JSON.parse(metadataText);
    expect(metadata.name).toBe('epub-001');
  });
});

describe('GDriveProvider — idempotent upload (DRP-3)', () => {
  let provider: GDriveProvider;

  beforeEach(() => {
    vi.clearAllMocks();
    globalThis.fetch = vi.fn();
    __resetGDriveFolderCache();
    provider = new GDriveProvider();
    vi.mocked(refreshDriveToken).mockResolvedValue('ya29.refreshed-token');
  });

  it('RED: finds existing file by canonical name and PATCH-updates it (no duplicate create)', async () => {
    mockAuth('token-1');
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'root-1' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-1' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'file-existing' }] }) },
      { ok: true, json: () => Promise.resolve({ id: 'file-existing' }) },
    ]);

    const fileId = await provider.upload('book-1', new Uint8Array([1]), 'book-1.epub');

    expect(fileId).toBe('file-existing');
    const uploadCall = vi.mocked(globalThis.fetch).mock.calls[3];
    expect(uploadCall[0]).toContain('files/file-existing?uploadType=multipart');
    expect(uploadCall[1]?.method).toBe('PATCH');
  });

  it('RED: creates a new file when no file with the canonical name exists', async () => {
    mockAuth('token-2');
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'root-2' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-2' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [] }) },
      { ok: true, json: () => Promise.resolve({ id: 'file-created' }) },
    ]);

    const fileId = await provider.upload('book-2', new Uint8Array([1]), 'book-2.epub');

    expect(fileId).toBe('file-created');
    const uploadCall = vi.mocked(globalThis.fetch).mock.calls[3];
    expect(uploadCall[0]).toContain('upload/drive/v3/files?uploadType=multipart');
    expect(uploadCall[1]?.method).toBe('POST');
  });

  it('RED: picks the first non-trashed match when duplicate names exist', async () => {
    mockAuth('token-3');
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'root-3' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-3' }] }) },
      {
        ok: true,
        json: () =>
          Promise.resolve({ files: [{ id: 'trashed-1', trashed: true }, { id: 'keep-1' }] }),
      },
      { ok: true, json: () => Promise.resolve({ id: 'keep-1' }) },
    ]);

    const fileId = await provider.upload('book-3', new Uint8Array([1]), 'book-3.epub');

    expect(fileId).toBe('keep-1');
    const uploadCall = vi.mocked(globalThis.fetch).mock.calls[3];
    expect(uploadCall[0]).toContain('files/keep-1?uploadType=multipart');
    expect(uploadCall[1]?.method).toBe('PATCH');
  });

  it('RED: retry after lost response updates the existing file (DRP-3 scenario)', async () => {
    mockAuth('token-4');
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'root-4' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-4' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'file-uploaded' }] }) },
      { ok: true, json: () => Promise.resolve({ id: 'file-uploaded' }) },
    ]);

    // Simulate the retry of a book whose upload response was lost
    const fileId = await provider.upload('book-4', new Uint8Array([1]), 'book-4.epub');

    expect(fileId).toBe('file-uploaded');
    // Only one upload request happened (PATCH), no POST create
    const uploadCalls = vi
      .mocked(globalThis.fetch)
      .mock.calls.filter((c) => String(c[0]).includes('uploadType=multipart'));
    expect(uploadCalls).toHaveLength(1);
    expect(uploadCalls[0][1]?.method).toBe('PATCH');
  });
});

describe('GDriveProvider — token refresh layers (DTL-1/DTL-2/DTL-3)', () => {
  let provider: GDriveProvider;

  beforeEach(() => {
    vi.clearAllMocks();
    globalThis.fetch = vi.fn();
    __resetGDriveFolderCache();
    provider = new GDriveProvider();
    vi.mocked(refreshDriveToken).mockResolvedValue('ya29.refreshed-token');
  });

  it('RED: falls back to refreshDriveToken when getDriveToken returns null (auto-refresh dropped token)', async () => {
    mockAuth(null);
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'root-r1' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-r1' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [] }) },
      { ok: true, json: () => Promise.resolve({ id: 'file-r1' }) },
    ]);

    const fileId = await provider.upload('book-r1', new Uint8Array([1]), 'book-r1.epub');

    expect(refreshDriveToken).toHaveBeenCalledTimes(1);
    expect(fileId).toBe('file-r1');
  });

  it('RED: 401 mid-request → refresh once → retry once → succeeds (no duplicate work)', async () => {
    mockAuth('token-r2');
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'root-r2' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-r2' }] }) },
      { ok: false, status: 401, json: () => Promise.resolve({}) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'file-r2' }] }) },
      { ok: true, json: () => Promise.resolve({ id: 'file-r2' }) },
    ]);

    const fileId = await provider.upload('book-r2', new Uint8Array([1]), 'book-r2.epub');

    expect(refreshDriveToken).toHaveBeenCalledTimes(1);
    expect(fileId).toBe('file-r2');
  });

  it('RED: still 401 after refresh → typed AUTH_EXPIRED, refresh bounded to ONE attempt (no hot loop)', async () => {
    mockAuth('token-r3');
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'root-r3' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-r3' }] }) },
      { ok: false, status: 401, json: () => Promise.resolve({}) },
      { ok: false, status: 401, json: () => Promise.resolve({}) },
    ]);

    await expect(provider.upload('book-r3', new Uint8Array([1]), 'book-r3.epub')).rejects.toThrow(
      /sign in with Google again/i,
    );

    expect(refreshDriveToken).toHaveBeenCalledTimes(1);
  });

  it('RED: 403 after refresh → typed PERMISSION_DENIED, refresh bounded to ONE attempt', async () => {
    mockAuth('token-r4');
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'root-r4' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-r4' }] }) },
      { ok: false, status: 403, json: () => Promise.resolve({}) },
      { ok: false, status: 403, json: () => Promise.resolve({}) },
    ]);

    await expect(provider.upload('book-r4', new Uint8Array([1]), 'book-r4.epub')).rejects.toThrow(
      /permission denied/i,
    );

    expect(refreshDriveToken).toHaveBeenCalledTimes(1);
  });

  it('RED: second upload reuses the resolved folder (no duplicate NextPage/Books)', async () => {
    mockAuth('token-conc');
    // First upload: find NextPage (none) → create → find Books (none) → create → find file (none) → upload
    // Second upload (cache hit): find file (none) → upload — NO folder creations.
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [] }) }, // find NextPage → none
      { ok: true, json: () => Promise.resolve({ id: 'root-new' }) }, // create NextPage
      { ok: true, json: () => Promise.resolve({ files: [] }) }, // find Books → none
      { ok: true, json: () => Promise.resolve({ id: 'books-new' }) }, // create Books
      { ok: true, json: () => Promise.resolve({ files: [] }) }, // find file-a → none
      { ok: true, json: () => Promise.resolve({ id: 'file-a' }) }, // upload A
      { ok: true, json: () => Promise.resolve({ files: [] }) }, // find file-b → none
      { ok: true, json: () => Promise.resolve({ id: 'file-b' }) }, // upload B
    ]);

    await provider.upload('book-a', new Uint8Array([1]), 'book-a.epub');
    const b = await provider.upload('book-b', new Uint8Array([2]), 'book-b.epub');

    expect(b).toBe('file-b');
    // Exactly TWO folder-create calls total (one NextPage + one Books), never four.
    const createCalls = vi
      .mocked(globalThis.fetch)
      .mock.calls.filter((c) => String(c[1]?.body).includes('google-apps.folder'));
    expect(createCalls).toHaveLength(2);
  });

  it('RED: upload failure message redacts tokens (DTL-3 — no raw bearer in errors)', async () => {
    mockAuth('token-r5');
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'root-r5' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-r5' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [] }) },
      {
        ok: false,
        status: 500,
        json: () => Promise.resolve({}),
        text: async () =>
          'authorization=ya29.SECRET-TOKEN and eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.sig',
      },
    ]);

    const err = (await provider
      .upload('book-r5', new Uint8Array([1]), 'book-r5.epub')
      .catch((e: Error) => e)) as Error;
    // The raw token value must never appear in the surfaced error (DTL-3)
    expect(err.message).not.toContain('SECRET-TOKEN');
    // The redacted form replaces it
    expect(err.message).toContain('[REDACTED]');
  });
});
