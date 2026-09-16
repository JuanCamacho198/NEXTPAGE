/**
 * Thin Tauri invoke wrappers for the backend streaming download command
 * (`desktop/src-tauri/src/commands/download.rs`).
 *
 * A remote book is never fetched from the webview (the CSP forbids it): the Rust
 * command streams the bytes to `{app_data}/tmp/downloads/{transferId}.{ext}`,
 * reports throttled progress on the `discover-download-progress` event, supports
 * per-transfer cancellation, and atomically renames the `.part` file on success.
 * The caller imports the returned local file through the existing import path and
 * then discards it.
 */
import { invoke } from '$lib/shared/api/invokeWrapper';

/** Mirrors Rust `DownloadRemoteBookInput` (camelCase IPC). */
export interface DownloadRemoteBookInput {
  transferId: string;
  url: string;
  format?: string;
}

/** Mirrors Rust `DownloadRemoteBookResult` (camelCase IPC). */
export interface DownloadRemoteBookResult {
  filePath: string;
  bytes: number;
  sha256: string;
}

/** Mirrors Rust `DownloadProgressPayload` (`discover-download-progress`). */
export interface DownloadProgressPayload {
  transferId: string;
  downloaded: number;
  total: number | null;
  phase: 'progress' | 'completed' | 'failed' | 'cancelled';
}

export async function downloadRemoteBook(
  input: DownloadRemoteBookInput,
): Promise<DownloadRemoteBookResult> {
  return invoke<DownloadRemoteBookResult>('downloadRemoteBook', { payload: input });
}

/** Returns `false` when the transfer is unknown or already finished. */
export async function cancelRemoteDownload(transferId: string): Promise<boolean> {
  return invoke<boolean>('cancelRemoteDownload', { transferId });
}

/** Returns `false` when the file was already gone; refuses paths outside the downloads dir. */
export async function discardRemoteDownload(filePath: string): Promise<boolean> {
  return invoke<boolean>('discardRemoteDownload', { filePath });
}
