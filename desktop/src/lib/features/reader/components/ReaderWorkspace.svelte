<script lang="ts">
  import PdfViewer from "./PdfViewer.svelte";
  import EpubViewer from "./EpubViewer.svelte";
  import SearchPanel from "./SearchPanel.svelte";
  import SelectionToolbar from "./SelectionToolbar.svelte";
  import Icon from "$lib/components/ui/navigation/Icon.svelte";
  import type { MessageKey } from "$lib/shared/i18n";
  import type { ReaderSettings } from "$lib/shared/types";
  import type { ReaderBook } from "$lib/shared/types/book";

  type Props = {
    activeReadingBook?: ReaderBook | null;
    readerSettings?: ReaderSettings;
    cfiLocation?: string;
    percentage?: number;
    searchResponse?: any;
    searchTargetLocator?: string | null;
    isSearching?: boolean;
    searchUnavailableReason?: string | null;
    readerError?: string | null;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onBackToHome: () => void;
    onPdfPageChange?: (page: number, total: number) => void;
    onPdfSessionProgress?: (event: any) => void;
    onEpubLocationChange?: (cfi: string, pct: number) => void;
    onReaderLocationContext?: (ctx: any) => void;
    onSearch?: (query: string, page: number) => void;
    onSearchJump?: (target: any) => void;
  };

  let {
    activeReadingBook = null,
    readerSettings = undefined,
    cfiLocation = "",
    percentage = 0,
    searchResponse = null,
    searchTargetLocator = null,
    isSearching = false,
    searchUnavailableReason = null,
    readerError = null,
    t,
    onBackToHome,
    onPdfPageChange,
    onPdfSessionProgress,
    onEpubLocationChange,
    onReaderLocationContext,
    onSearch,
    onSearchJump,
  }: Props = $props();

  // Selection state
  let selectedText = $state("");
  let selectionBounds = $state<{ left: number; top: number; right: number; bottom: number } | null>(null);
  let showToolbar = $state(false);
  let selectedColor = $state("#FACC15");

  // Search panel state
  let searchPanelOpen = $state(false);

  const isPdf = $derived(activeReadingBook?.format?.toLowerCase() === "pdf");
  const isEpub = $derived(activeReadingBook?.format?.toLowerCase() === "epub");
  const bookProgress = $derived(
    isPdf && activeReadingBook?.currentPage && activeReadingBook?.totalPages
      ? Math.round((activeReadingBook.currentPage / activeReadingBook.totalPages) * 100)
      : Math.round(percentage)
  );

  function handlePdfSelection(text: string, rect: { left: number; top: number; right: number; bottom: number; placement?: string }) {
    selectedText = text;
    selectionBounds = {
      left: rect.left,
      top: rect.top === 9999 ? 0 : rect.top,
      right: rect.right,
      bottom: rect.bottom,
    };
    showToolbar = true;
  }

  function handleCopy() {
    if (selectedText) {
      navigator.clipboard.writeText(selectedText);
    }
    dismissToolbar();
  }

  function handleNote(text: string) {
    // Future: save note to highlight
    dismissToolbar();
  }

  function handleColorSelect(color: string) {
    selectedColor = color;
  }

  function dismissToolbar() {
    showToolbar = false;
    selectedText = "";
    selectionBounds = null;
    window.getSelection()?.removeAllRanges();
  }

  function toggleSearch() {
    searchPanelOpen = !searchPanelOpen;
  }
</script>

<!-- Full viewport reader layout -->
<div class="flex h-screen flex-col bg-[#0B1120]">
  <!-- Header (64px) -->
  <header class="flex h-16 shrink-0 items-center justify-between border-b border-[#1E293B] px-8">
    <!-- Left: back + biblioteca -->
    <div class="flex items-center gap-2">
      <button type="button" onclick={onBackToHome} class="flex cursor-pointer items-center gap-2 text-[#94A3B8] hover:text-white">
        <Icon name="chevron-left" size={20} />
        <span class="font-inter text-sm font-medium text-[#94A3B8]">{t("reader.biblioteca")}</span>
      </button>
    </div>

    <!-- Center: book title -->
    <span class="font-inter text-sm font-medium text-[#94A3B8]">
      {activeReadingBook?.title ?? ""}
    </span>

    <!-- Right: tools -->
    <div class="flex items-center gap-6 text-[#94A3B8]">
      <button type="button" class="cursor-pointer text-[#94A3B8] hover:text-white" aria-label="menu">
        <Icon name="menu" size={20} />
      </button>
      <button type="button" onclick={toggleSearch} class="cursor-pointer text-[#94A3B8] hover:text-white" aria-label={t("epub.search")}>
        <Icon name="search" size={20} />
      </button>
      <button type="button" class="cursor-pointer text-[#94A3B8] hover:text-white" aria-label="font settings">
        <Icon name="type" size={20} />
      </button>
      <button type="button" class="cursor-pointer text-[#94A3B8] hover:text-white" aria-label="bookmark">
        <Icon name="bookmark" size={20} />
      </button>
      <button type="button" class="cursor-pointer text-[#94A3B8] hover:text-white" aria-label="fullscreen">
        <Icon name="maximize" size={20} />
      </button>
    </div>
  </header>

  <!-- Reading area (centered, fill remaining space) -->
  <div class="flex flex-1 items-center justify-center px-10">
    {#if readerError}
      <p class="font-inter text-sm text-white">{readerError}</p>
    {:else if !activeReadingBook}
      <p class="font-inter text-sm text-white">{t("reader.no_book_loaded")}</p>
    {:else if isPdf}
      <!-- White content card for PDF -->
      <div class="relative w-[800px] overflow-hidden rounded-xl bg-white shadow-lg">
        <PdfViewer
          filePath={activeReadingBook.filePath}
          bookId={activeReadingBook.id}
          initialPage={Math.max(1, activeReadingBook.currentPage || 1)}
          searchTargetLocator={searchTargetLocator}
          readerSettings={readerSettings}
          onPageChange={onPdfPageChange}
          onSessionProgress={onPdfSessionProgress}
          onselection={handlePdfSelection}
          {t}
        />
      </div>
    {:else if isEpub}
      <!-- White content card for EPUB -->
      <div class="relative w-[800px] rounded-xl bg-white shadow-lg">
        <EpubViewer
          filePath={activeReadingBook.filePath}
          initialLocation={cfiLocation}
          initialPercentage={percentage}
          searchTargetLocator={searchTargetLocator}
          readerSettings={readerSettings}
          onLocationContext={onReaderLocationContext}
          onLocationChange={onEpubLocationChange}
          {t}
        />
      </div>
    {:else}
      <p class="font-inter text-sm text-white">{t("reader.formato_no_soportado")}</p>
    {/if}

    <!-- Selection Toolbar (floating) -->
    {#if showToolbar && selectionBounds && selectedText}
      <SelectionToolbar
        {selectedText}
        selectionBounds={selectionBounds}
        onCopy={handleCopy}
        onNote={handleNote}
        onDismiss={dismissToolbar}
        onColorSelect={handleColorSelect}
        {t}
      />
    {/if}
  </div>

  <!-- Footer (48px) -->
  <footer class="flex h-12 shrink-0 items-center justify-between border-t border-[#1E293B] px-8">
    <span class="font-inter text-xs font-normal text-[#94A3B8]">
      {activeReadingBook?.title ?? ""}
    </span>
    <div class="flex items-center gap-3">
      <div class="h-2 w-[200px] rounded-full bg-[#1E293B]">
        <div
          class="h-full rounded-full bg-[#38BDF8] transition-all duration-300"
          style="width: {bookProgress}%"
        ></div>
      </div>
      <span class="font-inter text-xs font-normal text-[#94A3B8]">{bookProgress}%</span>
    </div>
  </footer>
</div>

<!-- Search Panel overlay -->
{#if searchPanelOpen && activeReadingBook}
  <SearchPanel
    bookId={activeReadingBook.id}
    disabledReason={searchUnavailableReason}
    isSearching={isSearching}
    response={searchResponse}
    onSearch={(query, page) => onSearch?.(query, page)}
    onJump={(target) => onSearchJump?.(target)}
    {t}
  />
{/if}
