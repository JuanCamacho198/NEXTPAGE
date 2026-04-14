<script lang="ts">
  import { getCurrentWindow } from "@tauri-apps/api/window";
  import type { UnlistenFn } from "@tauri-apps/api/event";
  import { onMount } from "svelte";

  import type { BookDto, SaveProgressInput } from "./lib/types";
  import { getProgress, listBooks, saveProgress } from "./lib/tauriClient";

  let books = $state<BookDto[]>([]);
  let selectedBook = $state<BookDto | null>(null);
  let cfiLocation = $state("");
  let percentage = $state(0);
  let isLoadingBooks = $state(false);
  let isLoadingProgress = $state(false);
  let isSaving = $state(false);
  let error = $state<string | null>(null);
  let status = $state("Ready");
  let hasUnsavedChanges = $state(false);

  const roundedPercentage = $derived(Math.round(percentage * 100) / 100);
  let lastPersistedSnapshot = "";
  let pendingPersist: Promise<boolean> | null = null;
  let skipChangeTracking = false;

  const DRAFT_STORAGE_PREFIX = "nextpage:draft-progress:";

  type LocalDraft = SaveProgressInput & {
    updatedAt: string;
  };

  const clampPercentage = (value: number) => {
    if (!Number.isFinite(value)) {
      return 0;
    }

    return Math.max(0, Math.min(100, value));
  };

  const toSnapshot = (payload: SaveProgressInput) =>
    `${payload.bookId}|${payload.cfiLocation}|${payload.percentage.toFixed(3)}`;

  const draftKey = (bookId: string) => `${DRAFT_STORAGE_PREFIX}${bookId}`;

  const readDraft = (bookId: string): LocalDraft | null => {
    try {
      const raw = localStorage.getItem(draftKey(bookId));
      if (!raw) {
        return null;
      }

      const parsed = JSON.parse(raw) as LocalDraft;
      if (!parsed.bookId || typeof parsed.cfiLocation !== "string") {
        return null;
      }

      return {
        ...parsed,
        percentage: clampPercentage(parsed.percentage)
      };
    } catch {
      return null;
    }
  };

  const writeDraft = (payload: SaveProgressInput) => {
    try {
      const draft: LocalDraft = {
        ...payload,
        percentage: clampPercentage(payload.percentage),
        updatedAt: new Date().toISOString()
      };

      localStorage.setItem(draftKey(payload.bookId), JSON.stringify(draft));
    } catch {
      // Ignore localStorage errors to avoid blocking UI flow.
    }
  };

  const clearDraft = (bookId: string) => {
    try {
      localStorage.removeItem(draftKey(bookId));
    } catch {
      // Ignore localStorage errors to avoid blocking UI flow.
    }
  };

  const applyProgressState = (nextCfiLocation: string, nextPercentage: number) => {
    skipChangeTracking = true;
    cfiLocation = nextCfiLocation;
    percentage = clampPercentage(nextPercentage);
    hasUnsavedChanges = false;
    skipChangeTracking = false;
  };

  const markPersisted = (payload: SaveProgressInput) => {
    lastPersistedSnapshot = toSnapshot(payload);
    hasUnsavedChanges = false;
  };

  const buildPayload = (): SaveProgressInput | null => {
    if (!selectedBook) {
      return null;
    }

    return {
      bookId: selectedBook.id,
      cfiLocation: cfiLocation.trim(),
      percentage: clampPercentage(percentage)
    };
  };

  const loadBooks = async () => {
    isLoadingBooks = true;
    error = null;

    try {
      const result = await listBooks();
      books = result;
      if (result.length > 0) {
        selectedBook = result[0];
      } else {
        selectedBook = null;
        cfiLocation = "";
        percentage = 0;
        status = "No books in local library.";
      }
    } catch (loadError) {
      error = loadError instanceof Error ? loadError.message : "Failed to load books.";
      status = "Failed to load library.";
    } finally {
      isLoadingBooks = false;
    }
  };

  const selectBook = (book: BookDto) => {
    selectedBook = book;
  };

  const loadProgress = async (bookId: string) => {
    isLoadingProgress = true;
    error = null;

    try {
      const progress = await getProgress(bookId);

      if (progress) {
        applyProgressState(progress.cfiLocation, progress.percentage);
        status = `Resumed ${Math.round(progress.percentage)}% for current book.`;
      } else {
        applyProgressState("", 0);
        status = "No saved progress. Start reading from beginning.";
      }

      const payload = buildPayload();
      if (payload) {
        markPersisted(payload);
      }

      const draft = readDraft(bookId);
      if (draft) {
        applyProgressState(draft.cfiLocation, draft.percentage);
        hasUnsavedChanges = true;
        status = "Recovered unsynced local draft. Save to sync with local DB.";
      }
    } catch (progressError) {
      error = progressError instanceof Error ? progressError.message : "Failed to load progress.";
      status = "Unable to fetch reading progress.";
    } finally {
      isLoadingProgress = false;
    }
  };

  const persistProgress = async (
    reason: "manual" | "visibility" | "beforeunload" | "close-requested",
    force = false
  ): Promise<boolean> => {
    const payload = buildPayload();
    if (!payload) {
      return false;
    }

    const snapshot = toSnapshot(payload);
    if (!force && snapshot === lastPersistedSnapshot) {
      return true;
    }

    if (pendingPersist) {
      return pendingPersist;
    }

    if (reason === "manual") {
      isSaving = true;
    }
    error = null;

    pendingPersist = (async () => {
      try {
        await saveProgress(payload);
        clearDraft(payload.bookId);
        markPersisted(payload);

        if (reason === "manual") {
          status = `Progress saved at ${Math.round(payload.percentage)}%.`;
        }

        return true;
      } catch (saveError) {
        writeDraft(payload);
        hasUnsavedChanges = false;
        error = saveError instanceof Error ? saveError.message : "Failed to save progress.";
        status = "Unable to persist to DB. Draft saved locally for recovery.";
        return false;
      } finally {
        if (reason === "manual") {
          isSaving = false;
        }
        pendingPersist = null;
      }
    })();

    return pendingPersist;
  };

  $effect(() => {
    const currentBook = selectedBook;
    if (!currentBook) {
      return;
    }

    void loadProgress(currentBook.id);
  });

  $effect(() => {
    const payload = buildPayload();
    if (!payload || skipChangeTracking) {
      return;
    }

    hasUnsavedChanges = toSnapshot(payload) !== lastPersistedSnapshot;
  });

  onMount(() => {
    let isCloseFlowFinalized = false;
    let unlistenCloseRequested: UnlistenFn | null = null;

    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!hasUnsavedChanges) {
        return;
      }

      void persistProgress("beforeunload", true);
      event.preventDefault();
      event.returnValue = "";
    };

    const onVisibilityChange = () => {
      if (document.visibilityState !== "hidden") {
        return;
      }

      void persistProgress("visibility", false);
    };

    window.addEventListener("beforeunload", onBeforeUnload);
    document.addEventListener("visibilitychange", onVisibilityChange);

    void (async () => {
      try {
        const currentWindow = getCurrentWindow();

        unlistenCloseRequested = await currentWindow.onCloseRequested(async (event) => {
          if (isCloseFlowFinalized || !hasUnsavedChanges) {
            return;
          }

          event.preventDefault();
          await persistProgress("close-requested", true);

          isCloseFlowFinalized = true;
          await currentWindow.close();
        });
      } catch {
        // Browser fallback: close hook unavailable outside Tauri runtime.
      }
    })();

    void loadBooks();

    return () => {
      window.removeEventListener("beforeunload", onBeforeUnload);
      document.removeEventListener("visibilitychange", onVisibilityChange);
      if (unlistenCloseRequested) {
        void unlistenCloseRequested();
      }
    };
  });
</script>

<main class="app">
  <section class="card header-card">
    <h1>NextPage Desktop (M1)</h1>
    <p>Thin IPC boundary: listBooks, getProgress, saveProgress.</p>
    <p class="status">{status}</p>
    {#if error}
      <p class="error">{error}</p>
    {/if}
  </section>

  <section class="workspace">
    <aside class="card library-card">
      <h2>Library</h2>
      {#if isLoadingBooks}
        <p>Loading books...</p>
      {:else if books.length === 0}
        <p>No books available.</p>
      {:else}
        <ul class="book-list">
          {#each books as book}
            <li>
              <button
                class:selected={selectedBook?.id === book.id}
                onclick={() => selectBook(book)}
                type="button"
              >
                <span>{book.title}</span>
                <small>{book.author}</small>
              </button>
            </li>
          {/each}
        </ul>
      {/if}
    </aside>

    <section class="card reader-card">
      <h2>Reader State</h2>
      {#if !selectedBook}
        <p>Select a book to continue.</p>
      {:else}
        <p class="meta">Book: {selectedBook.title}</p>
        <p class="meta">Format: {selectedBook.format}</p>

        {#if isLoadingProgress}
          <p>Loading saved progress...</p>
        {:else}
          <label for="cfi">CFI location</label>
          <input
            id="cfi"
            bind:value={cfiLocation}
            placeholder="epubcfi(/6/2!... )"
            type="text"
          />

          <label for="percentage">Progress ({roundedPercentage}%)</label>
          <input id="percentage" bind:value={percentage} max="100" min="0" step="0.1" type="range" />

          <button disabled={isSaving} onclick={() => void persistProgress("manual", true)} type="button">
            {isSaving ? "Saving..." : "Save Progress"}
          </button>
        {/if}
      {/if}
    </section>
  </section>
</main>
