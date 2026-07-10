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
