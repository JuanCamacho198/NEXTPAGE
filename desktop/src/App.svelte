<script lang="ts">
  import { onMount } from "svelte";
  import DropMenu from "./lib/components/ui/DropMenu.svelte";
  import Button from "./lib/components/ui/Button.svelte";
  import SettingsPanel from "./lib/components/SettingsPanel.svelte";
  import LibraryView from "./lib/components/library/LibraryView.svelte";
  import ReadingStatsPanel from "./lib/components/stats/ReadingStatsPanel.svelte";
  import SearchPanel from "./lib/components/reader/SearchPanel.svelte";
  import EpubViewer from "./lib/components/EpubViewer.svelte";
  import PdfViewer from "./lib/components/PdfViewer.svelte";

  import { importBook, type ImportProgress } from "./lib/services/BookImportService";
  import { pickFile } from "./lib/services/FilePicker";
  import {
    getProgress,
    getReadingStats,
    listBooks,
    listLibraryBooks,
    saveProgress,
    saveReadingSession,
    searchBookText,
    updateBookProgress,
  } from "./lib/tauriClient";

  import type {
    BookDto,
    CommandErrorDto,
    LibraryBookDto,
    ReadingSessionInput,
    ReadingStatsSummaryDto,
    SaveProgressInput,
    SearchBookTextResponse,
    SearchNavigationTarget,
  } from "./lib/types";

  type ReaderBook = LibraryBookDto & {
    filePath: string;
  };

  type LibraryViewMode = "list" | "grid";

  const DOMAIN = {
    LIBRARY: "library",
    STATS: "stats",
    SEARCH: "search",
  } as const;

  type Domain = (typeof DOMAIN)[keyof typeof DOMAIN];
  type MaybeCommandError = Error & { commandError?: CommandErrorDto };

  let books = $state<ReaderBook[]>([]);
  let selectedBook = $state<ReaderBook | null>(null);
  let libraryViewMode = $state<LibraryViewMode>("list");
  let libraryUnavailableReason = $state<string | null>(null);
  let statsUnavailableReason = $state<string | null>(null);
  let searchUnavailableReason = $state<string | null>(null);

  let isSettingsOpen = $state(false);
  let isLoadingLibrary = $state(false);
  let isLoadingStats = $state(false);
  let isSearching = $state(false);
  let isImporting = $state(false);
  let importProgress = $state<ImportProgress | null>(null);

  let cfiLocation = $state("");
  let percentage = $state(0);
  let stats = $state<ReadingStatsSummaryDto | null>(null);
  let searchResponse = $state<SearchBookTextResponse | null>(null);
  let searchTargetLocator = $state<string | null>(null);
  let readerError = $state<string | null>(null);

  const mapCommandError = (error: unknown) => {
    const typed = error as MaybeCommandError;
    if (typed.commandError) {
      return typed.commandError;
    }

    const fallback = error instanceof Error ? error.message : "Unknown command failure";
    return {
      code: "INTERNAL_ERROR",
      message: fallback,
      recoverable: false,
    } satisfies CommandErrorDto;
  };

  const setDomainUnavailable = (domain: Domain, reason: string | null) => {
    if (domain === DOMAIN.LIBRARY) {
      libraryUnavailableReason = reason;
      return;
    }

    if (domain === DOMAIN.STATS) {
      statsUnavailableReason = reason;
      return;
    }

    searchUnavailableReason = reason;
  };

  const openBook = async (book: ReaderBook) => {
    selectedBook = book;
    searchResponse = null;
    searchTargetLocator = null;

    if (book.format.toLowerCase() === "epub") {
      try {
        const progress = await getProgress(book.id);
        cfiLocation = progress?.cfiLocation ?? "";
        percentage = progress?.percentage ?? 0;
      } catch {
        cfiLocation = "";
        percentage = 0;
      }
    }

    void loadStats(book.id);
  };

  const loadLibrary = async () => {
    isLoadingLibrary = true;
    readerError = null;

    try {
      const [libraryRows, sourceRows] = await Promise.all([listLibraryBooks(1), listBooks()]);
      const filePathById = new Map<string, string>(
        sourceRows.map((book: BookDto) => [book.id, book.filePath]),
      );

      books = libraryRows.map((entry: LibraryBookDto) => ({
        ...entry,
        filePath: filePathById.get(entry.id) ?? "",
      }));

      setDomainUnavailable(DOMAIN.LIBRARY, null);

      if (books.length > 0 && !selectedBook) {
        void openBook(books[0]);
      } else if (selectedBook) {
        const refreshed = books.find((item) => item.id === selectedBook?.id) ?? null;
        selectedBook = refreshed;
      }
    } catch (error) {
      const details = mapCommandError(error);
      if (details.recoverable) {
        setDomainUnavailable(DOMAIN.LIBRARY, details.message);
      } else {
        readerError = details.message;
      }
    } finally {
      isLoadingLibrary = false;
    }
  };

  const loadStats = async (bookId?: string) => {
    isLoadingStats = true;

    try {
      stats = await getReadingStats(bookId);
      setDomainUnavailable(DOMAIN.STATS, null);
    } catch (error) {
      const details = mapCommandError(error);
      if (details.recoverable) {
        setDomainUnavailable(DOMAIN.STATS, details.message);
      } else {
        readerError = details.message;
      }
      stats = null;
    } finally {
      isLoadingStats = false;
    }
  };

  const handleImportFile = async () => {
    const file = await pickFile();
    if (!file) {
      return;
    }

    isImporting = true;
    readerError = null;

    try {
      const format = file.name.toLowerCase().endsWith(".epub") ? "epub" : "pdf";
      const title = file.name.replace(/\.(pdf|epub)$/i, "");

      await importBook(
        {
          sourcePath: file.path,
          title,
          format,
        },
        (progress) => {
          importProgress = progress;
        },
      );

      await loadLibrary();
    } catch (error) {
      readerError = error instanceof Error ? error.message : "Import failed";
    } finally {
      isImporting = false;
      importProgress = null;
    }
  };

  const handlePdfPageChange = async (page: number, total: number) => {
    const current = selectedBook;
    if (!current) {
      return;
    }

    selectedBook = {
      ...current,
      currentPage: page,
      totalPages: total,
    };

    books = books.map((book) =>
      book.id === current.id
        ? {
            ...book,
            currentPage: page,
            totalPages: total,
          }
        : book,
    );

    try {
      await updateBookProgress(current.id, page);
    } catch {
      // keep reader responsive even if progress write fails
    }

    void loadStats(current.id);
  };

  const handlePdfSessionProgress = async (event: {
    startedAt: string;
    endedAt?: string;
    durationSeconds: number;
    startPercentage?: number;
    endPercentage?: number;
  }) => {
    const current = selectedBook;
    if (!current) {
      return;
    }

    const payload: ReadingSessionInput = {
      bookId: current.id,
      startedAt: event.startedAt,
      endedAt: event.endedAt,
      durationSeconds: event.durationSeconds,
      startPercentage: event.startPercentage,
      endPercentage: event.endPercentage,
    };

    try {
      await saveReadingSession(payload);
      void loadStats(current.id);
    } catch {
      // non-blocking stats event path
    }
  };

  const handleEpubLocationChange = async (nextLocation: string, nextPercentage: number) => {
    const current = selectedBook;
    if (!current) {
      return;
    }

    cfiLocation = nextLocation;
    percentage = Math.max(0, Math.min(100, nextPercentage));

    const payload: SaveProgressInput = {
      bookId: current.id,
      cfiLocation: nextLocation,
      percentage,
    };

    try {
      await saveProgress(payload);
    } catch {
      // keep UI usable even when save fails
    }

    void loadStats(current.id);
  };

  const handleSearch = async (query: string, page: number) => {
    if (!selectedBook) {
      return;
    }

    isSearching = true;

    try {
      searchResponse = await searchBookText({
        bookId: selectedBook.id,
        query,
        page,
        pageSize: 200,
      });
      setDomainUnavailable(DOMAIN.SEARCH, null);
    } catch (error) {
      const details = mapCommandError(error);
      if (details.recoverable) {
        setDomainUnavailable(DOMAIN.SEARCH, details.message);
      } else {
        readerError = details.message;
      }
      searchResponse = null;
    } finally {
      isSearching = false;
    }
  };

  const handleSearchJump = (target: SearchNavigationTarget) => {
    searchTargetLocator = target.locator;
  };

  const handleReaderLocationContext = () => {
    // reserved for index_book_text integration when extraction pipeline is wired
  };

  onMount(() => {
    void loadLibrary();
    void loadStats(undefined);
  });
</script>

<main class="min-h-screen bg-slate-100 text-slate-900">
  <div class="mx-auto max-w-7xl p-4 md:p-6">
    <header class="mb-4 flex items-center justify-between rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div>
        <h1 class="text-xl font-semibold">NextPage Desktop</h1>
        <p class="text-sm text-slate-500">Desktop parity integration: library, settings, stats, and reader search</p>
      </div>
      <div class="flex items-center gap-2">
        <Button onclick={handleImportFile} disabled={isImporting} size="sm">
          {isImporting ? "Importing..." : "Import Book"}
        </Button>
        <DropMenu position="bottom-right">
          {#snippet trigger()}
            <button class="rounded-md border border-slate-300 bg-white px-2 py-1 text-sm text-slate-700 hover:bg-slate-50" aria-label="Open menu">
              Menu
            </button>
          {/snippet}
          <button
            class="w-full px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-100 hover:text-gray-900"
            onclick={() => {
              isSettingsOpen = true;
            }}
          >
            Settings
          </button>
        </DropMenu>
      </div>
    </header>

    {#if importProgress}
      <p class="mb-3 text-sm text-slate-600">{importProgress.message}</p>
    {/if}

    {#if readerError}
      <p class="mb-3 rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-900">{readerError}</p>
    {/if}

    <SettingsPanel bind:isOpen={isSettingsOpen} />

    <div class="grid gap-4 lg:grid-cols-[340px_1fr]">
      <div class="space-y-4">
        <LibraryView
          books={books}
          selectedBookId={selectedBook?.id ?? null}
          isLoading={isLoadingLibrary}
          disabledReason={libraryUnavailableReason}
          viewMode={libraryViewMode}
          onToggleView={(mode) => {
            libraryViewMode = mode;
          }}
          onSelect={(book) => {
            selectedBook = books.find((entry) => entry.id === book.id) ?? null;
          }}
          onOpen={(book) => {
            const match = books.find((entry) => entry.id === book.id);
            if (match) {
              void openBook(match);
            }
          }}
        />

        <ReadingStatsPanel
          stats={stats}
          isLoading={isLoadingStats}
          disabledReason={statsUnavailableReason}
          selectedBookTitle={selectedBook?.title ?? null}
          onRefresh={() => {
            void loadStats(selectedBook?.id);
          }}
        />
      </div>

      <section class="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        {#if !selectedBook}
          <p class="text-sm text-slate-500">Open a book from the library to start reading.</p>
        {:else}
          <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 class="text-lg font-semibold text-slate-800">{selectedBook.title}</h2>
              <p class="text-sm text-slate-500">
                {selectedBook.author || "Unknown author"} · {selectedBook.format.toUpperCase()} · {selectedBook.currentPage}/{selectedBook.totalPages || "-"}
              </p>
            </div>
            {#if selectedBook.format.toLowerCase() === "epub"}
              <p class="text-xs text-slate-500">Location: {cfiLocation || "start"} · {Math.round(percentage)}%</p>
            {/if}
          </div>

          {#if selectedBook.format.toLowerCase() === "pdf"}
            <div class="mb-4 h-[520px] overflow-hidden rounded-lg border border-slate-200">
              <PdfViewer
                filePath={selectedBook.filePath}
                initialPage={Math.max(1, selectedBook.currentPage || 1)}
                searchTargetLocator={searchTargetLocator}
                onPageChange={handlePdfPageChange}
                onSessionProgress={handlePdfSessionProgress}
              />
            </div>
          {:else}
            <div class="mb-4 h-[520px] overflow-hidden rounded-lg border border-slate-200">
              <EpubViewer
                filePath={selectedBook.filePath}
                initialLocation={cfiLocation}
                initialPercentage={percentage}
                searchTargetLocator={searchTargetLocator}
                onLocationContext={handleReaderLocationContext}
                onLocationChange={handleEpubLocationChange}
              />
            </div>
          {/if}

          <SearchPanel
            bookId={selectedBook.id}
            disabledReason={searchUnavailableReason}
            isSearching={isSearching}
            response={searchResponse}
            onSearch={(query, page) => {
              void handleSearch(query, page);
            }}
            onJump={handleSearchJump}
          />
        {/if}
      </section>
    </div>
  </div>
</main>
