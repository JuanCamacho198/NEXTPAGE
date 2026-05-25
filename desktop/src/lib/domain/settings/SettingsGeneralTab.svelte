<script lang="ts">
  import Panel from "$lib/components/ui/layout/Panel.svelte";
  import Button from "$lib/components/ui/forms/Button.svelte";
  import GoogleLoginButton from "$lib/domain/library/GoogleLoginButton.svelte";
  import ProfileCard from "./ProfileCard.svelte";
  import KeyboardShortcutsCard from "./KeyboardShortcutsCard.svelte";
  import type { MessageKey } from "$lib/i18n";
  import type { UiLocale, ProfileSessionViewModel } from "$lib/types";

  type ShortcutDescriptor = {
    id: string;
    combo: string;
    descriptionKey: MessageKey;
  };

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    locale: UiLocale;
    preferredTheme: string;
    preferredFontScale: number;
    settingsError: string | null;
    settingsUnavailable: string | null;
    isSavingSettings: boolean;
    isProfileLoading: boolean;
    profileError: string | null;
    profileAvatarBroken: boolean;
    profile: ProfileSessionViewModel;
    keyboardShortcuts: ShortcutDescriptor[];
    onLocaleChange: (value: string) => void;
    onSaveSettings: () => void;
    onOpenResetModal: () => void;
    onPreferredThemeChange: (theme: string) => void;
    onPreferredFontScaleChange: (scale: number) => void;
  };

  let {
    t,
    locale,
    preferredTheme,
    preferredFontScale,
    settingsError,
    settingsUnavailable,
    isSavingSettings,
    isProfileLoading,
    profileError,
    profileAvatarBroken,
    profile,
    keyboardShortcuts,
    onLocaleChange,
    onSaveSettings,
    onOpenResetModal,
    onPreferredThemeChange,
    onPreferredFontScaleChange,
  }: Props = $props();
</script>

<Panel title={t("settings.authentication")} subtitle={t("settings.authDescription")}>
  <GoogleLoginButton />

  {#if settingsUnavailable}
    <p class="mb-2 rounded border border-amber-300 bg-amber-50 px-2 py-1 text-xs text-amber-900">
      {settingsUnavailable}
    </p>
  {/if}
  {#if settingsError}
    <p class="mb-2 rounded border border-red-300 bg-red-50 px-2 py-1 text-xs text-red-900">
      {settingsError}
    </p>
  {/if}
</Panel>

<Panel title={t("settings.tab.profile")} subtitle={t("settings.profile.description")}>
  <ProfileCard {profile} {isProfileLoading} {profileError} profileAvatarBroken={profileAvatarBroken} {t} />
  <KeyboardShortcutsCard shortcuts={keyboardShortcuts} {t} />
</Panel>

<Panel title={t("settings.localPreferences")} subtitle={t("settings.localPreferencesDescription")}>
  <div class="grid grid-cols-2 gap-4 mb-4">
    <!-- Language selector -->
    <div class="bg-[var(--color-surface)] border border-[var(--color-border)] rounded-xl p-4">
      <label class="block text-xs font-medium text-[var(--color-text-muted)] mb-3">{t("settings.language")}</label>
      <div class="flex gap-2">
        <button
          type="button"
          class="flex-1 flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg border-2 transition-all duration-200 cursor-pointer"
          class:border-[var(--color-primary)]={locale === "es"}
          class:border-[var(--color-border)]={locale !== "es"}
          class:bg-[var(--color-primary)]/10={locale === "es"}
          class:bg-[var(--color-background)]={locale !== "es"}
          onclick={() => onLocaleChange("es")}
        >
          <span class="text-xl">🇪🇸</span>
          <span class="text-xs font-medium text-[var(--color-primary)]">{t("settings.languageSpanish")}</span>
        </button>
        <button
          type="button"
          class="flex-1 flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg border-2 transition-all duration-200 cursor-pointer"
          class:border-[var(--color-primary)]={locale === "en"}
          class:border-[var(--color-border)]={locale !== "en"}
          class:bg-[var(--color-primary)]/10={locale === "en"}
          class:bg-[var(--color-background)]={locale !== "en"}
          onclick={() => onLocaleChange("en")}
        >
          <span class="text-xl">🇺🇸</span>
          <span class="text-xs font-medium text-[var(--color-primary)]">{t("settings.languageEnglish")}</span>
        </button>
      </div>
    </div>

    <!-- Theme selector -->
    <div class="bg-[var(--color-surface)] border border-[var(--color-border)] rounded-xl p-4">
      <label class="block text-xs font-medium text-[var(--color-text-muted)] mb-3">{t("settings.theme")}</label>
      <div class="flex gap-2">
        <button
          type="button"
          class="flex-1 flex items-center justify-center p-2.5 rounded-lg border-2 transition-all duration-200 cursor-pointer text-[var(--color-text-muted)]"
          class:border-[var(--color-primary)]={preferredTheme === "light"}
          class:border-[var(--color-border)]={preferredTheme !== "light"}
          class:bg-[var(--color-primary)]/10={preferredTheme === "light"}
          class:bg-[var(--color-background)]={preferredTheme !== "light"}
          class:text-[var(--color-primary)]={preferredTheme === "light"}
          onclick={() => onPreferredThemeChange("light")}
          title={t("settings.theme.light")}
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
        </button>
        <button
          type="button"
          class="flex-1 flex items-center justify-center p-2.5 rounded-lg border-2 transition-all duration-200 cursor-pointer text-[var(--color-text-muted)]"
          class:border-[var(--color-primary)]={preferredTheme === "dark"}
          class:border-[var(--color-border)]={preferredTheme !== "dark"}
          class:bg-[var(--color-primary)]/10={preferredTheme === "dark"}
          class:bg-[var(--color-background)]={preferredTheme !== "dark"}
          class:text-[var(--color-primary)]={preferredTheme === "dark"}
          onclick={() => onPreferredThemeChange("dark")}
          title={t("settings.theme.dark")}
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
        </button>
        <button
          type="button"
          class="flex-1 flex items-center justify-center p-2.5 rounded-lg border-2 transition-all duration-200 cursor-pointer text-[var(--color-text-muted)]"
          class:border-[var(--color-primary)]={preferredTheme === "sepia"}
          class:border-[var(--color-border)]={preferredTheme !== "sepia"}
          class:bg-[var(--color-primary)]/10={preferredTheme === "sepia"}
          class:bg-[var(--color-background)]={preferredTheme !== "sepia"}
          class:text-[var(--color-primary)]={preferredTheme === "sepia"}
          onclick={() => onPreferredThemeChange("sepia")}
          title={t("settings.theme.sepia")}
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
        </button>
        <button
          type="button"
          class="flex-1 flex items-center justify-center p-2.5 rounded-lg border-2 transition-all duration-200 cursor-pointer text-[var(--color-text-muted)]"
          class:border-[var(--color-primary)]={preferredTheme === "system"}
          class:border-[var(--color-border)]={preferredTheme !== "system"}
          class:bg-[var(--color-primary)]/10={preferredTheme === "system"}
          class:bg-[var(--color-background)]={preferredTheme !== "system"}
          class:text-[var(--color-primary)]={preferredTheme === "system"}
          onclick={() => onPreferredThemeChange("system")}
          title={t("settings.theme.system")}
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
        </button>
      </div>
    </div>
  </div>

  <!-- Font scale -->
  <div class="bg-[var(--color-surface)] border border-[var(--color-border)] rounded-xl p-4 mb-4">
    <label class="block text-xs font-medium text-[var(--color-text-muted)] mb-3" for="general-font-scale">{t("settings.fontScale")}: {preferredFontScale}%</label>
    <input
      type="range"
      id="general-font-scale"
      min="80"
      max="140"
      value={preferredFontScale}
      oninput={(e) => onPreferredFontScaleChange(Number((e.target as HTMLInputElement).value))}
      class="w-full h-1.5 appearance-none bg-[var(--color-border)] rounded-full outline-none slider-thumb"
    />
    <div class="flex items-center justify-center h-12 bg-[var(--color-background)] border border-[var(--color-border)] rounded-lg text-[var(--color-primary)] font-medium" style="font-size: {preferredFontScale * 0.14}px">
      Aa
    </div>
  </div>

  <div class="flex gap-2 mt-4">
    <Button onclick={onSaveSettings} disabled={isSavingSettings} size="sm">
      {isSavingSettings ? t("settings.saving") : t("settings.savePreferences")}
    </Button>
    <Button onclick={onOpenResetModal} variant="danger" size="sm">
      {t("settings.resetDefaults")}
    </Button>
  </div>
</Panel>
