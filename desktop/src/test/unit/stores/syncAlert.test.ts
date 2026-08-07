/**
 * Unit tests for the syncAlert store + reportAuthError (WU2 — desktop-session-persistence).
 *
 * SR-3: typed AUTH_REQUIRED / AUTH_EXPIRED from sync/Drive paths surface to the
 * banner; generic errors and PostgrestError-style failures (breaker class, WU4)
 * never trigger it. D7 auto-clear on re-auth is exercised in the
 * SyncAuthBanner component test. SR-2: the store never schedules anything —
 * repeated reports are idempotent.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { syncAlertStore, reportAuthError } from '$lib/shared/stores/syncAlert.svelte';

function authError(code: string, message: string): Error {
  return Object.assign(new Error(message), { code });
}

beforeEach(() => {
  syncAlertStore.clear();
});

describe('reportAuthError — typed auth surfacing (SR-3)', () => {
  it('starts with no alert', () => {
    expect(syncAlertStore.current).toBeNull();
  });

  it('surfaces AUTH_REQUIRED with type auth_required and the (redacted) message', () => {
    const err = authError(
      'AUTH_REQUIRED',
      'Google Drive access expired. Please sign in with Google again.',
    );

    expect(reportAuthError(err)).toBe(true);
    expect(syncAlertStore.current).toEqual({
      type: 'auth_required',
      message: 'Google Drive access expired. Please sign in with Google again.',
    });
  });

  it('surfaces AUTH_EXPIRED with type auth_expired', () => {
    const err = authError(
      'AUTH_EXPIRED',
      'Google Drive access expired. Please sign in with Google again.',
    );

    expect(reportAuthError(err)).toBe(true);
    expect(syncAlertStore.current?.type).toBe('auth_expired');
  });

  it('uses a stable fallback message when the error carries none', () => {
    expect(reportAuthError({ code: 'AUTH_REQUIRED' })).toBe(true);
    expect(syncAlertStore.current?.message).toBe(
      'Google Drive access expired. Please sign in with Google again.',
    );
  });

  it('ignores generic errors (no banner)', () => {
    expect(reportAuthError(new Error('network drop'))).toBe(false);
    expect(syncAlertStore.current).toBeNull();
  });

  it('ignores PostgrestError-style failures (RLS-400 breaker class, WU4 — never the banner)', () => {
    const postgrest = Object.assign(new Error('RLS denied'), { status: 400, code: '42501' });

    expect(reportAuthError(postgrest)).toBe(false);
    expect(syncAlertStore.current).toBeNull();
  });

  it('repeated AUTH_REQUIRED reports are idempotent — single alert, no loop (SR-2)', () => {
    reportAuthError(authError('AUTH_REQUIRED', 'Drive access expired'));
    reportAuthError(authError('AUTH_REQUIRED', 'Drive access expired'));
    reportAuthError(authError('AUTH_REQUIRED', 'Drive access expired'));

    expect(syncAlertStore.current?.type).toBe('auth_required');
    expect(syncAlertStore.current?.message).toBe('Drive access expired');
  });

  it('clear() dismisses the banner', () => {
    reportAuthError(authError('AUTH_REQUIRED', 'x'));
    expect(syncAlertStore.current).not.toBeNull();

    syncAlertStore.clear();

    expect(syncAlertStore.current).toBeNull();
  });
});
