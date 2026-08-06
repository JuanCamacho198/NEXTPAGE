import { describe, expect, it } from 'vitest';
import { compareReadingState, isValidReadingStateEnvelope, shouldApplyReadingState, type ReadingStateEnvelope } from '$lib/shared/sync/readingStateSync';

const state = (overrides: Partial<ReadingStateEnvelope> = {}): ReadingStateEnvelope => ({
  bookId: 'book-1', state: 'reading', percentage: 20, stateVersion: 2,
  eventUpdatedAt: '2026-08-06T12:00:00.000Z', deviceId: 'desktop', ...overrides,
});

describe('reading state sync contract', () => {
  it('orders version, timestamp, then device deterministically', () => {
    expect(compareReadingState(state({ stateVersion: 3 }), state())).toBeGreaterThan(0);
    expect(compareReadingState(state({ eventUpdatedAt: '2026-08-06T12:01:00.000Z' }), state())).toBeGreaterThan(0);
    expect(compareReadingState(state({ deviceId: 'z' }), state({ deviceId: 'a' }))).toBeGreaterThan(0);
  });
  it('rejects malformed, older, and equal remote state', () => {
    expect(isValidReadingStateEnvelope({ ...state(), percentage: 101 })).toBe(false);
    expect(shouldApplyReadingState(state(), { ...state(), stateVersion: 1 })).toBe(false);
    expect(shouldApplyReadingState(state(), state())).toBe(false);
  });
  it('accepts newer state for offline reconciliation', () => {
    expect(shouldApplyReadingState(state(), state({ state: 'completed', stateVersion: 3 }))).toBe(true);
  });
});
