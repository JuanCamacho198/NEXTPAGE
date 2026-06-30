<script lang="ts">
  import { Button } from '$lib/shared/ui';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    error: string | null;
    isSubmitting: boolean;
    onCancel: () => void;
    onSubmit: (name: string, email: string | null) => void;
  };

  let { t, error, isSubmitting, onCancel, onSubmit }: Props = $props();

  let name = $state('');
  let email = $state('');

  function handleSubmit(event: SubmitEvent): void {
    event.preventDefault();
    onSubmit(name, email.trim() ? email.trim() : null);
  }
</script>

<form
  class="flex flex-col gap-3"
  onsubmit={handleSubmit}
  novalidate
  aria-label={t('welcome.localFormAriaLabel')}
>
  <div class="flex flex-col gap-1">
    <label class="text-xs font-medium text-(--color-text-muted)" for="local-name">
      {t('welcome.localNameLabel')}
    </label>
    <input
      id="local-name"
      type="text"
      bind:value={name}
      placeholder={t('welcome.localNamePlaceholder')}
      required
      autocomplete="name"
      maxlength="80"
      disabled={isSubmitting}
      class="w-full rounded-md border border-(--color-border) bg-(--color-background) px-3 py-2 text-sm text-(--color-primary) placeholder:text-(--color-text-muted) focus:outline-none focus:ring-2 focus:ring-(--color-primary) disabled:opacity-50"
    />
  </div>

  <div class="flex flex-col gap-1">
    <label class="text-xs font-medium text-(--color-text-muted)" for="local-email">
      {t('welcome.localEmailLabel')}
    </label>
    <input
      id="local-email"
      type="email"
      bind:value={email}
      placeholder={t('welcome.localEmailPlaceholder')}
      autocomplete="email"
      maxlength="120"
      disabled={isSubmitting}
      class="w-full rounded-md border border-(--color-border) bg-(--color-background) px-3 py-2 text-sm text-(--color-primary) placeholder:text-(--color-text-muted) focus:outline-none focus:ring-2 focus:ring-(--color-primary) disabled:opacity-50"
    />
  </div>

  {#if error}
    <p
      role="alert"
      class="m-0 rounded-md border border-red-300 bg-red-50 px-2 py-1 text-xs text-red-900"
    >
      {error}
    </p>
  {/if}

  <div class="flex items-center gap-2 mt-1">
    <Button type="submit" variant="primary" size="sm" disabled={isSubmitting} class="flex-1">
      {isSubmitting ? t('welcome.saving') : t('welcome.localSubmit')}
    </Button>
    <Button type="button" variant="ghost" size="sm" onclick={onCancel} disabled={isSubmitting}>
      {t('welcome.cancel')}
    </Button>
  </div>
</form>
