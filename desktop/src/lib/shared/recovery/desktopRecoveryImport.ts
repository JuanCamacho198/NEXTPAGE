/** PR4 Desktop recovery import: lifecycle-safe catalog classification and verified selected import. */
import { canonicalBookName, type SyncError } from '$lib/shared/protocol/DriveCatalogContract';
import type { SupabaseUserBookRow } from '$lib/shared/sync/SupabaseBookCatalogSync';
export type ImportOutcome = 'imported' | 'already_imported' | 'unavailable' | 'failed';
export interface ImportResult { bookId: string; outcome: ImportOutcome; error?: SyncError; }
export interface ImportDeps {
  download: (ref: string) => Promise<Uint8Array>;
  persist: (bookId: string, bytes: Uint8Array, meta: { title: string; author: string; format: string }) => Promise<void>;
  markImported: (bookId: string, version: number) => Promise<void>;
  findByHash?: (hash: string) => Promise<SupabaseUserBookRow | null>;
}
export function sha256Hex(bytes: Uint8Array): Promise<string> {
  const source = new Uint8Array(bytes);
  return crypto.subtle.digest('SHA-256', source.buffer as ArrayBuffer).then((d) =>
    [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, '0')).join(''));
}
/** Recoverable candidates; local absence never emits deletion. */
export function classifyRecoveryCatalog(remote: SupabaseUserBookRow[], localIds: ReadonlySet<string>): { downloadable: SupabaseUserBookRow[]; deleted: SupabaseUserBookRow[] } {
  const deleted = remote.filter((r) => r.lifecycle === 'deleted');
  const downloadable = remote.filter((r) => !localIds.has(r.id) && r.lifecycle !== 'deleted' && r.lifecycle !== 'unavailable');
  return { downloadable, deleted };
}
/** Stable remote file id first, canonical name fallback (legacy rows). */
export function resolveRemoteRef(row: SupabaseUserBookRow): string {
  return row.remoteFileId ?? canonicalBookName(row.id, row.format);
}
export function toSyncError(err: unknown, bookId?: string): SyncError {
  const message = err instanceof Error ? err.message : String(err);
  const lower = message.toLowerCase();
  const code = /401|unauthorized|token|sign in|authentication required/i.test(lower) ? 'AUTH_REQUIRED'
    : /403|permission/i.test(lower) ? 'PERMISSION_DENIED'
    : /not found/i.test(lower) ? 'REMOTE_NOT_FOUND'
    : /conflict/i.test(lower) ? 'CONFLICT'
    : 'UNAVAILABLE';
  return { code, message, retryable: code !== 'PERMISSION_DENIED', correlationId: crypto.randomUUID(), bookId };
}
export async function importRecoveredBook(row: SupabaseUserBookRow, deps: ImportDeps): Promise<ImportResult> {
  if (row.lifecycle === 'deleted' || row.lifecycle === 'unavailable') {
    return { bookId: row.id, outcome: 'unavailable', error: { code: 'UNAVAILABLE', message: `Row is ${row.lifecycle}`, retryable: false, correlationId: row.id, bookId: row.id } };
  }
  if (deps.findByHash && row.contentHash) {
    const existing = await deps.findByHash(row.contentHash.replace(/^sha256:/, ''));
    if (existing && existing.id !== row.id) return { bookId: row.id, outcome: 'already_imported' };
  }
  let bytes: Uint8Array;
  try {
    bytes = await deps.download(resolveRemoteRef(row));
  } catch (err) {
    return { bookId: row.id, outcome: 'failed', error: toSyncError(err, row.id) };
  }
  const expected = row.contentHash?.replace(/^sha256:/, '');
  if (expected && (await sha256Hex(bytes)) !== expected) {
    return { bookId: row.id, outcome: 'failed', error: { code: 'HASH_MISMATCH', message: 'SHA-256 verification failed', retryable: true, correlationId: row.id, bookId: row.id } };
  }
  try {
    await deps.persist(row.id, bytes, { title: row.title, author: row.author ?? '', format: row.format });
    await deps.markImported(row.id, (row.catalogVersion ?? 1) + 1);
    return { bookId: row.id, outcome: 'imported' };
  } catch (err) {
    return { bookId: row.id, outcome: 'failed', error: toSyncError(err, row.id) };
  }
}
/** Multi-select import; each row independent and idempotent (upsert by id). */
export async function importSelectedBooks(rows: SupabaseUserBookRow[], deps: ImportDeps): Promise<ImportResult[]> {
  const results: ImportResult[] = [];
  for (const row of rows) results.push(await importRecoveredBook(row, deps));
  return results;
}
