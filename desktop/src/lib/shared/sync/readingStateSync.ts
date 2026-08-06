import type { ReadingStatus } from '$lib/shared/types';

export type ReadingStateEnvelope = {
  bookId: string;
  state: ReadingStatus;
  percentage: number;
  stateVersion: number;
  eventUpdatedAt: string;
  deviceId: string;
};

export function compareReadingState(a: ReadingStateEnvelope, b: ReadingStateEnvelope): number {
  if (a.stateVersion !== b.stateVersion) return a.stateVersion - b.stateVersion;
  const time = Date.parse(a.eventUpdatedAt) - Date.parse(b.eventUpdatedAt);
  if (time !== 0) return time;
  return a.deviceId.localeCompare(b.deviceId);
}

export function isValidReadingStateEnvelope(value: unknown): value is ReadingStateEnvelope {
  if (!value || typeof value !== 'object') return false;
  const envelope = value as Partial<ReadingStateEnvelope>;
  const stateVersion = envelope.stateVersion;
  return typeof envelope.bookId === 'string' &&
    ['to_read', 'reading', 'completed'].includes(envelope.state ?? '') &&
    typeof envelope.percentage === 'number' && envelope.percentage >= 0 && envelope.percentage <= 100 &&
    typeof stateVersion === 'number' && Number.isInteger(stateVersion) && stateVersion >= 0 &&
    typeof envelope.eventUpdatedAt === 'string' && Number.isFinite(Date.parse(envelope.eventUpdatedAt)) &&
    typeof envelope.deviceId === 'string' && envelope.deviceId.length > 0;
}

export function shouldApplyReadingState(local: ReadingStateEnvelope | null, remote: unknown): remote is ReadingStateEnvelope {
  return isValidReadingStateEnvelope(remote) && (local === null || compareReadingState(remote, local) > 0);
}
