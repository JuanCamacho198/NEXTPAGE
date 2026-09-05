/**
 * Unit tests for `effectiveSeverity(event)` exported by
 * `desktop/src/lib/shared/logger/SentrySink.ts`.
 *
 * Locks the `reader-error-enrichment` spec scenarios:
 *  - "Reader event with rich context reaches Sentry" — medium + non-empty
 *    context ⇒ 'high'.
 *  - "Reader error with empty context" — preserves `event.severity`.
 *  - "Medium-severity reader event" — bumps to 'high' when context present.
 *  - Non-reader events are NEVER affected by the floor.
 */
import { describe, expect, it } from 'vitest';
import { effectiveSeverity } from '$lib/shared/logger/SentrySink';
import type { ErrorEvent } from '$lib/shared/events/ErrorEvent';

function makeEvent(overrides: Partial<ErrorEvent> = {}): ErrorEvent {
  return {
    timestamp: '2026-09-04T00:00:00.000Z',
    severity: 'medium',
    category: 'runtime',
    code: 'TEST_CODE',
    message: 'synthetic',
    correlationId: 'corr-1',
    source: 'reader',
    recoverable: true,
    context: {},
    ...overrides,
  };
}

describe('effectiveSeverity — reader-source severity floor', () => {
  it('returns "high" for a reader event with non-empty context', () => {
    const ev = makeEvent({ severity: 'medium', context: { bookId: 'abc' } });
    expect(effectiveSeverity(ev)).toBe('high');
  });

  it('returns event.severity unchanged for a reader event with empty context', () => {
    const ev = makeEvent({ severity: 'low', context: {} });
    expect(effectiveSeverity(ev)).toBe('low');
  });

  it('does NOT affect non-reader events even with non-empty context', () => {
    const ev = makeEvent({
      severity: 'medium',
      source: 'app_shell',
      context: { bookId: 'abc' },
    });
    expect(effectiveSeverity(ev)).toBe('medium');
  });

  it('bumps a medium-severity reader event to "high" when context is non-empty', () => {
    const ev = makeEvent({
      severity: 'medium',
      source: 'reader',
      context: { bookId: 'abc', highlightId: 'hl1', format: 'epub' },
    });
    expect(effectiveSeverity(ev)).toBe('high');
  });
});
