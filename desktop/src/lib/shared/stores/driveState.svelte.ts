/**
 * Reactive Drive connection state using Svelte 5 runes ($state).
 *
 * Fed by `DriveConnectService` (owns the OAuth flow and the durable grant):
 * it reports authorization, in-flight connect attempts, and the last typed
 * failure for the Settings card and gate callers to subscribe to. This store
 * never touches the disk itself — persistence lives in `drivePersistence.ts`.
 */

let isAuthorized = $state(false);
let isConnecting = $state(false);
let lastError = $state<string | null>(null);

export function setDriveAuthorized(value: boolean): void {
  isAuthorized = value;
  if (value) {
    lastError = null;
  }
}

export function setDriveConnecting(value: boolean): void {
  isConnecting = value;
}

export function setDriveError(message: string | null): void {
  lastError = message;
}

export function resetDriveState(): void {
  isAuthorized = false;
  isConnecting = false;
  lastError = null;
}

export const driveState = {
  get isAuthorized(): boolean {
    return isAuthorized;
  },
  get isConnecting(): boolean {
    return isConnecting;
  },
  get lastError(): string | null {
    return lastError;
  },
  setDriveAuthorized,
  setDriveConnecting,
  setDriveError,
  resetDriveState,
};
