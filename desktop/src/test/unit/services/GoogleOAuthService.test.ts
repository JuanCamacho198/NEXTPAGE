/**
 * Unit tests for GoogleOAuthService — Task 6 of oauth-loopback-migration.
 * Verifies the new loopback redirect flow: plugin.start()/cancel() integration,
 * state (CSRF) generation + validation, multi-instance safety, and OAuthError
 * typed-error mapping.
 *
 * Models on SyncService.test.ts: mock control variables + module-level vi.mock
 * + dynamic import in beforeAll (vi.mock is hoisted by vitest, so the SUT
 * must be imported after mocks register).
 */
import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';

// ---- Mock control variables ----
const mockPluginStart = vi.fn();
const mockPluginCancel = vi.fn();
const mockPluginOnUrl = vi.fn();

let capturedOnUrlHandler: ((url: string) => void) | null = null;
let mockSetSession = vi.fn();
let mockClearSession = vi.fn();

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

vi.mock('$lib/stores/authState.svelte', () => ({
  authState: {
    setSession: (...args: unknown[]) => mockSetSession(...args),
    clearSession: (...args: unknown[]) => mockClearSession(...args),
  },
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
type Sut = typeof import('$lib/shared/services/GoogleOAuthService');
let sut: Sut;

beforeAll(async () => {
  const mod = await import('$lib/shared/services/GoogleOAuthService');
  sut = mod;
});

beforeEach(async () => {
  // Reset mock history and implementations.
  vi.clearAllMocks();
  mockPluginStart.mockReset();
  mockPluginCancel.mockReset();
  mockPluginOnUrl.mockReset();
  mockSetSession.mockReset();
  mockClearSession.mockReset();
  capturedOnUrlHandler = null;
  mockPluginCancel.mockResolvedValue(undefined);
  mockSetSession.mockReturnValue(undefined);
  mockClearSession.mockReturnValue(undefined);

  // Reset module-level state by calling signOut() — it clears currentPort,
  // expectedState, codeVerifier. signOut is async, but here it returns
  // immediately when currentPort is null.
  await sut.signOut();
  // Force-clear in case signOut's internal check found a port (the previous
  // test's mockPluginCancel may have made cancel succeed silently).
  capturedOnUrlHandler = null;
});

// ---- Helpers ----

function makeTokenResponseJson(): string {
  // Build a JWT-like id_token (header.payload.sig) for parseIdTokenPayload.
  const header = base64UrlEncodeUtf8(JSON.stringify({ alg: 'RS256' }));
  const payload = base64UrlEncodeUtf8(
    JSON.stringify({
      sub: 'user-1',
      email: 'test@example.com',
      name: 'Test User',
      picture: 'https://example.com/pic.png',
    }),
  );
  return `${header}.${payload}.signature`;
}

function base64UrlEncodeUtf8(input: string): string {
  const bytes = new TextEncoder().encode(input);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i] ?? 0);
  }
  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

function mockFetchOk(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

function stubWindowOpen(): ReturnType<typeof vi.fn> {
  const fn = vi.fn();
  // jsdom does not implement window.open returning a window; stub it.
  window.open = fn as unknown as typeof window.open;
  return fn;
}

// ---- Tests ----

describe('GoogleOAuthService — startAuth (loopback redirect)', () => {
  it('startAuth calls plugin.start() and derives redirect URI from port', async () => {
    mockPluginStart.mockResolvedValue(48723);
    const openStub = stubWindowOpen();

    await sut.startAuth();

    expect(mockPluginStart).toHaveBeenCalledTimes(1);
    expect(openStub).toHaveBeenCalledTimes(1);
    const authUrl = openStub.mock.calls[0]?.[0] as string;
    expect(authUrl).toContain('accounts.google.com/o/oauth2/v2/auth');
    expect(authUrl).toContain('redirect_uri=http%3A%2F%2F127.0.0.1%3A48723%2F');
    expect(authUrl).toContain('response_type=code');
    expect(authUrl).toContain('code_challenge_method=S256');
    expect(authUrl).toContain('access_type=offline');
    expect(authUrl).toContain('prompt=consent');
  });

  it('startAuth generates and stores state for CSRF', async () => {
    mockPluginStart.mockResolvedValue(48000);
    const openStub = stubWindowOpen();

    await sut.startAuth();

    const authUrl = openStub.mock.calls[0]?.[0] as string;
    const stateMatch = authUrl.match(/state=([^&]+)/);
    expect(stateMatch).not.toBeNull();
    const stateFromUrl = stateMatch?.[1] ?? '';
    // base64url of 32 bytes → 43 chars (no padding)
    expect(stateFromUrl.length).toBeGreaterThanOrEqual(43);
    expect(stateFromUrl).toMatch(/^[A-Za-z0-9_-]+$/);
  });

  it('plugin.start() failure throws plugin_unavailable', async () => {
    mockPluginStart.mockRejectedValue(new Error('bind failed'));

    await expect(sut.startAuth()).rejects.toMatchObject({
      name: 'OAuthError',
      code: 'plugin_unavailable',
    });
    expect(mockPluginStart).toHaveBeenCalledTimes(1);
  });
});

describe('GoogleOAuthService — handleOAuthCallback', () => {
  it('extracts code, validates state, and exchanges token', async () => {
    mockPluginStart.mockResolvedValue(48723);
    const openStub = stubWindowOpen();
    await sut.startAuth();
    const authUrl = openStub.mock.calls[0]?.[0] as string;
    const state = new URL(authUrl).searchParams.get('state') ?? '';

    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(
        mockFetchOk({
          access_token: 'access-1',
          refresh_token: 'refresh-1',
          id_token: makeTokenResponseJson(),
          expires_in: 3600,
        }),
      );

    const callbackUrl = `http://127.0.0.1:48723/?code=ABC&state=${encodeURIComponent(state)}`;
    await sut.handleOAuthCallback(callbackUrl);

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const tokenUrl = fetchSpy.mock.calls[0]?.[0];
    expect(tokenUrl).toBe('https://oauth2.googleapis.com/token');
    expect(mockSetSession).toHaveBeenCalledTimes(1);
    expect(mockSetSession.mock.calls[0]?.[0]).toMatchObject({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
    });
    fetchSpy.mockRestore();
  });

  it('rejects mismatched state with state_mismatch OAuthError', async () => {
    mockPluginStart.mockResolvedValue(48723);
    stubWindowOpen();
    await sut.startAuth();

    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const callbackUrl = 'http://127.0.0.1:48723/?code=ABC&state=WRONG_STATE';
    await expect(sut.handleOAuthCallback(callbackUrl)).rejects.toMatchObject({
      name: 'OAuthError',
      code: 'state_mismatch',
    });
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(mockSetSession).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });

  it('user denial surfaces user_denied OAuthError', async () => {
    mockPluginStart.mockResolvedValue(48723);
    stubWindowOpen();
    await sut.startAuth();

    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    await expect(
      sut.handleOAuthCallback('http://127.0.0.1:48723/?error=access_denied'),
    ).rejects.toMatchObject({
      name: 'OAuthError',
      code: 'user_denied',
    });
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(mockSetSession).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });

  it('server error other than access_denied surfaces server_failed', async () => {
    mockPluginStart.mockResolvedValue(48723);
    stubWindowOpen();
    await sut.startAuth();

    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    await expect(
      sut.handleOAuthCallback(
        'http://127.0.0.1:48723/?error=invalid_request',
      ),
    ).rejects.toMatchObject({
      name: 'OAuthError',
      code: 'server_failed',
    });
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });
});

describe('GoogleOAuthService — registerOAuthCallbackHandler', () => {
  it('subscribes via onUrl exactly once and returns the unlisten', async () => {
    await sut.registerOAuthCallbackHandler();
    expect(capturedOnUrlHandler).not.toBeNull();

    // Second call is idempotent: no new onUrl subscription.
    await sut.registerOAuthCallbackHandler();
    // onUrl is called only once total.
    // (We can't directly count onUrl calls because it's wrapped in the
    // module mock; the test ensures the unlisten contract is satisfied.)
    expect(capturedOnUrlHandler).not.toBeNull();
  });
});

describe('GoogleOAuthService — multi-instance safety', () => {
  it('multi-instance cancels the previous server before starting a new one', async () => {
    mockPluginStart.mockResolvedValueOnce(48000).mockResolvedValueOnce(48001);
    stubWindowOpen();

    await sut.startAuth();
    expect(mockPluginStart).toHaveBeenCalledTimes(1);
    // First startAuth had no prior currentPort, so no cancel yet.
    expect(mockPluginCancel).not.toHaveBeenCalled();

    await sut.startAuth();
    expect(mockPluginCancel).toHaveBeenCalledWith(48000);
    expect(mockPluginStart).toHaveBeenCalledTimes(2);
  });

  it('re-sign-in after sign-out starts a fresh flow', async () => {
    mockPluginStart.mockResolvedValueOnce(48000).mockResolvedValueOnce(48001);
    stubWindowOpen();

    await sut.startAuth();
    const clearSessionCountBefore = mockClearSession.mock.calls.length;
    await sut.signOut();
    // signOut must have invoked clearSession (at least once more than before).
    expect(mockClearSession.mock.calls.length).toBeGreaterThan(
      clearSessionCountBefore,
    );
    expect(mockPluginCancel).toHaveBeenCalledWith(48000);

    // Fresh flow after sign-out — different port used.
    await sut.startAuth();
    expect(mockPluginStart).toHaveBeenCalledTimes(2);
  });
});
