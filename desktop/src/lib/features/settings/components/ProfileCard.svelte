<script lang="ts">
  import { getProfileInitials, type ProfileSessionViewModel } from '../profileSession';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    profile: ProfileSessionViewModel;
    isProfileLoading: boolean;
    profileError: string | null;
    profileAvatarBroken: boolean;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { profile, isProfileLoading, profileError, profileAvatarBroken, t }: Props = $props();

  let avatarBroken = $state(false);

  $effect(() => {
    avatarBroken = profileAvatarBroken;
  });
</script>

{#if profileError}
  <p class="mb-2 rounded border border-amber-300 bg-amber-50 px-2 py-1 text-xs text-amber-900">
    {profileError}
  </p>
{/if}

<article
  class="flex gap-3 items-start rounded-xl border border-(--color-border) bg-(--color-surface,#fff) p-3"
>
  <div class="w-14 h-14 shrink-0">
    {#if profile.avatarUrl && !avatarBroken}
      <img
        src={profile.avatarUrl}
        alt={t('settings.profile.avatarAlt', { name: profile.name })}
        class="w-full h-full rounded-full object-cover block border border-(--color-border)"
        onerror={() => {
          avatarBroken = true;
        }}
      />
    {:else}
      <div
        class="w-full h-full rounded-full flex items-center justify-center text-sm font-bold text-(--color-primary) bg-(--color-primary)/12 border border-(--color-border)"
        aria-hidden="true"
      >
        {getProfileInitials(profile.name)}
      </div>
    {/if}
  </div>

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
