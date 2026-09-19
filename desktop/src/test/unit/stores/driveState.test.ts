/**
 * Unit tests for the `driveState` runes store (login-drive-separation, PR1).
 *
 * The store is a pure state holder fed by `DriveConnectService` (PR2): it
 * never schedules work or touches the disk. These tests pin the default
 * state and the setter/reset transitions the service will rely on.
 */

import { describe, expect, it, beforeEach } from 'vitest';
import { driveState } from '$lib/shared/stores/driveState.svelte';

beforeEach(() => {
  driveState.resetDriveState();
});

describe('driveState', () => {
  it('starts unauthorized, idle, and error-free', () => {
    expect(driveState.isAuthorized).toBe(false);
    expect(driveState.isConnecting).toBe(false);
    expect(driveState.lastError).toBeNull();
  });

  it('tracks a connect attempt lifecycle', () => {
    driveState.setDriveConnecting(true);
    expect(driveState.isConnecting).toBe(true);

    driveState.setDriveConnecting(false);
    driveState.setDriveAuthorized(true);
    expect(driveState.isConnecting).toBe(false);
    expect(driveState.isAuthorized).toBe(true);
  });

  it('authorizing clears the last error', () => {
    driveState.setDriveError('AUTH_REQUIRED');
    driveState.setDriveAuthorized(true);
    expect(driveState.lastError).toBeNull();
  });

  it('holds the last failure for the Settings card to render', () => {
    driveState.setDriveError('DRIVE_CONFIG_MISSING');
    expect(driveState.lastError).toBe('DRIVE_CONFIG_MISSING');
    expect(driveState.isAuthorized).toBe(false);
  });

  it('reset returns to the initial state', () => {
    driveState.setDriveConnecting(true);
    driveState.setDriveAuthorized(true);
    driveState.setDriveError('AUTH_REQUIRED');

    driveState.resetDriveState();

    expect(driveState.isAuthorized).toBe(false);
    expect(driveState.isConnecting).toBe(false);
    expect(driveState.lastError).toBeNull();
  });
});
