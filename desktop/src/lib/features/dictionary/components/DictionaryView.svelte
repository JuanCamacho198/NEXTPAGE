<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import type { MessageKey } from '$lib/shared/i18n';
  import { dictionaryState } from '$lib/shared/stores/DictionaryState.svelte';
  import EmptyState from '$lib/shared/ui/feedback/EmptyState.svelte';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import Button from '$lib/shared/ui/forms/Button.svelte';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { t }: Props = $props();

  let searchQuery = $state('');
  let debouncedQuery = $state('');
  let newWord = $state('');
  let newTags = $state('');
  let isAdding = $state(false);
  let errorMsg = $state<string | null>(null);
  let duplicateWord = $state<string | null>(null);
  let editingId = $state<string | null>(null);
  let editingValue = $state('');
  let editingTags = $state('');
  let importError = $state<string | null>(null);
  let importResult = $state<string | null>(null);
  let debounceTimer: ReturnType<typeof setTimeout> | null = null;

  const filteredWords = $derived.by(() => {
    if (!debouncedQuery.trim()) return dictionaryState.words;
    return dictionaryState.search(debouncedQuery.trim(), 50);
  });

  $effect(() => {
    const q = searchQuery;
    if (debounceTimer) clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      debouncedQuery = q;
    }, 250);
  });

  onMount(() => {
    void dictionaryState.load();
  });

  onDestroy(() => {
    if (debounceTimer) clearTimeout(debounceTimer);
  });

  async function handleAdd(): Promise<void> {
    const trimmed = newWord.trim();
    if (!trimmed) return;
    const tags = newTags.split(',').map((s) => s.trim()).filter(Boolean);
    isAdding = true;
    errorMsg = null;
    duplicateWord = null;
    try {
      await dictionaryState.add(trimmed, { tags });
      newWord = '';
      newTags = '';
    } catch (e) {
      const msg = e instanceof Error ? e.message : t('errors.commandFailure');
      if (msg.includes('dictionary.duplicate') || msg.includes('duplicate')) {
        duplicateWord = trimmed;
        errorMsg = t('dictionary.duplicate', { word: trimmed });
      } else {
        errorMsg = msg;
      }
    } finally {
      isAdding = false;
    }
  }

  async function handleDelete(id: string): Promise<void> {
    try {
      await dictionaryState.remove(id);
    } catch {}
  }

  function startEdit(w: { id: string; word: string; tags?: string[] | null }): void {
    editingId = w.id;
    editingValue = w.word;
    editingTags = (w.tags ?? []).join(', ');
    errorMsg = null;
    duplicateWord = null;
  }

  function cancelEdit(): void {
    editingId = null;
    editingValue = '';
    editingTags = '';
  }

  async function handleEditSave(id: string): Promise<void> {
    const trimmed = editingValue.trim();
    if (!trimmed) return;
    const tags = editingTags.split(',').map((s) => s.trim()).filter(Boolean);
    try {
      await dictionaryState.update(id, { word: trimmed, tags });
      editingId = null;
      editingValue = '';
      editingTags = '';
      errorMsg = null;
      duplicateWord = null;
    } catch (e) {
      const msg = e instanceof Error ? e.message : t('errors.commandFailure');
      if (msg.includes('dictionary.duplicate') || msg.includes('duplicate')) {
        duplicateWord = trimmed;
        errorMsg = t('dictionary.duplicate', { word: trimmed });
      } else {
        errorMsg = msg;
      }
    }
  }

  async function handleToggleFav(id: string): Promise<void> {
    await dictionaryState.toggleFavorite(id);
  }

  async function handleExport(format: 'json' | 'csv'): Promise<void> {
    try {
      const data = await dictionaryState.exportData(format);
      const blob = new Blob([data], { type: format === 'json' ? 'application/json' : 'text/csv' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `dictionary.${format}`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      importError = e instanceof Error ? e.message : 'Export failed';
    }
  }

  async function handleImportFile(e: Event): Promise<void> {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const text = await file.text();
    const format = file.name.endsWith('.csv') ? 'csv' : 'json';
    importError = null;
    importResult = null;
    try {
      const res = await dictionaryState.importData(text, format);
      importResult = `Imported ${res.imported}, errors ${res.errors.length}`;
      if (res.errors.length) importError = res.errors.map((x) => `row ${x.row}: ${x.reason}`).join('; ');
    } catch (err) {
      importError = err instanceof Error ? err.message : 'Import failed';
    } finally {
      input.value = '';
    }
  }

  const handleKeydown = (e: KeyboardEvent): void => {
    if (e.key === 'Enter') void handleAdd();
  };

  const handleEditKeydown = (e: KeyboardEvent, id: string): void => {
    if (e.key === 'Enter') void handleEditSave(id);
    if (e.key === 'Escape') cancelEdit();
  };
</script>

<section class="space-y-5 max-w-3xl">
  <header class="flex flex-col gap-1">
    <h1 class="text-3xl font-semibold tracking-tight text-(--color-primary)">{t('dictionary.title')}</h1>
    <p class="text-sm text-(--color-text-muted)">{t('dictionary.subtitle')}</p>
  </header>

  <div class="flex gap-2">
    <div class="relative flex-1 flex items-center">
      <Icon name="search" size="sm" class="pointer-events-none absolute left-3 text-(--color-text-muted)" />
      <input
        type="text"
        class="w-full h-11 pl-10 pr-3 rounded-xl border border-(--color-border) bg-(--color-surface) text-(--color-primary) text-sm placeholder:text-(--color-text-muted) focus:outline-none focus:border-(--color-accent-blue) focus:shadow-[0_0_0_3px_rgba(73,212,255,0.15)]"
        placeholder={t('dictionary.searchPlaceholder')}
        bind:value={searchQuery}
      />
    </div>
    <label class="flex items-center gap-2 text-xs text-(--color-text-muted) cursor-pointer">
      <input type="file" accept=".json,.csv" class="hidden" onchange={handleImportFile} />
      <span class="px-3 py-2 rounded-xl border border-(--color-border) bg-(--color-surface) hover:bg-(--color-panel-accent) cursor-pointer">Import</span>
    </label>
    <button type="button" class="px-3 py-2 rounded-xl border border-(--color-border) bg-(--color-surface) text-xs cursor-pointer hover:bg-(--color-panel-accent)" onclick={() => void handleExport('json')}>Export JSON</button>
    <button type="button" class="px-3 py-2 rounded-xl border border-(--color-border) bg-(--color-surface) text-xs cursor-pointer hover:bg-(--color-panel-accent)" onclick={() => void handleExport('csv')}>CSV</button>
  </div>
  {#if importError}<p class="text-xs text-amber-600">{importError}</p>{/if}
  {#if importResult}<p class="text-xs text-green-600">{importResult}</p>{/if}

  <div class="rounded-xl border border-(--color-border) bg-(--color-surface) p-4">
    <div class="flex gap-2">
      <input
        type="text"
        class="flex-1 h-10 px-3 rounded-xl border border-(--color-border) bg-(--color-background) text-(--color-primary) text-sm placeholder:text-(--color-text-muted) focus:outline-none focus:border-(--color-accent-blue)"
        placeholder={t('dictionary.wordPlaceholder')}
        bind:value={newWord}
        onkeydown={handleKeydown}
        disabled={isAdding}
      />
      <input
        type="text"
        class="w-32 h-10 px-2 rounded-xl border border-(--color-border) bg-(--color-background) text-(--color-primary) text-xs placeholder:text-(--color-text-muted) focus:outline-none focus:border-(--color-accent-blue)"
        placeholder="tags, comma"
        bind:value={newTags}
        onkeydown={handleKeydown}
        disabled={isAdding}
      />
      <Button size="sm" disabled={!newWord.trim() || isAdding} onclick={() => void handleAdd()}>
        {#if isAdding}
          {t('settings.saving')}
        {:else}
          {t('dictionary.addWord')}
        {/if}
      </Button>
    </div>
    {#if errorMsg}
      <p class="mt-2 text-xs {duplicateWord ? 'text-amber-600' : 'text-red-500'}">{errorMsg}</p>
    {/if}
  </div>

  {#if dictionaryState.isLoading}
    <div class="rounded-xl border border-(--color-border) bg-(--color-bg-panel) p-8 text-center text-sm text-(--color-text-muted)">{t('stats.loading')}</div>
  {:else if filteredWords.length === 0}
    <div class="flex min-h-[30vh] items-center justify-center">
      <EmptyState
        icon="search"
        title={dictionaryState.words.length === 0 ? t('dictionary.emptyTitle') : t('home.highlightsEmptyTitle')}
        description={dictionaryState.words.length === 0 ? t('dictionary.emptyDescription') : t('home.highlightsEmptyDescription')}
      />
    </div>
  {:else}
    <ul class="list-none p-0 m-0 flex flex-col gap-2">
      {#each filteredWords as w (w.id)}
        <li class="flex items-center justify-between gap-3 rounded-xl border border-(--color-border) bg-(--color-surface) px-4 py-3 hover:border-(--color-border-strong) transition-colors">
          <div class="min-w-0 flex-1">
            {#if editingId === w.id}
              <div class="flex gap-2 items-center">
                <input
                  type="text"
                  class="flex-1 h-8 px-2 rounded-lg border border-(--color-accent-blue) bg-(--color-background) text-(--color-primary) text-sm focus:outline-none"
                  bind:value={editingValue}
                  onkeydown={(e) => handleEditKeydown(e, w.id)}
                />
                <input type="text" class="w-24 h-8 px-2 rounded-lg border border-(--color-border) text-xs" bind:value={editingTags} placeholder="tags" />
                <button
                  type="button"
                  class="px-2 py-1 rounded-lg bg-(--color-accent-blue) text-white text-xs cursor-pointer hover:opacity-90"
                  onclick={() => void handleEditSave(w.id)}
                >{t('highlight.save')}</button>
                <button
                  type="button"
                  class="px-2 py-1 rounded-lg border border-(--color-border) text-xs text-(--color-text-muted) cursor-pointer hover:bg-(--color-surface-hover,rgba(25,41,62,0.06))"
                  onclick={cancelEdit}
                >{t('highlight.cancel')}</button>
              </div>
            {:else}
              <div class="flex items-center gap-2">
                <p class="text-sm font-medium text-(--color-primary) truncate">{w.word}</p>
                {#if w.isFavorite}<span class="text-amber-500 text-xs">★</span>{/if}
              </div>
              <div class="flex gap-1 mt-1 flex-wrap">
                {#each (w.tags ?? []) as tag}
                  <span class="px-1.5 py-0.5 rounded-full bg-(--color-panel-accent) text-[10px] text-(--color-primary) border border-(--color-border)">{tag}</span>
                {/each}
                {#if w.srsStage != null && w.srsStage > 0}<span class="px-1.5 py-0.5 rounded-full bg-blue-50 text-[10px] text-blue-600">SRS {w.srsStage}</span>{/if}
              </div>
              <p class="text-xs text-(--color-text-muted)">{w.updatedAt ? new Date(w.updatedAt).toLocaleDateString() : new Date(w.createdAt).toLocaleDateString()}</p>
            {/if}
          </div>
          {#if editingId !== w.id}
            <button
              type="button"
              class="flex h-8 w-8 items-center justify-center rounded-lg border border-transparent bg-transparent hover:bg-amber-50 hover:text-amber-500 transition-colors cursor-pointer"
              aria-label="favorite"
              onclick={() => void handleToggleFav(w.id)}
            >
              <span class="text-sm">{w.isFavorite ? '★' : '☆'}</span>
            </button>
            <button
              type="button"
              class="flex h-8 w-8 items-center justify-center rounded-lg border border-transparent bg-transparent text-(--color-text-muted) hover:bg-(--color-panel-accent) hover:text-(--color-primary) transition-colors cursor-pointer"
              aria-label={t('dictionary.wordPlaceholder')}
              onclick={() => startEdit(w)}
            >
              <Icon name="edit" size="sm" />
            </button>
            <button
              type="button"
              class="flex h-8 w-8 items-center justify-center rounded-lg border border-transparent bg-transparent text-(--color-text-muted) hover:bg-red-50 hover:text-red-500 hover:border-red-200 transition-colors cursor-pointer"
              aria-label={t('dictionary.deleteConfirm')}
              onclick={() => void handleDelete(w.id)}
            >
              <Icon name="trash" size="sm" />
            </button>
          {/if}
        </li>
      {/each}
    </ul>
  {/if}
</section>
