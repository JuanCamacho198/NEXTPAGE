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
    hideBookFromLibrary,
    getReadingStats,
    listBooks,
    listLibraryBooks,
    saveProgress,
    saveReadingSession,
    searchBookText,
    upsertBookCover,
    updateBookProgress,
  } from "./lib/tauriClient";
  import { i18n, type MessageKey } from "./lib/i18n";
  import { generatePdfFirstPageThumbnail } from "./lib/services/pdfThumbnail";

  import type {
    BookDto,
    CommandErrorDto,
    LibraryBookDto,
    ReadingSessionInput,
    ReadingStatsSummaryDto,
    SaveProgressInput,
    SearchBookTextResponse,
    SearchNavigationTarget,
    UiLocale,
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
  let locale = $state<UiLocale>("es");
  const thumbnailGenerationInFlight = new Set<string>();
  const thumbnailGenerationAttempted = new Set<string>();

  const t = (key: MessageKey, params?: Record<string, string | number>) => i18n.t(locale, key, params);

  const mapCommandError = (error: unknown) => {
    const typed = error as MaybeCommandError;
    if (typed.commandError) {
      return typed.commandError;
    }

    const fallback = error instanceof Error ? error.message : t("errors.commandFailure");
    return {
      code: "INTERNAL_ERROR",
      message: fallback,
      recoverable: false,
    } satisfies CommandErrorDto;
  };

  const isValidSessionProgressEvent = (event: {
    startedAt: string;
    endedAt?: string;
    durationSeconds: number;
    startPercentage?: number;
    endPercentage?: number;
  }) => {
    if (!event.endedAt || event.durationSeconds <= 0) {
      return false;
    }

    const startedAt = Date.parse(event.startedAt);
    const endedAt = Date.parse(event.endedAt);
    if (!Number.isFinite(startedAt) || !Number.isFinite(endedAt) || endedAt <= startedAt) {
      return false;
    }

    const percentages = [event.startPercentage, event.endPercentage].filter(
      (value): value is number => typeof value === "number",
    );

    return percentages.every((value) => value >= 0 && value <= 100);
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

  const hasResolvedCoverPath = (book: Pick<LibraryBookDto, "coverPath">) => {
    return typeof book.coverPath === "string" && book.coverPath.trim().length > 0;
  };

  const shouldGeneratePdfCover = (book: ReaderBook) => {
    if (book.format.toLowerCase() !== "pdf") {
      return false;
    }

    if (hasResolvedCoverPath(book)) {
      return false;
    }

    return book.filePath.trim().length > 0;
  };

  const ensurePdfCover = async (book: ReaderBook) => {
    if (thumbnailGenerationInFlight.has(book.id)) {
      return;
    }

    thumbnailGenerationInFlight.add(book.id);
    try {
      const thumbnailBytes = await generatePdfFirstPageThumbnail(book.filePath);
      await upsertBookCover({
        bookId: book.id,
        data: Array.from(thumbnailBytes),
        mimeType: "image/png",
      });
      await loadLibrary();
    } catch {
      // fallback UI remains stable when thumbnail generation fails
    } finally {
      thumbnailGenerationInFlight.delete(book.id);
    }
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

      const pendingThumbnailBooks = books.filter((book) => {
        if (!shouldGeneratePdfCover(book)) {
          return false;
        }

        if (thumbnailGenerationAttempted.has(book.id)) {
          return false;
        }

        thumbnailGenerationAttempted.add(book.id);
        return true;
      });

      setDomainUnavailable(DOMAIN.LIBRARY, null);

      if (books.length > 0 && !selectedBook) {
        void openBook(books[0]);
      } else if (selectedBook) {
        const refreshed = books.find((item) => item.id === selectedBook?.id) ?? null;
        selectedBook = refreshed;
      }

      for (const pendingBook of pendingThumbnailBooks) {
        void ensurePdfCover(pendingBook);
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
      readerError = error instanceof Error ? error.message : t("import.failed");
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

    if (!isValidSessionProgressEvent(event)) {
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

  const handleHideBook = async (book: ReaderBook) => {
    try {
      await hideBookFromLibrary(book.id);

      if (selectedBook?.id === book.id) {
        selectedBook = null;
      }

      await loadLibrary();
    } catch (error) {
      const details = mapCommandError(error);
      readerError = details.message;
    }
  };

  const handleReaderLocationContext = () => {
    // reserved for index_book_text integration when extraction pipeline is wired
  };

  const handleLocaleChange = (nextLocale: UiLocale) => {
    locale = nextLocale;
  };

  onMount(() => {
    void i18n.initializeLocale().then((nextLocale) => {
      locale = nextLocale;
    });
    void loadLibrary();
    void loadStats(undefined);
  });
</script>

<main class="min-h-screen bg-[var(--color-background)] text-[var(--color-primary)]">
  <div class="mx-auto max-w-7xl p-4 md:p-6">
    <header class="mb-4 flex items-center justify-between rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm">
      <div>
        <h1 class="text-xl font-semibold">{t("app.title")}</h1>
        <p class="text-sm text-[var(--color-text-muted)]">{t("app.subtitle")}</p>
      </div>
      <div class="flex items-center gap-2">
        <Button onclick={handleImportFile} disabled={isImporting} size="sm">
          {isImporting ? t("app.importing") : t("app.importBook")}
        </Button>
        <DropMenu position="bottom-right">
          {#snippet trigger()}
            <button class="rounded-md border border-[color:var(--color-border)] bg-[var(--color-surface)] px-2 py-1 text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]" aria-label={t("app.openMenu")}>
              {t("app.menu")}
            </button>
          {/snippet}
          <button
            class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
            onclick={() => {
              isSettingsOpen = true;
            }}
          >
            {t("app.settings")}
          </button>
        </DropMenu>
      </div>
    </header>

    {#if importProgress}
      <p class="mb-3 text-sm text-[var(--color-secondary)]">{importProgress.message}</p>
    {/if}

    {#if readerError}
      <p class="mb-3 rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-900">{readerError}</p>
    {/if}

    <SettingsPanel bind:isOpen={isSettingsOpen} {t} {locale} onLocaleChange={handleLocaleChange} />

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
          onHide={(book) => {
            const match = books.find((entry) => entry.id === book.id);
            if (match) {
              void handleHideBook(match);
            }
          }}
          {t}
        />

        <ReadingStatsPanel
          stats={stats}
          isLoading={isLoadingStats}
          disabledReason={statsUnavailableReason}
          selectedBookTitle={selectedBook?.title ?? null}
          onRefresh={() => {
            void loadStats(selectedBook?.id);
          }}
          {t}
        />
      </div>

      <section class="rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm">
        {#if !selectedBook}
          <p class="text-sm text-[var(--color-text-muted)]">{t("app.openBookPrompt")}</p>
        {:else}
          <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 class="text-lg font-semibold text-[var(--color-primary)]">{selectedBook.title}</h2>
              <p class="text-sm text-[var(--color-text-muted)]">
                {selectedBook.author || t("app.unknownAuthor")} · {selectedBook.format.toUpperCase()} · {selectedBook.currentPage}/{selectedBook.totalPages || "-"}
              </p>
            </div>
            {#if selectedBook.format.toLowerCase() === "epub"}
              <p class="text-xs text-[var(--color-text-muted)]">{t("app.locationLabel")}: {cfiLocation || t("app.start")} · {Math.round(percentage)}%</p>
            {/if}
          </div>

          {#if selectedBook.format.toLowerCase() === "pdf"}
            <div class="mb-4 h-[520px] overflow-hidden rounded-lg border border-[color:var(--color-border)]">
              <PdfViewer
                filePath={selectedBook.filePath}
                initialPage={Math.max(1, selectedBook.currentPage || 1)}
                searchTargetLocator={searchTargetLocator}
                onPageChange={handlePdfPageChange}
                onSessionProgress={handlePdfSessionProgress}
                {t}
              />
            </div>
          {:else}
            <div class="mb-4 h-[520px] overflow-hidden rounded-lg border border-[color:var(--color-border)]">
              <EpubViewer
                filePath={selectedBook.filePath}
                initialLocation={cfiLocation}
                initialPercentage={percentage}
                searchTargetLocator={searchTargetLocator}
                onLocationContext={handleReaderLocationContext}
                onLocationChange={handleEpubLocationChange}
                {t}
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
            {t}
          />
        {/if}
      </section>
    </div>
  </div>
</main>
