import { describe, expect, it, vi } from 'vitest';
import { SyncOutboxService, type OutboxHandler } from '$lib/shared/outbox/SyncOutboxService';
import type { SyncOutboxDao, SyncOutboxRow } from '$lib/shared/outbox/SyncOutboxDao';

const row = (entityType: string): SyncOutboxRow => ({
  id: 'row-1',
  entityType,
  entityId: 'book-1',
  operation: 'UPSERT',
  payloadJson: '{}',
  retryCount: 0,
  lastError: null,
  createdAt: '2026-01-01T00:00:00.000Z',
  nextRetryAt: '2026-01-01T00:00:00.000Z',
});

function makeDao(items: SyncOutboxRow[]): SyncOutboxDao {
  return {
    listReady: vi.fn().mockResolvedValue(items),
    delete: vi.fn().mockResolvedValue(undefined),
    markFailed: vi.fn().mockResolvedValue(undefined),
    prune: vi.fn().mockResolvedValue(0),
    add: vi.fn(),
  } as unknown as SyncOutboxDao;
}

describe('SyncOutboxService', () => {
  it('keeps unsupported entities retryable with evidence instead of deleting them', async () => {
    const dao = makeDao([row('UNSUPPORTED')]);
    const handler: OutboxHandler = vi.fn().mockRejectedValue(new Error('Unsupported outbox entity: UNSUPPORTED'));
    const service = new SyncOutboxService(dao);
    service.setHandler(handler);

    await service.flush();

    expect(handler).toHaveBeenCalledWith('UNSUPPORTED', 'book-1', 'UPSERT', '{}');
    expect(dao.delete).not.toHaveBeenCalled();
    expect(dao.markFailed).toHaveBeenCalledWith('row-1', 'Unsupported outbox entity: UNSUPPORTED');
  });

  it('deletes an item only after its handler succeeds', async () => {
    const dao = makeDao([row('BOOK')]);
    const handler: OutboxHandler = vi.fn().mockResolvedValue(undefined);
    const service = new SyncOutboxService(dao);
    service.setHandler(handler);

    await service.flush();

    expect(dao.delete).toHaveBeenCalledWith('row-1');
    expect(dao.markFailed).not.toHaveBeenCalled();
  });
});
