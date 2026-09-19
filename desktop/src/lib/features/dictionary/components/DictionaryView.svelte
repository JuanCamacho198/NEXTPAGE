<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import type { MessageKey } from '$lib/shared/i18n';
  import {
    dictionaryState,
    type DictionaryStateApi,
  } from '$lib/shared/stores/DictionaryState.svelte';
  import EmptyState from '$lib/shared/ui/feedback/EmptyState.svelte';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import Button from '$lib/shared/ui/forms/Button.svelte';
  import DictionaryKpiRow from './DictionaryKpiRow.svelte';
  import DictionaryRow from './DictionaryRow.svelte';
  import { deriveDictionaryKpis } from '../dictionaryKpis';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    dictionary?: DictionaryStateApi;
  };

  let { t, dictionary = dictionaryState }: Props = $props();

  type Tab = 'all' | 'recent' | 'az';

  let searchQuery = $state('');
  let debouncedQuery = $state('');
  let activeTab = $state<Tab>('all');
  let selectedId = $state<string | null>(null);
  let showAddForm = $state(false);
  let newWord = $state('');
  let newTags = $state('');
  let isAdding = $state(false);
  let errorMsg = $state<string | null>(null);
  let duplicateWord = $state<string | null>(null);
  let importError = $state<string | null>(null);
  let importResult = $state<string | null>(null);
  let debounceTimer: ReturnType<typeof setTimeout> | null = null;

  const tabs: { id: Tab; label: MessageKey }[] = [
    { id: 'all', label: 'dictionary.tabAll' },
    { id: 'recent', label: 'dictionary.tabRecent' },
    { id: 'az', label: 'dictionary.tabAz' },
  ];

  const kpis = $derived(deriveDictionaryKpis(dictionary.words));

  const filteredWords = $derived.by(() => {
    const query = debouncedQuery.trim();
    const base = query ? dictionary.search(query, 50) : [...dictionary.words];
    if (activeTab === 'recent') {
      return [...base].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
    }
    if (activeTab === 'az') {
      return [...base].sort((a, b) => (a.word ?? '').localeCompare(b.word ?? ''));
    }
    return base;
  });

  $effect(() => {
    const q = searchQuery;
    if (debounceTimer) clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      debouncedQuery = q;
    }, 250);
  });

  onMount(() => {
    void dictionary.load();
  });

  onDestroy(() => {
    if (debounceTimer) clearTimeout(debounceTimer);
  });

  async function handleAdd(): Promise<void> {
    const trimmed = newWord.trim();
    if (!trimmed) return;
    const tags = newTags
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    isAdding = true;
    errorMsg = null;
    duplicateWord = null;
    try {
      const created = await dictionary.add(trimmed, { tags });
      newWord = '';
      newTags = '';
      showAddForm = false;
      selectedId = created.id;
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

  async function handleExport(format: 'json' | 'csv'): Promise<void> {
    try {
      const data = await dictionary.exportData(format);
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
      const res = await dictionary.importData(text, format);
      importResult = `Imported ${res.imported}, errors ${res.errors.length}`;
      if (res.errors.length)
        importError = res.errors.map((x) => `row ${x.row}: ${x.reason}`).join('; ');
    } catch (err) {
      importError = err instanceof Error ? err.message : 'Import failed';
    } finally {
      input.value = '';
    }
  }
</script>

<section class="flex w-full flex-col gap-6 p-6">
  <header class="flex h-20 items-center justify-between gap-4">
    <div class="flex min-w-0 flex-col gap-1.5">
      <h1 class="truncate text-2xl font-extrabold text-(--color-primary)">
        {t('dictionary.title')}
      </h1>
      <p class="truncate text-2sm text-(--color-text-tertiary)">{t('dictionary.subtitle')}</p>
    </div>
    <button
      type="button"
      class="flex shrink-0 cursor-pointer items-center gap-2 rounded-[10px] bg-(--color-accent-blue) px-4 py-2.5 text-2sm font-bold text-white transition-opacity hover:opacity-90"
      onclick={() => (showAddForm = !showAddForm)}
    >
      <Icon name="add" size="md" />
      <span>{t('dictionary.newWord')}</span>
    </button>
  </header>

  <DictionaryKpiRow {t} {kpis} />

  <div class="flex gap-3">
    <div
      class="flex w-110 shrink-0 flex-col gap-3.5 rounded-lg border border-(--color-panel-border) bg-(--color-panel) p-4"
      data-testid="dictionary-words-panel"
    >
      <div
        class="flex items-center gap-2.5 rounded-[10px] border border-(--color-panel-border) bg-(--color-panel-input) px-3 py-2.5"
      >
        <Icon name="search" size="md" class="shrink-0 text-(--color-text-tertiary)" />
        <input
          type="text"
          class="w-full bg-transparent text-2sm text-(--color-primary) placeholder:text-(--color-text-tertiary) focus:outline-none"
          placeholder={t('dictionary.searchPlaceholder')}
          bind:value={searchQuery}
        />
      </div>

      <div class="flex items-center gap-2" data-testid="dictionary-tabs">
        {#each tabs as tab (tab.id)}
          <button
            type="button"
            class={`cursor-pointer rounded-full px-3 py-1.5 text-xs font-semibold transition-colors ${
              activeTab === tab.id
                ? 'bg-(--color-accent-fill) text-(--color-accent-blue)'
                : 'bg-(--color-panel-input) text-(--color-secondary)'
            }`}
            aria-pressed={activeTab === tab.id}
            onclick={() => (activeTab = tab.id)}
          >
            {t(tab.label)}
          </button>
        {/each}
      </div>

      <div class="flex items-center justify-end gap-2 text-xs text-(--color-text-tertiary)">
        <label
          class="cursor-pointer rounded-md border border-(--color-panel-border) px-2.5 py-1.5 hover:bg-(--color-panel-input)"
        >
          <input type="file" accept=".json,.csv" class="hidden" onchange={handleImportFile} />
          <span>Import</span>
        </label>
        <button
          type="button"
          class="cursor-pointer rounded-md border border-(--color-panel-border) px-2.5 py-1.5 hover:bg-(--color-panel-input)"
          onclick={() => void handleExport('json')}>Export JSON</button
        >
        <button
          type="button"
          class="cursor-pointer rounded-md border border-(--color-panel-border) px-2.5 py-1.5 hover:bg-(--color-panel-input)"
          onclick={() => void handleExport('csv')}>CSV</button
        >
      </div>
      {#if importError}<p class="text-xs text-amber-600">{importError}</p>{/if}
      {#if importResult}<p class="text-xs text-green-600">{importResult}</p>{/if}

      {#if showAddForm}
        <form
          class="flex flex-col gap-2 rounded-md border border-(--color-panel-border) bg-(--color-panel-input) p-3"
          onsubmit={(event) => {
            event.preventDefault();
            void handleAdd();
          }}
        >
          <div class="flex gap-2">
            <input
              type="text"
              class="h-10 min-w-0 flex-1 rounded-md border border-(--color-panel-border) bg-(--color-panel) px-3 text-2sm text-(--color-primary) placeholder:text-(--color-text-tertiary) focus:outline-none"
              placeholder={t('dictionary.wordPlaceholder')}
              bind:value={newWord}
              disabled={isAdding}
            />
            <input
              type="text"
              class="h-10 w-32 rounded-md border border-(--color-panel-border) bg-(--color-panel) px-2 text-xs text-(--color-primary) placeholder:text-(--color-text-tertiary) focus:outline-none"
              placeholder="tags, comma"
              bind:value={newTags}
              disabled={isAdding}
            />
            <Button size="sm" type="submit" disabled={!newWord.trim() || isAdding}>
              {#if isAdding}
                {t('settings.saving')}
              {:else}
                {t('dictionary.addWord')}
              {/if}
            </Button>
          </div>
          {#if errorMsg}
            <p class="text-xs {duplicateWord ? 'text-amber-600' : 'text-red-500'}">{errorMsg}</p>
          {/if}
        </form>
      {/if}

      {#if dictionary.isLoading}
        <div
          class="rounded-md border border-(--color-panel-border) bg-(--color-panel-input) p-8 text-center text-sm text-(--color-text-tertiary)"
        >
          {t('stats.loading')}
        </div>
      {:else if filteredWords.length === 0}
        <div class="flex min-h-[24vh] items-center justify-center">
          <EmptyState
            icon="search"
            title={dictionary.words.length === 0
              ? t('dictionary.emptyTitle')
              : t('home.highlightsEmptyTitle')}
            description={dictionary.words.length === 0
              ? t('dictionary.emptyDescription')
              : t('home.highlightsEmptyDescription')}
          />
        </div>
      {:else}
        <ul class="m-0 flex list-none flex-col gap-1.5 p-0" data-testid="dictionary-list">
          {#each filteredWords as w, index (w.id)}
            <DictionaryRow
              word={w}
              {index}
              selected={selectedId === w.id}
              {t}
              onselect={(id) => (selectedId = id)}
            />
          {/each}
        </ul>
      {/if}
    </div>
  </div>
</section>
