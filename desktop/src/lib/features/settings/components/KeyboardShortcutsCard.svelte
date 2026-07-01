<script lang="ts">
  import type { MessageKey } from '$lib/shared/i18n';

  type ShortcutDescriptor = {
    id: string;
    combo: string;
    descriptionKey: MessageKey;
  };

  type Props = {
    shortcuts: ShortcutDescriptor[];
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { shortcuts, t }: Props = $props();
</script>

<section
  class="mt-3 rounded-xl border border-(--color-border) bg-(--color-surface,#fff) p-3"
  aria-labelledby="shortcuts-heading"
>
  <h4 id="shortcuts-heading" class="mt-0 mb-2 text-sm font-semibold text-neutral-300">
    {t('settings.shortcuts.title')}
  </h4>
  <p class="text-xs text-(--color-text-muted) mb-3">{t('settings.shortcuts.description')}</p>
  <ul class="m-0 p-0 list-none grid gap-2">
    {#each shortcuts as shortcut (shortcut.id)}
      <li class="flex items-center gap-2">
        <span
          class="inline-flex items-center justify-center min-w-[86px] px-2 py-1 rounded-md border border-(--color-border) font-mono text-(--text-2xs) text-(--color-primary) bg-(--color-background)"
          >{shortcut.combo}</span
        >
        <span class="text-xs text-(--color-primary)">{t(shortcut.descriptionKey)}</span>
      </li>
    {/each}
  </ul>
</section>
