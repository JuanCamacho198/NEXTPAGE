import { render, screen } from '@testing-library/svelte';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const t = (key: string) => key;

// vi.mock is hoisted, so vi.hoisted must define the mock data first
const { mockAppState } = vi.hoisted(() => {
  const mas = {
    t: (key: string) => key,
    navigateToHome: vi.fn(),
  };
  return { mockAppState: mas };
});

vi.mock('$lib/shared/stores/AppState.svelte', () => ({
  appState: mockAppState,
}));

vi.mock('$lib/stores/authState.svelte', () => ({
  authState: { isSignedIn: false, accessToken: null, email: null },
  setLocalUser: vi.fn(),
}));

vi.mock('$lib/stores/authPersistence', () => ({
  savePersistedAuth: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('$lib/features/library', async () => {
  const mod = await import('../../mocks/MockGoogleLoginButton.svelte');
  return { GoogleLoginButton: mod.default };
});

vi.mock('@tauri-apps/api/webviewWindow', () => ({
  getCurrentWebviewWindow: () => ({
    setFullscreen: vi.fn(),
    minimize: vi.fn(),
    toggleMaximize: vi.fn(),
    close: vi.fn(),
    isMaximized: vi.fn().mockResolvedValue(false),
    onResized: vi.fn(),
  }),
}));

import WelcomeScreen from '$lib/features/welcome/WelcomeScreen.svelte';
import { titlebarState } from '$lib/stores/titlebarState.svelte';

describe('WelcomeScreen', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    titlebarState.isCustomTitlebar = false;
  });

  it('bounds the login card with viewport cap and internal scroll classes', () => {
    render(WelcomeScreen, { props: { t } });

    const section = screen.getByLabelText('Sign in');
    expect(section.classList.contains('max-h-full')).toBe(true);

    const card = section.querySelector(':scope > div');
    expect(card?.classList.contains('max-h-full')).toBe(true);
    expect(card?.classList.contains('overflow-y-auto')).toBe(true);
  });

  it('hides the logo row when the custom titlebar flag is true, keeps nav', () => {
    titlebarState.isCustomTitlebar = true;
    render(WelcomeScreen, { props: { t } });

    expect(screen.queryByText('NextPage')).toBeNull();
    expect(screen.getByText('welcome.nav.features')).toBeInTheDocument();
    expect(screen.getByText('welcome.nav.trust')).toBeInTheDocument();
  });

  it('shows the logo row when the flag is false, keeps nav', () => {
    render(WelcomeScreen, { props: { t } });

    expect(screen.getByText('NextPage')).toBeInTheDocument();
    expect(screen.getByText('welcome.nav.features')).toBeInTheDocument();
    expect(screen.getByText('welcome.nav.trust')).toBeInTheDocument();
  });
});
