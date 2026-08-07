/**
 * SyncOutboxDao — SQLite-backed outbox for offline-first Supabase sync.
 *
 * Mutations are INSERTed here first, then a timer-based processor
 * (SyncOutboxService) flushes them to Supabase. On 2xx, the row is
 * deleted. On failure, retry_count is incremented with exponential
 * backoff (2^retry_count seconds, capped at 60s, max 50 retries).
 *
 * Backed by specific Tauri commands (no generic SQL bridge).
 */
import { invoke } from '$lib/shared/api/invokeWrapper';

export interface SyncOutboxRow {
  id: string;
  entityType: string;
  entityId: string | null;
  operation: 'UPSERT' | 'DELETE';
  payloadJson: string;
  retryCount: number;
  lastError: string | null;
  createdAt: string;
  nextRetryAt: string;
}

export class SyncOutboxDao {
  /**
   * Insert a new outbox row. Returns the generated UUID.
   */
  async add(
    entityType: string,
    entityId: string | null,
    operation: 'UPSERT' | 'DELETE',
    payloadJson: string,
  ): Promise<string> {
    return invoke<string>('addSyncOutboxItem', {
      entityType,
      entityId,
      operation,
      payloadJson,
    });
  }

  /**
   * Coalesced enqueue (D5, SR-4.1): transactionally UPDATE-else-INSERT the row
   * keyed by (entityType, entityId) + payload `userId`, latest client
   * `updatedAt` wins (D6). One IPC per location change — the flood of progress
   * events collapses to a single row per (user_id, book_id) in SQLite, so the
   * flush never re-sends a stale event. Never drops rows. Returns the id of the
   * row that now holds the event (existing row updated in place, or the new id).
   */
  async addCoalesced(
    entityType: string,
    entityId: string,
    operation: 'UPSERT',
    payloadJson: string,
  ): Promise<string> {
    return invoke<string>('addCoalescedSyncOutboxItem', {
      entityType,
      entityId,
      operation,
      payloadJson,
    });
  }

  /**
   * Return all rows that are ready for retry (next_retry_at <= now),
   * ordered by created_at ASC (FIFO). Excludes items with retry_count >= 50.
   */
  async listReady(): Promise<SyncOutboxRow[]> {
    const rows = await invoke<SyncOutboxRow[]>('listSyncOutboxReady');
    return rows.map((r) => ({
      ...r,
      operation: r.operation as 'UPSERT' | 'DELETE',
    }));
  }

  /**
   * Mark an item as failed: increments retry_count, sets last_error,
   * and calculates next_retry_at with exponential backoff.
   */
  async markFailed(id: string, error: string): Promise<void> {
    await invoke('markSyncOutboxFailed', { id, error });
  }

  /**
   * Delete a row after successful sync.
   */
  async delete(id: string): Promise<void> {
    await invoke('deleteSyncOutboxItem', { id });
  }

  /**
   * Prune rows older than 7 days with retry_count >= 10.
   * Returns the number of rows deleted.
   */
  async prune(): Promise<number> {
    return invoke<number>('pruneSyncOutbox');
  }
}

/**
 * Auth-class failure detection (D4, R2): the outbox circuit breaker pauses on
 * these and only these failures. A PostgrestError with HTTP status 400 (RLS
 * denial) or 401 (invalid/expired JWT), or a typed SyncError with code
 * AUTH_REQUIRED / AUTH_EXPIRED. Anything else is a normal failure that keeps
 * the existing per-row exponential backoff — never the breaker.
 */
export function isAuthClassError(error: unknown): boolean {
  const status = (error as { status?: unknown } | null)?.status;
  if (typeof status === 'number' && (status === 400 || status === 401)) return true;
  const code = (error as { code?: unknown } | null)?.code;
  return code === 'AUTH_REQUIRED' || code === 'AUTH_EXPIRED';
}
