/**
 * Unit tests for SupabaseAuthService.
 *
 * Verifies Supabase Auth integration: sign-in via OAuth loopback,
 * session handling, Drive token extraction, sign-out, anonymous sign-in,
 * and callback registration.
 */
import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';

// ---- Mock control variables ----
const mockPluginStart = vi.fn();
const mockPluginCancel = vi.fn();
const mockOpenUrl = vi.fn();

let capturedOnUrlHandler: ((url: string) => void) | null = null;
const mockSetSupabaseSession = vi.fn();
const mockClearSupabaseSession = vi.fn();
const mockSavePersistedAuth = vi.fn();

const mockSignInWithOAuth = vi.fn();
const mockExchangeCodeForSession = vi.fn();
const mockGetSession = vi.fn();
const mockSignOut = vi.fn();
const mockSignInAnonymously = vi.fn();
const mockRefreshSession = vi.fn();
const mockDriveRefreshToken = vi.fn<() => string | null>();
const mockGetLiveSession = vi.fn<() => unknown>(() => null);

// ---- Mock layers ----

vi.mock('@fabianlars/tauri-plugin-oauth', () => ({
  start: (...args: unknown[]) => mockPluginStart(...args),
  cancel: (...args: unknown[]) => mockPluginCancel(...args),
  onUrl: (handler: (url: string) => void) => {
    capturedOnUrlHandler = handler;
    return Promise.resolve(() => {
      if (capturedOnUrlHandler === handler) {
        capturedOnUrlHandler = null;
      }
    });
  },
}));

vi.mock('@tauri-apps/plugin-opener', () => ({
  openUrl: (...args: unknown[]) => mockOpenUrl(...args),
}));

vi.mock('$lib/stores/authState.svelte', () => ({
  authState: {
    setSupabaseSession: (...args: unknown[]) => mockSetSupabaseSession(...args),
    clearSupabaseSession: (...args: unknown[]) => mockClearSupabaseSession(...args),
    get driveRefreshToken(): string | null {
      return mockDriveRefreshToken();
    },
  },
}));

vi.mock('$lib/stores/authPersistence', () => ({
  savePersistedAuth: (...args: unknown[]) => mockSavePersistedAuth(...args),
}));

vi.mock('$lib/services/supabase', () => ({
  getSessionClient: () => ({
    auth: {
      signInWithOAuth: mockSignInWithOAuth,
      exchangeCodeForSession: mockExchangeCodeForSession,
      getSession: mockGetSession,
      signOut: mockSignOut,
      signInAnonymously: mockSignInAnonymously,
      refreshSession: mockRefreshSession,
    },
  }),
  getLiveSession: () => mockGetLiveSession(),
}));

vi.mock('$lib/shared/logger/Logger', () => ({
  logger: {
    error: vi.fn(),
    warn: vi.fn(),
    info: vi.fn(),
    debug: vi.fn(),
  },
}));

vi.mock('$lib/shared/events/ErrorEvent', () => ({
  createErrorEvent: (params: Record<string, unknown>) => ({
    timestamp: new Date().toISOString(),
    correlationId: 'mock-corr-id',
    ...params,
  }),
}));

// ---- Dynamic import (after mocks register) ----
type Sut = typeof import('$lib/shared/services/SupabaseAuthService');
let sut: Sut;

beforeAll(async () => {
  vi.stubEnv('VITE_SUPABASE_URL', 'https://test-project.supabase.co');
  vi.stubEnv('VITE_SUPABASE_ANON_KEY', 'test-anon-key');

  const mod = await import('$lib/shared/services/SupabaseAuthService');
  sut = mod;
});

beforeEach(async () => {
  // 1. Set default mock implementations so signOut() doesn't crash
  mockPluginCancel.mockResolvedValue(undefined);
  mockOpenUrl.mockResolvedValue(undefined);
  mockSetSupabaseSession.mockReturnValue(undefined);
  mockClearSupabaseSession.mockReturnValue(undefined);
  mockSavePersistedAuth.mockResolvedValue(undefined);
  mockSignInWithOAuth.mockResolvedValue({
    data: { url: 'https://example.supabase.co/auth/v1/callback', provider: null },
    error: null,
  });
  mockExchangeCodeForSession.mockResolvedValue({ data: { session: null }, error: null });
  mockGetSession.mockResolvedValue({ data: { session: null } });
  mockSignOut.mockResolvedValue({ error: null });
  mockSignInAnonymously.mockResolvedValue({ data: { session: null }, error: null });
  mockRefreshSession.mockResolvedValue({ data: { session: null }, error: null });
  mockDriveRefreshToken.mockReturnValue(null);
  mockGetLiveSession.mockReturnValue(null);
  globalThis.fetch = vi.fn();

  // 2. Clear module-level state (currentPort may be set from previous test)
  sut.unregisterCallbackHandler();
  await sut.signOut();
  capturedOnUrlHandler = null;

  // 3. Clear all mock call history (keeps implementations)
  vi.clearAllMocks();
});

// ---- Helpers ----

function makeMockSession(overrides: Record<string, unknown> = {}) {
  return {
    access_token: 'access-123',
    refresh_token: 'refresh-123',
    expires_at: Math.floor(Date.now() / 1000) + 3600,
    provider_token: 'ya29.provider-token',
    provider_refresh_token: 'google-refresh-token',
    user: {
      id: 'user-1',
      email: 'test@example.com',
      user_metadata: {
        full_name: 'Test User',
        avatar_url: 'https://example.com/avatar.png',
      },
    },
    ...overrides,
  };
}

function makeMockOAuthData(url?: string) {
  return {
    data: {
      url: url ?? 'https://test-project.supabase.co/auth/v1/authorize?provider=google',
      provider: null,
    },
    error: null,
  };
}

function makeMockSessionData(session: Record<string, unknown>) {
  return { data: { session }, error: null };
}

function makeMockError(message: string) {
  return { data: { session: null }, error: { message } };
}

// ---- Tests ----

describe('SupabaseAuthService — signInWithGoogle', () => {
  it('starts loopback server and opens OAuth URL in browser', async () => {
    mockPluginStart.mockResolvedValue(48723);
    mockSignInWithOAuth.mockResolvedValue(makeMockOAuthData());

    await sut.signInWithGoogle();

    expect(mockPluginStart).toHaveBeenCalledTimes(1);
    expect(mockSignInWithOAuth).toHaveBeenCalledTimes(1);
    const options = mockSignInWithOAuth.mock.calls[0]?.[0];
    expect(options).toMatchObject({
      provider: 'google',
      options: {
        redirectTo: 'http://127.0.0.1:48723/callback',
      },
    });
    expect(mockOpenUrl).toHaveBeenCalledTimes(1);
  });

  it('throws when plugin.start() fails', async () => {
    mockPluginStart.mockRejectedValue(new Error('port bind failed'));

    await expect(sut.signInWithGoogle()).rejects.toThrow('Failed to start loopback server');
    expect(mockSignInWithOAuth).not.toHaveBeenCalled();
  });

  it('throws when Supabase OAuth URL generation fails', async () => {
    mockPluginStart.mockResolvedValue(48723);
    mockSignInWithOAuth.mockResolvedValue({
      data: { url: null },
      error: { message: 'OAuth config missing' },
    });

    await expect(sut.signInWithGoogle()).rejects.toThrow('Supabase OAuth error');
    expect(mockOpenUrl).not.toHaveBeenCalled();
  });

  it('cancels previous loopback before starting a new one', async () => {
    mockPluginStart.mockResolvedValueOnce(48000).mockResolvedValueOnce(48001);
    mockSignInWithOAuth.mockResolvedValue(makeMockOAuthData());

    await sut.signInWithGoogle();
    expect(mockPluginStart).toHaveBeenCalledTimes(1);
    expect(mockPluginCancel).not.toHaveBeenCalled();

    await sut.signInWithGoogle();
    expect(mockPluginCancel).toHaveBeenCalledWith(48000);
    expect(mockPluginStart).toHaveBeenCalledTimes(2);
  });
});

describe('SupabaseAuthService — registerSupabaseCallbackHandler', () => {
  it('subscribes via onUrl exactly once', async () => {
    await sut.registerSupabaseCallbackHandler();
    expect(capturedOnUrlHandler).not.toBeNull();

    // Second call is idempotent — no new subscription
    await sut.registerSupabaseCallbackHandler();
    expect(capturedOnUrlHandler).not.toBeNull();
  });

  it('handles callback with valid code and exchanges for session', async () => {
    await sut.registerSupabaseCallbackHandler();
    expect(capturedOnUrlHandler).not.toBeNull();

    const mockSession = makeMockSessionData(makeMockSession());
    mockExchangeCodeForSession.mockResolvedValue(mockSession);

    await capturedOnUrlHandler!('http://127.0.0.1:48723/callback?code=valid-code');

    expect(mockExchangeCodeForSession).toHaveBeenCalledWith('valid-code');
    expect(mockSetSupabaseSession).toHaveBeenCalledTimes(1);
    expect(mockSetSupabaseSession.mock.calls[0]?.[0]).toMatchObject({
      accessToken: 'access-123',
      providerToken: 'ya29.provider-token',
      // DTL-1: provider_refresh_token must be persisted at sign-in (not dropped)
      driveRefreshToken: 'google-refresh-token',
    });
    expect(mockSavePersistedAuth).toHaveBeenCalledTimes(1);
  });

  it('ignores callback when code is missing', async () => {
    await sut.registerSupabaseCallbackHandler();

    await capturedOnUrlHandler!('http://127.0.0.1:48723/callback?error=access_denied');

    expect(mockExchangeCodeForSession).not.toHaveBeenCalled();
    expect(mockSetSupabaseSession).not.toHaveBeenCalled();
  });

  it('handles code exchange failure gracefully', async () => {
    await sut.registerSupabaseCallbackHandler();
    mockExchangeCodeForSession.mockResolvedValue(makeMockError('Invalid code'));

    await capturedOnUrlHandler!('http://127.0.0.1:48723/callback?code=bad-code');

    expect(mockExchangeCodeForSession).toHaveBeenCalledWith('bad-code');
    expect(mockSetSupabaseSession).not.toHaveBeenCalled();
  });
});

describe('SupabaseAuthService — getDriveToken', () => {
  it('returns provider_token from current session', async () => {
    mockGetSession.mockResolvedValue({
      data: { session: { provider_token: 'ya29.drive-token' } },
    });

    const token = await sut.getDriveToken();
    expect(token).toBe('ya29.drive-token');
  });

  it('returns null when no session exists', async () => {
    mockGetSession.mockResolvedValue({ data: { session: null } });

    const token = await sut.getDriveToken();
    expect(token).toBeNull();
  });

  it('returns null when provider_token is not present', async () => {
    mockGetSession.mockResolvedValue({
      data: { session: { provider_token: null } },
    });

    const token = await sut.getDriveToken();
    expect(token).toBeNull();
  });
});

describe('SupabaseAuthService — refreshDriveToken layered refresh (DTL-1/DTL-2/DTL-3)', () => {
  function mockGoogleTokenResponse(data: Record<string, unknown>, ok = true) {
    vi.mocked(globalThis.fetch).mockResolvedValue({
      ok,
      status: ok ? 200 : 400,
      json: async () => data,
    } as Response);
  }

  it('uses the re-issued provider_token from GoTrue refreshSession (path 1)', async () => {
    mockRefreshSession.mockResolvedValue({
      data: { session: makeMockSession({ provider_token: 'ya29.reissued' }) },
      error: null,
    });

    const token = await sut.refreshDriveToken();
    expect(token).toBe('ya29.reissued');
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it('falls through to the Google token endpoint when GoTrue drops provider_token (path 2)', async () => {
    vi.stubEnv('VITE_GOOGLE_OAUTH_CLIENT_ID', 'client-123');
    vi.stubEnv('VITE_GOOGLE_OAUTH_CLIENT_SECRET', 'secret-456');
    mockRefreshSession.mockResolvedValue({
      data: { session: makeMockSession({ provider_token: null }) },
      error: null,
    });
    mockDriveRefreshToken.mockReturnValue('google-refresh-token');
    mockGoogleTokenResponse({ access_token: 'ya29.direct-exchange' });

    const token = await sut.refreshDriveToken();

    expect(token).toBe('ya29.direct-exchange');
    const [url, init] = vi.mocked(globalThis.fetch).mock.calls[0];
    expect(url).toBe('https://oauth2.googleapis.com/token');
    const body = (init?.body as URLSearchParams).toString();
    expect(body).toContain('grant_type=refresh_token');
    expect(body).toContain('client_id=client-123');
    expect(body).toContain('refresh_token=google-refresh-token');
  });

  it('throws typed AUTH_REQUIRED when no refresh token is persisted (path 3, retryable=false)', async () => {
    mockRefreshSession.mockResolvedValue({
      data: { session: makeMockSession({ provider_token: null }) },
      error: null,
    });
    mockDriveRefreshToken.mockReturnValue(null);

    const err = (await sut.refreshDriveToken().catch((e: Error) => e)) as Error & {
      code?: string;
      retryable?: boolean;
    };
    expect(err.message).toContain('sign in with Google again');
    expect(err.code).toBe('AUTH_REQUIRED');
    expect(err.retryable).toBe(false);
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it('throws typed AUTH_REQUIRED when env client credentials are missing (path 3)', async () => {
    vi.stubEnv('VITE_GOOGLE_OAUTH_CLIENT_ID', '');
    vi.stubEnv('VITE_GOOGLE_OAUTH_CLIENT_SECRET', '');
    mockRefreshSession.mockResolvedValue({
      data: { session: makeMockSession({ provider_token: null }) },
      error: null,
    });
    mockDriveRefreshToken.mockReturnValue('google-refresh-token');

    const err = (await sut.refreshDriveToken().catch((e: Error) => e)) as Error & { code?: string };
    expect(err.code).toBe('AUTH_REQUIRED');
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it('throws typed AUTH_REQUIRED when the Google token endpoint rejects (no raw token in error)', async () => {
    vi.stubEnv('VITE_GOOGLE_OAUTH_CLIENT_ID', 'client-123');
    vi.stubEnv('VITE_GOOGLE_OAUTH_CLIENT_SECRET', 'secret-456');
    mockRefreshSession.mockResolvedValue({
      data: { session: makeMockSession({ provider_token: null }) },
      error: null,
    });
    mockDriveRefreshToken.mockReturnValue('google-refresh-token');
    mockGoogleTokenResponse({ error: 'invalid_grant' }, false);

    const err = (await sut.refreshDriveToken().catch((e: Error) => e)) as Error;
    expect(err.message).toContain('sign in with Google again');
    expect(err.message).not.toContain('google-refresh-token');
    expect(err.message).not.toContain('secret-456');
  });

  it('never retries refresh in a hot loop — single attempt then typed failure', async () => {
    vi.stubEnv('VITE_GOOGLE_OAUTH_CLIENT_ID', 'client-123');
    vi.stubEnv('VITE_GOOGLE_OAUTH_CLIENT_SECRET', 'secret-456');
    mockRefreshSession.mockResolvedValue({
      data: { session: makeMockSession({ provider_token: null }) },
      error: null,
    });
    mockDriveRefreshToken.mockReturnValue('google-refresh-token');
    mockGoogleTokenResponse({ error: 'invalid_grant' }, false);

    await expect(sut.refreshDriveToken()).rejects.toThrow();
    // Exactly one Google token endpoint call
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  });
});

describe('SupabaseAuthService — signOut', () => {
  it('calls supabase.auth.signOut and clears auth state', async () => {
    mockPluginStart.mockResolvedValue(48000);
    mockSignInWithOAuth.mockResolvedValue(makeMockOAuthData());
    await sut.signInWithGoogle();
    mockSignOut.mockResolvedValue({ error: null });

    await sut.signOut();

    expect(mockSignOut).toHaveBeenCalled();
    expect(mockClearSupabaseSession).toHaveBeenCalled();
  });

  it('cancels active loopback server on sign out', async () => {
    mockPluginStart.mockResolvedValue(48000);
    mockSignInWithOAuth.mockResolvedValue(makeMockOAuthData());
    await sut.signInWithGoogle();
    mockSignOut.mockResolvedValue({ error: null });

    await sut.signOut();

    expect(mockPluginCancel).toHaveBeenCalledWith(48000);
  });

  it('handles signOut supabase error gracefully', async () => {
    mockPluginStart.mockResolvedValue(48000);
    mockSignInWithOAuth.mockResolvedValue(makeMockOAuthData());
    await sut.signInWithGoogle();
    mockSignOut.mockResolvedValue({ error: { message: 'network error' } });

    // Should not throw — warning is logged
    await expect(sut.signOut()).resolves.toBeUndefined();
  });
});

describe('SupabaseAuthService — signInAnonymously', () => {
  it('sets anonymous session on success', async () => {
    const anonSession = makeMockSession({
      email: null,
      user: {
        id: 'anon-1',
        email: null,
        user_metadata: {},
      },
      provider_token: null,
    });
    const anonData = { data: { session: anonSession }, error: null };
    mockSignInAnonymously.mockResolvedValue(anonData);

    await sut.signInAnonymously();

    expect(mockSignInAnonymously).toHaveBeenCalled();
    expect(mockSetSupabaseSession).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 'anon-1',
        email: null,
        displayName: null,
        providerToken: null,
      }),
    );
  });

  it('handles anonymous sign-in failure gracefully', async () => {
    mockSignInAnonymously.mockResolvedValue({
      data: { session: null },
      error: { message: 'not available' },
    });

    // Should not throw — warning is logged
    await expect(sut.signInAnonymously()).resolves.toBeUndefined();
    expect(mockSetSupabaseSession).not.toHaveBeenCalled();
  });

  it('no-ops entirely when a live session already exists (anon-clobber guard, DA-3)', async () => {
    mockGetLiveSession.mockReturnValue({ user: { id: 'real-user-1' } });

    await sut.signInAnonymously();

    // Never reaches the Supabase client, never touches authState
    expect(mockSignInAnonymously).not.toHaveBeenCalled();
    expect(mockSetSupabaseSession).not.toHaveBeenCalled();
  });

  it('discards the anonymous result if a live session lands during the await (DA-3.1)', async () => {
    const anonSession = makeMockSession({
      email: null,
      user: { id: 'anon-1', email: null, user_metadata: {} },
      provider_token: null,
    });
    mockSignInAnonymously.mockResolvedValue({ data: { session: anonSession }, error: null });
    // Passes the pre-check (cache empty), then a real session lands mid-flight
    mockGetLiveSession.mockReturnValueOnce(null).mockReturnValue({ user: { id: 'real-user-1' } });

    await sut.signInAnonymously();

    expect(mockSignInAnonymously).toHaveBeenCalledTimes(1);
    expect(mockSetSupabaseSession).not.toHaveBeenCalled();
  });
});

describe('SupabaseAuthService — restoreSession', () => {
  it('returns session from supabase.auth.getSession()', async () => {
    const session = makeMockSession();
    mockGetSession.mockResolvedValue({ data: { session } });

    const result = await sut.restoreSession();
    expect(result).toEqual(session);
  });

  it('returns null when no session', async () => {
    mockGetSession.mockResolvedValue({ data: { session: null } });

    const result = await sut.restoreSession();
    expect(result).toBeNull();
  });
});

describe('SupabaseAuthService — unregisterCallbackHandler', () => {
  it('removes the URL listener', async () => {
    await sut.registerSupabaseCallbackHandler();
    expect(capturedOnUrlHandler).not.toBeNull();

    sut.unregisterCallbackHandler();
    expect(capturedOnUrlHandler).toBeNull();
  });

  it('is safe to call when no handler is registered', () => {
    sut.unregisterCallbackHandler();
    // No throw
  });
});
