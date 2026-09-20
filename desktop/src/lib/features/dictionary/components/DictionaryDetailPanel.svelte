<script lang="ts">
  import type { DictionaryWordDto } from '$lib/shared/types';
  import EmptyState from '$lib/shared/ui/feedback/EmptyState.svelte';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import {
    bookInitials,
    bookReferenceLine,
    formatPhonetic,
    hasAnyDetail,
    hasBookReference,
    hasQuote,
    type Translator,
  } from '../dictionaryEntry';

  type Props = {
    /** The selected entry, or null when the list has no selection. */
    word: DictionaryWordDto | null;
    t: Translator;
    onViewBook?: (bookId: string) => void;
    onEdit?: (id: string) => void;
    onDelete?: (id: string) => void;
  };

  let { word, t, onViewBook, onEdit, onDelete }: Props = $props();

  const tag = $derived(word?.partOfSpeech?.trim() ?? '');
  const phonetic = $derived(formatPhonetic(word?.phonetic ?? ''));
  const definition = $derived(word?.definition?.trim() ?? '');
  const example = $derived(word?.example?.trim() ?? '');
  const quote = $derived(word?.quote?.trim() ?? '');
  const bookTitle = $derived(word?.sourceBookTitle?.trim() ?? '');
  const referenceLine = $derived(bookReferenceLine(word?.sourceBookAuthor, word?.sourceChapter));
  const showQuote = $derived(word != null && hasQuote(word));
  const showReference = $derived(word != null && hasBookReference(word));
  const showCitation = $derived(showQuote || showReference);
  const hasDetails = $derived(word != null && hasAnyDetail(word));

  type SectionId = 'definition' | 'example' | 'citation';

  const sections = $derived.by<SectionId[]>(() => {
    const present: SectionId[] = [];
    if (definition) present.push('definition');
    if (example) present.push('example');
    if (showCitation) present.push('citation');
    return present;
  });
</script>

<section
  class="flex min-w-0 flex-1 flex-col gap-5 rounded-lg bg-(--color-panel) p-6"
  data-testid="dictionary-detail"
>
  {#if word == null}
    <div
      class="flex flex-1 items-center justify-center"
      data-testid="dictionary-detail-no-selection"
    >
      <EmptyState
        icon="book"
        title={t('dictionary.selectWordTitle')}
        description={t('dictionary.selectWordDescription')}
      />
    </div>
  {:else}
    <div class="flex min-w-0 flex-wrap items-center gap-3" data-testid="dictionary-detail-header">
      <h2
        class="min-w-0 truncate text-[32px] text-(--color-primary)"
        style="font-family: var(--font-serif)"
        data-testid="dictionary-detail-term"
      >
        {word.word}
      </h2>

      {#if tag}
        <span
          class="shrink-0 rounded-full bg-(--color-accent-fill) px-2.5 py-1 text-2xs font-semibold text-(--color-accent-blue)"
          data-testid="dictionary-detail-tag"
        >
          {tag}
        </span>
      {/if}

      {#if phonetic}
        <span
          class="min-w-0 truncate text-2sm text-(--color-text-tertiary)"
          data-testid="dictionary-detail-phonetic"
        >
          {phonetic}
        </span>
      {/if}

      <div class="ms-auto flex shrink-0 items-center gap-2" data-testid="dictionary-detail-actions">
        <button
          type="button"
          class="flex cursor-pointer items-center gap-1.5 rounded-sm bg-(--color-panel-input) px-1.5 py-2 text-xs font-semibold text-(--color-secondary) transition-colors hover:bg-(--color-accent-fill)"
          data-testid="dictionary-detail-view-book"
          onclick={() => onViewBook?.(word.sourceBookId ?? '')}
        >
          <Icon name="book-open" size="sm" />
          <span>{t('dictionary.viewBook')}</span>
        </button>

        <button
          type="button"
          class="flex cursor-pointer items-center gap-1.5 rounded-sm bg-(--color-panel-input) px-1.5 py-2 text-xs font-semibold text-(--color-secondary) transition-colors hover:bg-(--color-accent-fill)"
          data-testid="dictionary-detail-edit"
          onclick={() => onEdit?.(word.id)}
        >
          <Icon name="edit" size="sm" />
          <span>{t('dictionary.edit')}</span>
        </button>

        <button
          type="button"
          class="flex h-8.5 w-8.5 shrink-0 cursor-pointer items-center justify-center rounded-sm bg-(--color-error-soft) text-(--color-error) transition-opacity hover:opacity-90"
          aria-label={t('dictionary.deleteConfirm')}
          data-testid="dictionary-detail-delete"
          onclick={() => onDelete?.(word.id)}
        >
          <Icon name="trash" size="md" />
        </button>
      </div>
    </div>

    {#if !hasDetails}
      <div class="flex flex-1 items-center justify-center" data-testid="dictionary-detail-empty">
        <EmptyState
          icon="book"
          title={t('dictionary.noDetailTitle')}
          description={t('dictionary.noDetailDescription')}
        />
      </div>
    {:else}
      {#each sections as section, index (section)}
        {#if index > 0}
          <div
            class="h-px w-full shrink-0 bg-(--color-panel-border)"
            data-testid="dictionary-detail-divider"
          ></div>
        {/if}

        <div class="flex flex-col gap-2.5" data-testid="dictionary-detail-section">
          {#if section === 'definition'}
            <span
              class="flex items-center gap-2 text-xs font-semibold text-(--color-accent-blue)"
              data-testid="dictionary-detail-definition-label"
            >
              <Icon name="book-text" size="sm" />
              {t('dictionary.description')}
            </span>
            <p class="text-sm text-(--color-primary)" data-testid="dictionary-detail-definition">
              {definition}
            </p>
          {:else if section === 'example'}
            <span
              class="flex items-center gap-2 text-xs font-semibold text-(--color-accent-blue)"
              data-testid="dictionary-detail-example-label"
            >
              <Icon name="quote" size="sm" />
              {t('dictionary.personalExample')}
            </span>
            <p
              class="text-[15px] text-(--color-secondary)"
              style="font-family: var(--font-serif)"
              data-testid="dictionary-detail-example"
            >
              {example}
            </p>
          {:else}
            <div
              class="flex flex-col gap-2.5 rounded-md bg-(--color-panel-input) p-3.5"
              data-testid="dictionary-citation"
            >
              {#if showQuote}
                <p
                  class="text-2sm text-(--color-secondary)"
                  style="font-family: var(--font-serif)"
                  data-testid="dictionary-quote"
                >
                  {quote}
                </p>
              {/if}

              {#if showReference}
                <div class="flex items-center gap-2.5" data-testid="dictionary-reference">
                  <span
                    class="flex h-12.5 w-9 shrink-0 items-center justify-center rounded-[4px] bg-(--color-accent-blue) text-2xs font-semibold text-white"
                    aria-hidden="true"
                    data-testid="dictionary-reference-initials"
                  >
                    {bookInitials(bookTitle)}
                  </span>

                  <div class="flex min-w-0 flex-col gap-0.5">
                    <span
                      class="flex items-center gap-1.5 text-micro font-semibold text-(--color-accent-blue)"
                      data-testid="dictionary-reference-label"
                    >
                      <Icon name="book-open" size="sm" />
                      {t('dictionary.bookReference')}
                    </span>
                    {#if bookTitle}
                      <span
                        class="truncate text-2sm text-(--color-primary)"
                        data-testid="dictionary-reference-title"
                      >
                        {bookTitle}
                      </span>
                    {/if}
                    {#if referenceLine}
                      <span
                        class="truncate text-2xs text-(--color-text-tertiary)"
                        data-testid="dictionary-reference-author"
                      >
                        {referenceLine}
                      </span>
                    {/if}
                  </div>
                </div>
              {/if}
            </div>
          {/if}
        </div>
      {/each}
    {/if}
  {/if}
</section>
