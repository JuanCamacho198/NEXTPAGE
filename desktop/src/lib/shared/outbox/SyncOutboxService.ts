/**
 * SyncOutboxService — timer-based processor for the SQLite sync outbox.
 *
 * Lifecycle:
 *   start() → flush immediately, then every 30s
 *   stop()  → clear interval
 *
 * On each flush:
 *   listReady() → for each row (FIFO):
 *     call handler(entityType, entityId, payloadJson)
 *     on success → delete row
 *     on failure → markFailed (exponential backoff)
 *
 * The handler is injected so SupabaseProgressSync or any future sync
 * target can register without coupling this service to Supabase.
 */
import { SyncOutboxDao } from './SyncOutboxDao';

export type OutboxHandler = (
  entityType: string,
  entityId: string | null,
  operation: 'UPSERT' | 'DELETE',
  payloadJson: string,
) => Promise<void>;

export class SyncOutboxService {
  private dao = new SyncOutboxDao();
  private intervalId: ReturnType<typeof setInterval> | null = null;
  private handler: OutboxHandler | null = null;
  private flushing = false;

  /**
   * Register the handler that processes each outbox row.
   * Call before start().
   */
  setHandler(handler: OutboxHandler): void {
    this.handler = handler;
  }

  /**
   * Start the timer-based processor.
   * Flushes immediately, then every 30 seconds.
   */
  start(): void {
    if (this.intervalId !== null) return;
    void this.flush();
    this.intervalId = setInterval(() => void this.flush(), 30_000);
  }

  /**
   * Stop the processor.
   */
  stop(): void {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  /**
   * Flush all pending outbox items.
   * Public so it can be called manually (e.g., on online event).
   */
  async flush(): Promise<void> {
    if (this.flushing || !this.handler) return;
    this.flushing = true;

    try {
      const items = await this.dao.listReady();

      for (const item of items) {
        try {
          await this.handler(
            item.entityType,
            item.entityId,
            item.operation,
            item.payloadJson,
          );
          await this.dao.delete(item.id);
        } catch (err) {
          const msg = err instanceof Error ? err.message : String(err);
          await this.dao.markFailed(item.id, msg);
        }
      }

      // Prune stale failed items once per flush cycle
      try {
        const pruned = await this.dao.prune();
        if (pruned > 0) {
          console.debug(`SyncOutbox: pruned ${pruned} stale failed items`);
        }
      } catch {
        // Prune is best-effort
      }
    } finally {
      this.flushing = false;
    }
  }
}
