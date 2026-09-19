/**
 * Unit tests for `driveConnectPromptGate` (login-drive-separation, PR1).
 *
 * Ports the Android `DriveConnectPromptGateTest` decision table to the
 * desktop gate. Desktop difference: there is no `importSucceeded` input —
 * the gate is evaluated only when a Drive-gated action is invoked while
 * unauthorized, so the trigger is implied by the call itself.
 */

import { describe, expect, it } from 'vitest';
import {
  declineKeyForUser,
  markDriveDeclined,
  shouldShowDrivePrompt,
  DRIVE_PROMPT_DECLINE_KEY_PREFIX,
} from '$lib/shared/services/driveConnectPromptGate';

const userId = 'user-google-1';

function show(overrides: Partial<Parameters<typeof shouldShowDrivePrompt>[0]> = {}): boolean {
  return shouldShowDrivePrompt({
    driveEnabled: false,
    providerIsGoogle: true,
    declinedForUser: null,
    currentUser: userId,
    ...overrides,
  });
}

describe('shouldShowDrivePrompt', () => {
  it('shows on the first gated action, then never again after decline is persisted', () => {
    expect(show()).toBe(true);

    const declinedForUser = markDriveDeclined(userId);

    expect(show({ declinedForUser })).toBe(false);
  });

  it('never shows when Drive is already authorized', () => {
    expect(show({ driveEnabled: true })).toBe(false);
  });

  it('never shows for non-Google providers', () => {
    expect(show({ providerIsGoogle: false })).toBe(false);
  });

  it('never shows without a signed-in user', () => {
    expect(show({ currentUser: null })).toBe(false);
    expect(show({ currentUser: '' })).toBe(false);
    expect(show({ currentUser: '   ' })).toBe(false);
  });

  it('decline is per account — a different account is still offered', () => {
    const declinedForUser = markDriveDeclined('user-A');
    expect(show({ declinedForUser, currentUser: 'user-B' })).toBe(true);
  });

  it('may re-offer after the decline marker is cleared (e.g. post-disconnect)', () => {
    const declinedForUser = markDriveDeclined(userId);
    expect(show({ declinedForUser })).toBe(false);

    expect(show({ driveEnabled: true, declinedForUser })).toBe(false);
    expect(show({ declinedForUser: null })).toBe(true);
  });
});

describe('markDriveDeclined', () => {
  it('returns the current user as the marker', () => {
    expect(markDriveDeclined(userId)).toBe(userId);
  });

  it('returns null for blank or null users (nothing to persist)', () => {
    expect(markDriveDeclined(null)).toBeNull();
    expect(markDriveDeclined('')).toBeNull();
    expect(markDriveDeclined('   ')).toBeNull();
  });
});

describe('decline marker key', () => {
  it('namespaces the key per user', () => {
    expect(declineKeyForUser(userId)).toBe(`${DRIVE_PROMPT_DECLINE_KEY_PREFIX}${userId}`);
    expect(declineKeyForUser('user-B')).not.toBe(declineKeyForUser('user-A'));
  });
});
