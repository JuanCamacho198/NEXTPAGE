<script lang="ts">
  import type { ProfileSessionViewModel } from '../profileSession';
  import type { MessageKey } from '$lib/shared/i18n';
  import Avatar from '$lib/shared/ui/forms/Avatar.svelte';

  type Props = {
    profile: ProfileSessionViewModel;
    isProfileLoading: boolean;
    profileError: string | null;
    profileAvatarBroken: boolean;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  // The `profileAvatarBroken` prop is accepted for backwards compatibility
  // (other call sites pass it), but the actual broken-image state now lives
  // inside the shared `Avatar` component.
  let {
    profile,
    isProfileLoading,
    profileError,
    profileAvatarBroken: _unused,
    t,
  }: Props = $props();
</script>

{#if profileError}
  <p class="mb-2 rounded border border-amber-300 bg-amber-50 px-2 py-1 text-xs text-amber-900">
    {profileError}
  </p>
{/if}

<article
  class="flex gap-3 items-start rounded-xl border border-(--color-border) bg-(--color-surface,#fff) p-3"
>
  <Avatar src={profile.avatarUrl ?? undefined} name={profile.name} size="lg" />

  <div class="min-w-0 flex-1">
    <p class="m-0 text-[11px] text-(--color-text-muted,#6b7280)">
      {t('settings.profile.nameLabel')}
    </p>
    <p class="my-0.5 mb-2 text-sm text-(--color-primary) wrap-break-word">
      {isProfileLoading ? t('settings.profile.loading') : profile.name}
    </p>

    <p class="m-0 text-[11px] text-(--color-text-muted,#6b7280)">
      {t('settings.profile.emailLabel')}
    </p>
    <p class="my-0.5 mb-2 text-sm text-(--color-primary) wrap-break-word">
      {isProfileLoading ? t('settings.profile.loading') : profile.email}
    </p>

    {#if !profile.isSignedIn}
      <p class="m-0 mt-1.5 text-xs text-(--color-text-muted,#6b7280)">
        {t('settings.profile.signInPrompt')}
      </p>
    {/if}
  </div>
</article>
