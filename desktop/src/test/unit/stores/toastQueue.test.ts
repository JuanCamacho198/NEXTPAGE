/**
 * Unit tests for the toastQueue store — T-08 (Batch 3 / PR 3).
 *
 * Covers `pushToast` (append with unique ids) and `dismiss` (remove by id,
 * no-op for unknown ids).
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { toastQueue, pushToast, dismiss } from '$lib/stores/toastQueue.svelte';

describe('toastQueue', () => {
  beforeEach(() => {
    // Reset the module-level queue between tests. Iterate a COPY: the queue is
    // mutated by dismiss during the loop, which would make for...of skip items.
    for (const toast of [...toastQueue.items]) {
      dismiss(toast.id);
    }
  });

  it('pushToast appends a toast with type and message', () => {
    pushToast('success', 'Cache cleared');

    expect(toastQueue.items).toEqual([
      { id: expect.any(Number), type: 'success', message: 'Cache cleared' },
    ]);
  });

  it('assigns a unique id to each toast', () => {
    pushToast('info', 'one');
    pushToast('error', 'two');

    const ids = toastQueue.items.map((t) => t.id);
    expect(new Set(ids).size).toBe(2);
  });

  it('dismiss removes the toast by id', () => {
    pushToast('success', 'one');
    pushToast('info', 'two');
    const [first] = toastQueue.items;

    dismiss(first.id);

    expect(toastQueue.items).toEqual([{ id: expect.any(Number), type: 'info', message: 'two' }]);
  });

  it('dismiss is a no-op for an unknown id', () => {
    pushToast('success', 'one');

    dismiss(9999);

    expect(toastQueue.items).toHaveLength(1);
  });
});
