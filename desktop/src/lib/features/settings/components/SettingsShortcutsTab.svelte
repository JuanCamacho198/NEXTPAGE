<script lang="ts">
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { t }: Props = $props();

  type ShortcutDescriptor = {
    id: string;
    combo: string;
    descriptionKey: MessageKey;
  };

  const keyboardShortcuts: ShortcutDescriptor[] = [
    { id: 'reader-prev', combo: 'ArrowLeft', descriptionKey: 'settings.shortcuts.readerPrev' },
    { id: 'reader-next', combo: 'ArrowRight', descriptionKey: 'settings.shortcuts.readerNext' },
    { id: 'reader-scroll-up', combo: 'ArrowUp', descriptionKey: 'settings.shortcuts.readerScrollUp' },
    { id: 'reader-scroll-down', combo: 'ArrowDown', descriptionKey: 'settings.shortcuts.readerScrollDown' },
    { id: 'dialog-close', combo: 'Escape', descriptionKey: 'settings.shortcuts.closeDialog' },
    { id: 'reader-search', combo: 'Ctrl+F', descriptionKey: 'settings.shortcuts.search' },
    { id: 'reader-fullscreen', combo: 'F11', descriptionKey: 'settings.shortcuts.fullscreen' },
    { id: 'app-help', combo: '?', descriptionKey: 'settings.shortcuts.showHelp' },
    { id: 'app-command-palette', combo: 'Ctrl+K', descriptionKey: 'settings.shortcuts.commandPalette' },
    { id: 'app-toggle-dark', combo: 'Ctrl+D', descriptionKey: 'settings.shortcuts.toggleDarkMode' },
  ];
</script>

<div role="tabpanel" id="tabpanel-atajos" aria-labelledby="tab-atajos" class="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
  <section class="rounded-xl border border-(--color-border) bg-(--color-surface) p-4">
    <h3 class="mt-0 mb-2 text-sm font-semibold text-(--color-primary)">
      {t('settings.shortcuts.title')}
    </h3>
    <p class="text-2xs text-(--color-text-muted) mb-3">
      {t('settings.shortcuts.description')}
    </p>
    <ul class="m-0 p-0 list-none grid gap-2">
      {#each keyboardShortcuts as shortcut (shortcut.id)}
        <li class="flex items-center gap-2">
          <span class="inline-flex items-center justify-center min-w-[86px] px-2 py-1 rounded-md border border-(--color-border) font-mono text-2xs text-(--color-primary) bg-(--color-background)"
            >{shortcut.combo}</span
          >
          <span class="text-xs text-(--color-primary)">{t(shortcut.descriptionKey)}</span>
        </li>
      {/each}
    </ul>
  </section>
</div>
