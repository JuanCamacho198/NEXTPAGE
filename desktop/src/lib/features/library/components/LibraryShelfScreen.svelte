<script lang="ts">
  import Button from '$lib/shared/ui/forms/Button.svelte';
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';
  import Toast from '$lib/shared/ui/feedback/Toast.svelte';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import ShelfGrid from './ShelfGrid.svelte';
  import ShelfList from './ShelfList.svelte';
  import ShelfDownloadsSection from './ShelfDownloadsSection.svelte';
  import { useLibraryShelf } from '$lib/features/library/useLibraryShelf.svelte';
  import { FILTER_OPTIONS, SORT_OPTIONS, getSafeProgressPercentage, type ShelfBook } from '$lib/features/library/utils';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    books: ShelfBook[];
    isImporting?: boolean;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onImportBook?: () => void;
    onOpenBook?: (book: ShelfBook) => void;
    onContinueReading?: (book: ShelfBook) => void;
    onToggleFavorite?: (book: ShelfBook) => void;
    onStatusChange?: (book: ShelfBook, status: string) => void;
    onViewDetails?: (book: ShelfBook) => void;
    onRemoveBook?: (book: ShelfBook) => void;
    onDownloaded?: () => void;
  };

  let {
    books,
    isImporting = false,
    t,
    onImportBook,
    onOpenBook,
    onContinueReading,
    onToggleFavorite,
    onStatusChange,
    onViewDetails,
    onRemoveBook,
    onDownloaded,
  }: Props = $props();

  const shelf = useLibraryShelf(() => books);

  let downloadSuccessVisible = $state(false);

  function handleDownloaded(): void {
    downloadSuccessVisible = true;
    onDownloaded?.();
  }

  const sortDropdownOptions = $derived(SORT_OPTIONS.map((o) => ({ value: o.key, label: o.label })));
  const activeSortLabel = $derived(SORT_OPTIONS.find((o) => o.key === shelf.activeSort)?.label ?? '');

  const totalBooks = $derived(books.length);
  const readingBooks = $derived(books.filter((b) => getSafeProgressPercentage(b) > 0 && getSafeProgressPercentage(b) < 100).length);
  const completedBooks = $derived(
    books.filter((b) => b.readingStatus === 'completed' || getSafeProgressPercentage(b) >= 100).length,
  );
</script>

<section class="space-y-5">
  <header
    class="rounded-(--radius-2xl) border border-(--color-border) bg-[linear-gradient(180deg,rgba(17,30,48,0.94),rgba(10,18,31,0.94))] p-5 shadow-(--shadow-hero)"
  >
    <div class="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
      <div class="space-y-2">
        <div>
          <h1 class="text-3xl font-semibold tracking-tight text-(--color-primary)">{t('library.title')}</h1>
          <p class="mt-1 text-sm text-(--color-text-muted)">Todos tus libros organizados en un solo lugar.</p>
        </div>
        <div class="flex flex-wrap gap-3 text-xs text-(--color-text-muted)">
          <div class="rounded-full border border-(--color-border) bg-(--color-surface-subtle) px-3 py-1.5">
            {t('shelf.booksCount', { count: totalBooks })}
          </div>
          <div class="rounded-full border border-(--color-border) bg-(--color-surface-subtle) px-3 py-1.5">
            {t('shelf.readingCount', { count: readingBooks })}
          </div>
          <div class="rounded-full border border-(--color-border) bg-(--color-surface-subtle) px-3 py-1.5">
            {t('shelf.completedCount', { count: completedBooks })}
          </div>
        </div>
      </div>
      <div class="flex w-full flex-col gap-3 xl:max-w-[640px]">
        <div class="flex flex-col gap-3 md:flex-row md:items-center">
          <label class="group relative flex-1">
            <span class="sr-only">Buscar libros</span>
            <svg
              class="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-(--color-text-muted)"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
            >
              <circle cx="11" cy="11" r="7"></circle>
              <path d="M20 20L17 17"></path>
            </svg>
            <input
              type="text"
              class="h-11 w-full rounded-2xl border border-(--color-border) bg-[rgba(8,17,31,0.72)] pl-11 pr-16 text-sm text-(--color-primary) outline-none placeholder:text-center placeholder:text-(--color-text-muted)"
              placeholder={t('library.searchPlaceholder')}
              bind:value={shelf.searchQuery}
            />
            <span
              class="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 rounded-md border border-(--color-border) px-1.5 py-0.5 text-micro text-(--color-text-muted)"
            >
              Ctrl K
            </span>
          </label>
          <Button
            onclick={onImportBook}
            disabled={isImporting}
            class="h-11 min-w-[170px] rounded-2xl bg-(--gradient-accent) !text-[#07111d] shadow-(--shadow-glow-strong)"
          >
            {isImporting ? t('shelf.importing') : t('shelf.importBook')}
          </Button>
        </div>
      </div>
    </div>
  </header>

  <section
    class="rounded-(--radius-2xl) border border-(--color-border) bg-(--color-bg-panel) p-4 shadow-(--shadow-section)"
  >
    <div class="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
      <fieldset class="border-0 p-0 m-0">
        <legend class="sr-only">{t('shelf.filterAria')}</legend>
        <div class="flex flex-wrap gap-2">
          {#each FILTER_OPTIONS as option}
            <button
              type="button"
              class={`rounded-2xl border px-3 py-2 text-xs font-medium transition ${shelf.activeFilter === option.key ? 'border-[rgba(82,143,255,0.4)] bg-[rgba(78,140,255,0.22)] text-(--color-primary)' : 'border-(--color-border) bg-(--color-surface-subtle) text-(--color-text-muted) hover:text-(--color-primary)'}`}
              onclick={() => {
                shelf.activeFilter = option.key;
              }}
            >
              {option.label}
            </button>
          {/each}
        </div>
      </fieldset>
      <div class="flex flex-col gap-3 md:flex-row md:items-center">
        <span class="text-xs text-(--color-text-muted)">{t('shelf.sortBy')}</span>
        <Dropdown options={sortDropdownOptions} bind:value={shelf.activeSort} class="min-w-[130px]">
          {#snippet trigger()}
            <span class="text-sm text-(--color-primary)">{activeSortLabel}</span>
            <svg class="ml-1 h-4 w-4 text-(--color-text-muted)" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
            </svg>
          {/snippet}
        </Dropdown>
        <fieldset class="inline-flex rounded-2xl border-(--color-border) bg-(--color-surface-subtle) p-1 border-0">
          <legend class="sr-only">{t('shelf.viewToggleAria')}</legend>
          <button
            type="button"
            class={`flex h-9 w-10 items-center justify-center rounded-xl ${shelf.activeView === 'grid' ? 'bg-[rgba(78,140,255,0.2)] text-(--color-primary)' : 'text-(--color-text-muted)'}`}
            aria-label={t('shelf.gridView')}
            onclick={() => {
              shelf.activeView = 'grid';
            }}
          >
            <Icon name="grid" size="sm" title={t('shelf.gridView')} />
          </button>
          <button
            type="button"
            class={`flex h-9 w-10 items-center justify-center rounded-xl ${shelf.activeView === 'list' ? 'bg-[rgba(78,140,255,0.2)] text-(--color-primary)' : 'text-(--color-text-muted)'}`}
            aria-label={t('shelf.listView')}
            onclick={() => {
              shelf.activeView = 'list';
            }}
          >
            <Icon name="list" size="sm" title={t('shelf.listView')} />
          </button>
        </fieldset>
      </div>
    </div>
  </section>

  {#if shelf.activeView === 'grid'}
    <ShelfGrid
      books={shelf.filteredBooks}
      {t}
      {onImportBook}
      {onOpenBook}
      {onContinueReading}
      {onToggleFavorite}
      {onStatusChange}
      {onViewDetails}
      {onRemoveBook}
    />
  {:else}
    <ShelfList
      books={shelf.filteredBooks}
      {t}
      {onImportBook}
      {onOpenBook}
      {onContinueReading}
      {onToggleFavorite}
      {onStatusChange}
      {onViewDetails}
      {onRemoveBook}
    />
  {/if}

  <ShelfDownloadsSection {t} onDownloaded={handleDownloaded} />

  <Toast type="success" message={t('shelf.downloadSuccess')} bind:visible={downloadSuccessVisible} onDismiss={() => (downloadSuccessVisible = false)} />
</section>
