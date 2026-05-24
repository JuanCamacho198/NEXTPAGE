<script lang="ts">
  import { onMount } from "svelte";
  import type { Book, Rendition } from "epubjs";
  import ePub from "epubjs";
  import type { MessageKey } from "$lib/i18n";
  import { getFileBytes } from "$lib/shared/api/tauriClient";
  import type { ReaderSettings, ReaderThemeMode } from "$lib/shared/types";
  import { resolveReaderArrowIntent } from "$lib/features/reader/epub/keyboardNav";

  import type { TocEntry } from "./ReaderTocPanel.svelte";
  import { debugState } from "$lib/debug/debugState.svelte";

  type Props = {
    filePath: string;
    initialLocation?: string;
    initialPercentage?: number;
    onLocationChange?: (cfiLocation: string, percentage: number) => void;
    searchTargetLocator?: string | null;
    onLocationContext?: (context: { locator: string; percentage: number }) => void;
    readerSettings?: ReaderSettings;
    onselection?: (event: {
      text: string;
      bounds: { left: number; top: number; right: number; bottom: number };
      container: { left: number; top: number; width: number; height: number };
      placement: string;
    }) => void;
    onTocReady?: (entries: TocEntry[]) => void;
    externalTocNavigate?: TocEntry | null;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  const DEFAULT_READER_SETTINGS: ReaderSettings = {
    themeMode: "paper",
    brightness: 100,
    contrast: 100,
    epub: {
      fontSize: 100,
      fontFamily: "serif",
    },
    selectionColor: "#33bbff",
  };

  let {
    filePath,
    initialLocation = "",
    initialPercentage = 0,
    onLocationChange,
    searchTargetLocator = null,
    onLocationContext,
    readerSettings = DEFAULT_READER_SETTINGS,
    onselection,
    onTocReady,
    externalTocNavigate = null,
    t,
  }: Props = $props();

  // Emit TOC entries to parent when epub TOC is ready
  $effect(() => {
    if (toc.length > 0) {
      onTocReady?.(
        toc.map((item) => ({
          id: item.id,
          title: item.label,
          depth: 0,
        }))
      );
    }
  });

  // Navigate to a TOC entry triggered from an external panel
  $effect(() => {
    if (externalTocNavigate && externalTocNavigate.id) {
      const chapter = toc.find((t) => t.id === externalTocNavigate.id);
      if (chapter) {
        goToChapter(chapter);
      }
    }
  });

  let viewerContainer: HTMLDivElement | undefined = $state();
  let book: Book | null = $state(null);
  let rendition: Rendition | null = $state(null);
  let isLoading = $state(true);
  let error = $state<string | null>(null);

  let currentLocation = $state("");
  let currentPercentage = $state(0);
  let lastJumpTarget = "";
  let displaySettings = $state({
    fontSize: 100,
    fontFamily: "serif",
    margin: 20,
    theme: "paper" as ReaderThemeMode,
  });

  let toc = $state<Array<{ id: string; label: string; href: string }>>([]);
  let showToc = $state(false);
  let isViewerFocused = $state(false);

  let epubContainer: HTMLDivElement | undefined = $state();

  const VERTICAL_SCROLL_STEP_PX = 120;

  const clamp = (value: number, min: number, max: number) => {
    return Math.min(max, Math.max(min, Math.round(value)));
  };

  const resolveThemeStyles = (themeMode: ReaderThemeMode) => {
    if (themeMode === "sepia") {
      return {
        background: "#f1e7d4",
        color: "#3a2f1d",
      };
    }

    if (themeMode === "night") {
      return {
        background: "#10141f",
        color: "#e7ebf1",
      };
    }

    return {
      background: "#faf6eb",
      color: "#2b2116",
    };
  };

  onMount(() => {
    initEpub();
    return () => {
      if (book) {
        book.destroy();
      }
    };
  });

  $effect(() => {
    if (filePath) {
      initEpub();
    }
  });

  // Selection listener: try to attach to the EPUB iframe once it's available
  $effect(() => {
    const container = epubContainer;
    if (!container) return;

    const tryAttachSelectionListener = () => {
      const iframe = container.querySelector("iframe");
      if (!iframe) {
        // Iframe might not be ready yet, retry
        setTimeout(tryAttachSelectionListener, 500);
        return;
      }

      try {
        const doc = iframe.contentDocument || iframe.contentWindow?.document;
        if (!doc) {
          setTimeout(tryAttachSelectionListener, 500);
          return;
        }

        doc.addEventListener("mouseup", () => {
          const selection = doc.getSelection();
          if (!selection || selection.rangeCount === 0 || !selection.toString().trim()) return;

          const text = selection.toString().trim();
          const range = selection.getRangeAt(0);
          const rect = range.getBoundingClientRect();
          const containerRect = container.getBoundingClientRect();

          onselection?.({
            text,
            bounds: {
              left: rect.left - containerRect.left,
              top: rect.top - containerRect.top,
              right: rect.right - containerRect.left,
              bottom: rect.bottom - containerRect.top,
            },
            container: {
              left: containerRect.left,
              top: containerRect.top,
              width: containerRect.width,
              height: containerRect.height,
            },
            placement: rect.top > 120 ? "above" : "below",
          });

          if (debugState.enabled) {
            const rectCount = range.getClientRects().length;
            if (text) {
              const r = range.getClientRects()[0];
              debugState.selection = {
                text,
                source: "epub",
                rectCount,
                firstRect: { top: r.top, left: r.left, width: r.width, height: r.height },
              };
            } else {
              debugState.selection = null;
            }
          }
        });

        // Clean up selection when user clicks outside the iframe
        const clearOutside = (e: MouseEvent) => {
          if (debugState.enabled && e.target !== iframe && !iframe.contains(e.target as Node)) {
            debugState.selection = null;
          }
        };
        doc.addEventListener("mouseup", clearOutside);
      } catch {
        // Cross-origin iframe — selection unavailable
        console.warn("Cannot access EPUB iframe selection (cross-origin or sandbox)");
      }
    };

    tryAttachSelectionListener();
  });

  async function initEpub() {
    if (!filePath) return;

    isLoading = true;
    error = null;

    try {
      if (book) {
        book.destroy();
      }

      const bytes = await getFileBytes(filePath);
      const epubData = new Uint8Array(bytes).buffer;
      book = ePub(epubData) as unknown as Book;

      const metadata = await (book as Book).loaded.metadata;
      console.log("Loaded book:", metadata.title);

      const navigation = await (book as Book).loaded.navigation;
      toc = navigation.toc.map((item: { id: string; label: string; href: string }) => ({
        id: item.id,
        label: item.label,
        href: item.href,
      }));

      renderBook();
    } catch (err) {
      error = err instanceof Error ? err.message : t("epub.error");
    } finally {
      isLoading = false;
    }
  }

  async function renderBook() {
    if (!book || !epubContainer) return;

    rendition = book.renderTo(epubContainer, {
      width: "100%",
      height: "100%",
      spread: "none",
      flow: "paginated",
    });

    await rendition.display();

    applyDisplaySettings();

    if (initialLocation) {
      await (rendition as Rendition).display(initialLocation);
    } else if (initialPercentage > 0 && initialPercentage < 100) {
      currentPercentage = initialPercentage;
      try {
        await (book as Book).locations.generate(1000);
        const cfi = (book as Book).locations.cfiFromPercentage(initialPercentage / 100);
        if (cfi) {
          await (rendition as Rendition).display(cfi);
        }
      } catch {
        // Locations generation failed, start from beginning
      }
    }

    rendition.themes.default({
      body: {
        "font-size": `${displaySettings.fontSize}%`,
        "font-family": displaySettings.fontFamily,
      },
    });

    rendition.on("locationChanged", (loc: { start: { cfi: string; percentage: number } }) => {
      currentLocation = loc.start.cfi;
      currentPercentage = loc.start.percentage * 100;
      onLocationChange?.(currentLocation, currentPercentage);
      onLocationContext?.({
        locator: currentLocation,
        percentage: currentPercentage,
      });

      if (debugState.enabled) {
        debugState.readerInfo = {
          format: "epub",
          isTocOpen: showToc,
          isSearchOpen: false,
          isFullscreen: !!document.fullscreenElement,
          pageInfo: `${Math.round(currentPercentage)}%`,
          scale: displaySettings.fontSize,
        };
      }
    });

    rendition.on("relocated", (loc: { start: { cfi: string } }) => {
      currentLocation = loc.start.cfi;
    });
  }

  const applyDisplaySettings = () => {
    if (!rendition) return;

    (rendition as Rendition).themes.fontSize(`${displaySettings.fontSize}%`);
    (rendition as Rendition).themes.font(displaySettings.fontFamily);

    const themeStyles = resolveThemeStyles(displaySettings.theme as ReaderThemeMode);
    rendition.themes.default({
      body: {
        "font-size": `${displaySettings.fontSize}%`,
        "font-family": displaySettings.fontFamily,
        "background-color": themeStyles.background,
        color: themeStyles.color,
      },
      p: {
        color: themeStyles.color,
      },
      h1: {
        color: themeStyles.color,
      },
      h2: {
        color: themeStyles.color,
      },
      h3: {
        color: themeStyles.color,
      },
      h4: {
        color: themeStyles.color,
      },
      h5: {
        color: themeStyles.color,
      },
      h6: {
        color: themeStyles.color,
      },
      a: {
        color: themeStyles.color,
      },
    });
  };

  $effect(() => {
    readerSettings;
    displaySettings.fontSize = clamp(readerSettings.epub.fontSize, 80, 200);
    displaySettings.fontFamily =
      readerSettings.epub.fontFamily?.trim().length > 0 ? readerSettings.epub.fontFamily : "serif";
    displaySettings.theme = readerSettings.themeMode;
    applyDisplaySettings();
  });

  const visualFilterStyle = $derived(
    `brightness(${clamp(readerSettings.brightness, 50, 150)}%) contrast(${clamp(readerSettings.contrast, 50, 150)}%)`,
  );

  $effect(() => {
    const target = searchTargetLocator?.trim();
    if (!target || !rendition || target === lastJumpTarget) {
      return;
    }

    lastJumpTarget = target;
    void rendition.display(target);
  });

  const goToPrev = () => {
    if (!rendition) return;
    rendition.prev();
  };

  const goToNext = () => {
    if (!rendition) return;
    rendition.next();
  };

  const handleViewerKeydown = (event: KeyboardEvent) => {
    if (!isViewerFocused) {
      return;
    }

    if ((event.ctrlKey || event.metaKey) && (event.key === "=" || event.key === "+" || event.key === "-")) {
      event.preventDefault();
      const step = event.key === "-" ? -10 : 10;
      updateFontSize(displaySettings.fontSize + step);
      return;
    }

    const intent = resolveReaderArrowIntent(event);
    if (!intent) {
      return;
    }

    if (intent === "prevPage") {
      event.preventDefault();
      goToPrev();
      return;
    }

    if (intent === "nextPage") {
      event.preventDefault();
      goToNext();
      return;
    }

    if (intent === "scrollUp") {
      event.preventDefault();
      scrollByVerticalStep(-VERTICAL_SCROLL_STEP_PX);
      return;
    }

    if (intent === "scrollDown") {
      event.preventDefault();
      scrollByVerticalStep(VERTICAL_SCROLL_STEP_PX);
      return;
    }
  };

  const canScrollElementInDirection = (element: HTMLElement, delta: number) => {
    if (element.scrollHeight <= element.clientHeight + 1) {
      return false;
    }

    if (delta < 0) {
      return element.scrollTop > 0;
    }

    return element.scrollTop + element.clientHeight < element.scrollHeight - 1;
  };

  const resolveEpubIframeScrollHost = () => {
    const iframe = epubContainer?.querySelector("iframe");
    if (!(iframe instanceof HTMLIFrameElement)) {
      return null;
    }

    try {
      const frameDocument = iframe.contentDocument;
      if (!frameDocument) {
        return null;
      }

      const scrollingElement = frameDocument.scrollingElement;
      if (scrollingElement instanceof HTMLElement) {
        return scrollingElement;
      }

      if (frameDocument.documentElement instanceof HTMLElement) {
        return frameDocument.documentElement;
      }

      if (frameDocument.body instanceof HTMLElement) {
        return frameDocument.body;
      }
    } catch {
      return null;
    }

    return null;
  };

  const scrollByVerticalStep = (delta: number) => {
    const iframeScrollHost = resolveEpubIframeScrollHost();
    if (iframeScrollHost && canScrollElementInDirection(iframeScrollHost, delta)) {
      iframeScrollHost.scrollBy({ top: delta, behavior: "auto" });
      return;
    }

    const containerScrollHost = epubContainer;
    if (containerScrollHost && canScrollElementInDirection(containerScrollHost, delta)) {
      containerScrollHost.scrollBy({ top: delta, behavior: "auto" });
      return;
    }

    if (typeof window !== "undefined") {
      window.scrollBy({ top: delta, behavior: "auto" });
    }
  };

  const goToChapter = (chapter: { id: string; href: string }) => {
    if (!rendition) return;
    rendition.display(chapter.href);
    showToc = false;
  };

  const updateFontSize = (size: number) => {
    displaySettings.fontSize = Math.max(50, Math.min(200, size));
    applyDisplaySettings();
  };

  const updateMargin = (margin: number) => {
    displaySettings.margin = Math.max(0, Math.min(50, margin));
    applyDisplaySettings();
  };
</script>

<svelte:window onkeydown={handleViewerKeydown} />

<div class="epub-viewer" onfocusin={() => { isViewerFocused = true; }} onfocusout={() => { isViewerFocused = false; }}>
  {#if isLoading}
    <div class="loading">{t("epub.loading")}</div>
  {:else if error}
    <div class="error">{t("epub.error")}: {error}</div>
  {:else}
    <div class="toolbar">
      <button type="button" onclick={() => (showToc = !showToc)} class="toc-btn">
        {showToc ? t("epub.hide") : t("epub.toc")}
      </button>
      <span class="progress">{Math.round(currentPercentage)}%</span>
      <div class="nav-buttons">
        <button type="button" onclick={goToPrev} class="nav-btn" aria-label={t("epub.previous")}><span aria-hidden="true">&#8592;</span> {t("epub.previous")}</button>
        <button type="button" onclick={goToNext} class="nav-btn" aria-label={t("epub.next")}><span aria-hidden="true">&#8594;</span> {t("epub.next")}</button>
      </div>
      <div class="settings-controls">
        <button type="button" onclick={() => updateFontSize(displaySettings.fontSize - 10)} class="size-btn">
          A-
        </button>
        <span class="size-label">{displaySettings.fontSize}%</span>
        <button type="button" onclick={() => updateFontSize(displaySettings.fontSize + 10)} class="size-btn">
          A+
        </button>
      </div>
    </div>

    <div class="content-area">
      {#if showToc}
        <aside class="toc-sidebar">
          <h3>{t("epub.tableOfContents")}</h3>
          <ul class="toc-list">
            {#each toc as chapter}
              <li>
                <button type="button" onclick={() => goToChapter(chapter)} class="toc-item">
                  {chapter.label}
                </button>
              </li>
            {/each}
          </ul>
        </aside>
      {/if}

      <div bind:this={epubContainer} class="epub-container" style:filter={visualFilterStyle}></div>
    </div>
  {/if}
</div>

<style>
  .epub-viewer {
    display: flex;
    flex-direction: column;
    height: 100%;
    background: var(--color-background);
    color: var(--color-primary);
    outline: none;
  }

  .loading,
  .error {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 200px;
    font-size: 14px;
  }

  .error {
    color: #dc2626;
  }

  .toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 12px;
    background: var(--color-surface);
    border-bottom: 1px solid var(--color-border);
    flex-wrap: wrap;
  }

  .toc-btn,
  .nav-btn,
  .size-btn {
    padding: 6px 12px;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    background: var(--color-surface);
    color: var(--color-primary);
    cursor: pointer;
    font-size: 13px;
  }

  .toc-btn:hover,
  .nav-btn:hover,
  .size-btn:hover {
    background: color-mix(in srgb, var(--color-primary) 8%, var(--color-surface));
  }

  .nav-buttons {
    display: flex;
    gap: 8px;
  }

  .progress {
    font-size: 13px;
    color: var(--color-text-muted);
    min-width: 40px;
    text-align: center;
  }

  .settings-controls {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-left: auto;
  }

  .size-label {
    font-size: 12px;
    min-width: 40px;
    text-align: center;
  }

  .content-area {
    display: flex;
    flex: 1;
    overflow: hidden;
  }

  .toc-sidebar {
    width: 240px;
    background: var(--color-surface);
    border-right: 1px solid var(--color-border);
    overflow-y: auto;
    flex-shrink: 0;
  }

  .toc-sidebar h3 {
    padding: 12px;
    font-size: 14px;
    font-weight: 600;
    border-bottom: 1px solid var(--color-border);
    margin: 0;
    color: var(--color-primary);
  }

  .toc-list {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  .toc-item {
    display: block;
    width: 100%;
    padding: 10px 12px;
    border: none;
    background: transparent;
    text-align: left;
    cursor: pointer;
    font-size: 13px;
    line-height: 1.4;
    word-break: break-word;
  }

  .toc-item:hover {
    background: color-mix(in srgb, var(--color-primary) 8%, var(--color-surface));
  }

  .epub-container {
    flex: 1;
    overflow: hidden;
  }

  .epub-container :global(iframe) {
    width: 100%;
    height: 100%;
    border: none;
  }
</style>
