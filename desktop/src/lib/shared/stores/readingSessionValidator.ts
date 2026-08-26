/**
 * readingSessionValidator — pure validation for reading session progress events.
 * Extracted from ReaderDomainState (P2-5) to enable isolated testing and
 * to keep ReaderDomainState focused on lifecycle only.
 */

export const MIN_SESSION_DURATION_SECONDS = 30;

export type SessionProgressEvent = {
  startedAt: string;
  endedAt?: string;
  durationSeconds: number;
  startPercentage?: number;
  endPercentage?: number;
};

/**
 * Returns true iff the event is well-formed:
 * - endedAt present and duration > 0
 * - startedAt/endedAt are valid ISO dates with endedAt > startedAt
 * - optional percentages, when present, are in [0, 100]
 */
export function isValidSessionProgressEvent(event: SessionProgressEvent): boolean {
  if (!event.endedAt || event.durationSeconds <= 0) return false;

  const startedAt = Date.parse(event.startedAt);
  const endedAt = Date.parse(event.endedAt);
  if (!Number.isFinite(startedAt) || !Number.isFinite(endedAt) || endedAt <= startedAt) {
    return false;
  }

  const percentages = [event.startPercentage, event.endPercentage].filter(
    (value): value is number => typeof value === 'number',
  );

  return percentages.every((value) => value >= 0 && value <= 100);
}

/**
 * Full gate used before persisting: duration must be >=30s AND event valid.
 * Mirrors the double-guard in ReaderDomainState.handlePdfSessionProgress.
 */
export function shouldPersistSession(event: SessionProgressEvent): boolean {
  if (event.durationSeconds < MIN_SESSION_DURATION_SECONDS) return false;
  return isValidSessionProgressEvent(event);
}
