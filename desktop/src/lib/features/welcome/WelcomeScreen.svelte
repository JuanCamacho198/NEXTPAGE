<script lang="ts">
  import { authState, setLocalUser } from '$lib/stores/authState.svelte';
  import { savePersistedAuth } from '$lib/stores/authPersistence';
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import { GoogleLoginButton } from '$lib/features/library';
  import type { MessageKey } from '$lib/shared/i18n';
  import { Button } from '$lib/shared/ui';
  import LocalUserForm from './components/LocalUserForm.svelte';
  import type { LocalUserProfile } from '$lib/stores/authPersistence';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { t }: Props = $props();

  let showLocalForm = $state(false);
  let localFormError = $state<string | null>(null);
  let isCreatingLocal = $state(false);

  const DEV_NAME = 'Dev';
  const DEV_EMAIL = 'dev@local';

  /**
   * Dev escape hatch: in `import.meta.env.DEV`, the "Continuar en local"
   * button immediately seeds a mock local user and routes home. The
   * inline form is skipped — keeps dev iteration fast.
   */
  const isDev = import.meta.env.DEV;

  async function handleLocalDev(): Promise<void> {
    if (isCreatingLocal) return;
    isCreatingLocal = true;
    try {
      const profile: LocalUserProfile = {
        name: DEV_NAME,
        email: DEV_EMAIL,
        avatarUrl: null,
        localOnly: true,
      };
      setLocalUser(profile);
      await savePersistedAuth({ kind: 'local', profile });
      appState.navigateToHome();
    } finally {
      isCreatingLocal = false;
    }
  }

  async function handleLocalProdSubmit(name: string, email: string | null): Promise<void> {
    if (isCreatingLocal) return;
    const trimmedName = name.trim();
    if (trimmedName.length === 0) {
      localFormError = t('welcome.localNameRequired');
      return;
    }

    isCreatingLocal = true;
    try {
      const profile: LocalUserProfile = {
        name: trimmedName,
        email: email?.trim() ? email.trim() : null,
        avatarUrl: null,
        localOnly: true,
      };
      setLocalUser(profile);
      await savePersistedAuth({ kind: 'local', profile });
      appState.navigateToHome();
    } finally {
      isCreatingLocal = false;
    }
  }

  function toggleLocalForm(): void {
    showLocalForm = !showLocalForm;
    localFormError = null;
  }

  // ─── Google auth persistence (welcome context) ───
  // The GoogleLoginButton in SettingsPanel persists via a side-effect that
  // runs after `setSession` is called. On the welcome screen we want the
  // same behavior: as soon as the user is signed in, write the cache so a
  // re-launch skips welcome.
  $effect(() => {
    if (authState.isSignedIn && authState.accessToken) {
      const tokens = {
        accessToken: authState.accessToken,
        refreshToken: authState.refreshToken ?? '',
        idToken: '', // The idToken is only available at OAuth time; not on re-read.
        expiresIn: authState.expiresAt
          ? Math.max(0, Math.floor((authState.expiresAt - Date.now()) / 1000))
          : 0,
      };
      void savePersistedAuth({ kind: 'google', tokens }).catch((err) => {
        console.error('Failed to persist Google auth on welcome:', err);
      });
      appState.navigateToHome();
    }
  });
</script>

<div
  class="flex h-full w-full flex-col bg-(--color-background) text-(--color-primary) overflow-hidden"
>
  <!-- Header -->
  <header
    class="flex items-center justify-between px-6 py-4 border-b border-(--color-border) shrink-0"
  >
    <div class="flex items-center gap-3">
      <div
        class="flex size-9 items-center justify-center rounded-lg bg-(--color-primary) text-(--color-background) font-bold text-base shrink-0"
        aria-hidden="true"
      >
        N
      </div>
      <span class="text-lg font-semibold tracking-tight">NextPage</span>
    </div>
    <nav class="flex items-center gap-2 text-sm text-(--color-text-muted)" aria-label="Welcome nav">
      <a
        href="#features"
        class="px-3 py-1.5 rounded-md hover:text-(--color-primary) hover:bg-(--color-surface) transition-colors"
      >
        {t('welcome.nav.features')}
      </a>
      <a
        href="#trust"
        class="px-3 py-1.5 rounded-md hover:text-(--color-primary) hover:bg-(--color-surface) transition-colors"
      >
        {t('welcome.nav.trust')}
      </a>
    </nav>
  </header>

  <!-- Main 2-column layout -->
  <main class="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-8 p-6 lg:p-12 overflow-y-auto">
    <!-- Left: branding + features -->
    <section
      class="flex flex-col justify-center gap-6 max-w-xl mx-auto lg:mx-0 w-full"
      aria-labelledby="welcome-headline"
    >
      <p class="m-0 text-xs font-semibold uppercase tracking-wider text-(--color-accent-blue)">
        {t('welcome.eyebrow')}
      </p>
      <h1
        id="welcome-headline"
        class="m-0 text-3xl md:text-4xl lg:text-5xl font-bold leading-tight tracking-tight"
      >
        {t('welcome.headline')}
      </h1>
      <p class="m-0 text-base lg:text-lg text-(--color-text-muted) leading-relaxed">
        {t('welcome.subtitle')}
      </p>

      <ul id="features" class="m-0 p-0 list-none grid grid-cols-1 sm:grid-cols-2 gap-3 mt-2">
        <li
          class="flex items-start gap-3 rounded-lg border border-(--color-border) bg-(--color-surface) p-3"
        >
          <div
            class="flex size-8 shrink-0 items-center justify-center rounded-md bg-(--color-primary)/12 text-(--color-primary) font-semibold text-sm"
            aria-hidden="true"
          >
            1
          </div>
          <div>
            <p class="m-0 text-sm font-semibold">{t('welcome.feature1Label')}</p>
            <p class="mt-0.5 mb-0 text-xs text-(--color-text-muted) leading-relaxed">
              {t('welcome.feature1Description')}
            </p>
          </div>
        </li>
        <li
          class="flex items-start gap-3 rounded-lg border border-(--color-border) bg-(--color-surface) p-3"
        >
          <div
            class="flex size-8 shrink-0 items-center justify-center rounded-md bg-(--color-primary)/12 text-(--color-primary) font-semibold text-sm"
            aria-hidden="true"
          >
            2
          </div>
          <div>
            <p class="m-0 text-sm font-semibold">{t('welcome.feature2Label')}</p>
            <p class="mt-0.5 mb-0 text-xs text-(--color-text-muted) leading-relaxed">
              {t('welcome.feature2Description')}
            </p>
          </div>
        </li>
        <li
          class="flex items-start gap-3 rounded-lg border border-(--color-border) bg-(--color-surface) p-3"
        >
          <div
            class="flex size-8 shrink-0 items-center justify-center rounded-md bg-(--color-primary)/12 text-(--color-primary) font-semibold text-sm"
            aria-hidden="true"
          >
            3
          </div>
          <div>
            <p class="m-0 text-sm font-semibold">{t('welcome.feature3Label')}</p>
            <p class="mt-0.5 mb-0 text-xs text-(--color-text-muted) leading-relaxed">
              {t('welcome.feature3Description')}
            </p>
          </div>
        </li>
        <li
          class="flex items-start gap-3 rounded-lg border border-(--color-border) bg-(--color-surface) p-3"
        >
          <div
            class="flex size-8 shrink-0 items-center justify-center rounded-md bg-(--color-primary)/12 text-(--color-primary) font-semibold text-sm"
            aria-hidden="true"
          >
            4
          </div>
          <div>
            <p class="m-0 text-sm font-semibold">{t('welcome.feature4Label')}</p>
            <p class="mt-0.5 mb-0 text-xs text-(--color-text-muted) leading-relaxed">
              {t('welcome.feature4Description')}
            </p>
          </div>
        </li>
      </ul>
    </section>

    <!-- Right: login card -->
    <section
      class="flex items-center justify-center lg:items-center w-full max-w-md mx-auto lg:mx-0 lg:justify-self-end"
      aria-label="Sign in"
    >
      <div
        class="w-full rounded-2xl border border-(--color-border) bg-(--color-surface) p-6 lg:p-8 shadow-sm"
      >
        <h2 class="m-0 mb-1 text-xl font-semibold">{t('welcome.cardTitle')}</h2>
        <p class="m-0 mb-6 text-sm text-(--color-text-muted)">
          {t('welcome.cardSubtitle')}
        </p>

        <!-- Google sign-in: reuses the existing GoogleLoginButton with the
             i18n `t` prop. The button shows "Continue with Google" because
             the user is not yet signed in. -->
        <GoogleLoginButton {t} />

        <!-- Divider -->
        <div class="my-5 flex items-center gap-3" aria-hidden="true">
          <div class="h-px flex-1 bg-(--color-border)"></div>
          <span class="text-xs uppercase tracking-wider text-(--color-text-muted)">
            {t('welcome.divider')}
          </span>
          <div class="h-px flex-1 bg-(--color-border)"></div>
        </div>

        <!-- Local sign-in: dev escape vs prod inline form -->
        {#if isDev}
          <div class="flex flex-col gap-2">
            <Button
              onclick={() => void handleLocalDev()}
              variant="secondary"
              disabled={isCreatingLocal}
              class="w-full"
            >
              {t('welcome.continueLocal')}
            </Button>
            <p class="m-0 text-center text-[11px] text-(--color-text-muted)">
              {t('welcome.devSkipHint')}
            </p>
          </div>
        {:else}
          {#if !showLocalForm}
            <Button
              onclick={toggleLocalForm}
              variant="secondary"
              disabled={isCreatingLocal}
              class="w-full"
            >
              {t('welcome.continueLocal')}
            </Button>
          {:else}
            <LocalUserForm
              {t}
              error={localFormError}
              isSubmitting={isCreatingLocal}
              onCancel={toggleLocalForm}
              onSubmit={(name, email) => handleLocalProdSubmit(name, email)}
            />
          {/if}
        {/if}
      </div>
    </section>
  </main>

  <!-- Footer: 3 trust items -->
  <footer
    id="trust"
    class="grid grid-cols-1 sm:grid-cols-3 gap-4 px-6 py-4 border-t border-(--color-border) bg-(--color-surface) shrink-0"
  >
    <div class="flex items-center gap-2 text-sm text-(--color-text-muted)">
      <span
        class="flex size-7 shrink-0 items-center justify-center rounded-full bg-(--color-primary)/12 text-(--color-primary) text-xs font-semibold"
        aria-hidden="true"
      >
        ✓
      </span>
      <span>{t('welcome.footer.item1')}</span>
    </div>
    <div class="flex items-center gap-2 text-sm text-(--color-text-muted)">
      <span
        class="flex size-7 shrink-0 items-center justify-center rounded-full bg-(--color-primary)/12 text-(--color-primary) text-xs font-semibold"
        aria-hidden="true"
      >
        ✓
      </span>
      <span>{t('welcome.footer.item2')}</span>
    </div>
    <div class="flex items-center gap-2 text-sm text-(--color-text-muted)">
      <span
        class="flex size-7 shrink-0 items-center justify-center rounded-full bg-(--color-primary)/12 text-(--color-primary) text-xs font-semibold"
        aria-hidden="true"
      >
        ✓
      </span>
      <span>{t('welcome.footer.item3')}</span>
    </div>
  </footer>
</div>
