<script lang="ts">
  import { onMount } from 'svelte';
  import type { Book, Rendition } from 'epubjs';
  import ePub from 'epubjs';
  import type { MessageKey } from '$lib/i18n';
  import { getFileBytes } from '$lib/api/tauriClient';
  import type { ReaderSettings, ReaderThemeMode } from '$lib/shared/types';
  import { resolveReaderArrowIntent } from '$lib/features/reader/epub/keyboardNav';
  import {
    getCachedEpub,
    setCachedEpub,
    getCachedEpubToc,
    setCachedEpubToc,
    clearEpubCache,
  } from '$lib/features/reader/epub/epubCache';
  import { clamp, FONT_SIZE_MIN, FONT_SIZE_MAX, applyDisplaySettings } from '$lib/features/reader/epub/epubTheme';
  import { scrollByVerticalStep } from '$lib/features/reader/epub/epubScroll';
  import EpubControls from './epub/EpubControls.svelte';
  import EpubTocSidebar from './epub/EpubTocSidebar.svelte';

  import type { TocEntry } from './ReaderTocPanel.svelte';
  import { debugState } from '$lib/debug/debugState.svelte';

  type Props = {
    filePath: string;
    initialLocation?: string;
    initialPercentage?: number;
    onLocationChange?: (cfiLocation: string, percentage: number) => void;
    searchTargetLocator?: string | null;
    onLocationContext?: (context: { locator: string; percentage: number }) => void;
    readerSettings?: ReaderSettings;
    preloadedBytes?: number[] | null;
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
    themeMode: 'paper',
    brightness: 100,
    contrast: 100,
    epub: {
      fontSize: 100,
      fontFamily: 'serif',
    },
    selectionColor: '#33bbff',
  };

  let {
    filePath,
    initialLocation = '',
    initialPercentage = 0,
    onLocationChange,
    searchTargetLocator = null,
    onLocationContext,
    readerSettings = DEFAULT_READER_SETTINGS,
    preloadedBytes = null,
    onselection,
    onTocReady,
    externalTocNavigate = null,
    t,
  }: Props = $props();

  // --- state ---
  let epubContainer: HTMLDivElement | undefined = $state();
  let book: Book | null = $state(null);
  let rendition: Rendition | null = $state(null);
  let isLoading = $state(true);
  let error = $state<string | null>(null);

  let currentLocation = $state('');
  let currentPercentage = $state(0);
  let lastJumpTarget = '';

  let displaySettings = $state({
    fontSize: 100,
    fontFamily: 'serif',
    margin: 20,
    theme: 'paper' as ReaderThemeMode,
  });

  let toc = $state<Array<{ id: string; label: string; href: string }>>([]);
  let showToc = $state(false);
  let isViewerFocused = $state(false);
  let tocDeferred = $state(false);

  const VERTICAL_SCROLL_STEP_PX = 120;

  // --- lifecycle ---
  onMount(() => {
    return () => {
      clearEpubCache();
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

  // --- TOC ---
  $effect(() => {
    if (!showToc || tocDeferred || !book) return;
    tocDeferred = true;
    loadEpubToc();
  });

  // Emit TOC entries to parent when ready
  $effect(() => {
    if (toc.length > 0) {
      onTocReady?.(
        toc.map((item) => ({
          id: item.id,
          title: item.label,
          depth: 0,
        })),
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

  async function loadEpubToc() {
    if (!book || !filePath) return;

    try {
      const navigation = await (book as Book).loaded.navigation;
      const tocItems = navigation.toc.map((item: { id: string; label: string; href: string }) => ({
        id: item.id,
        label: item.label,
        href: item.href,
      }));
      toc = tocItems;
      setCachedEpubToc(filePath, tocItems);
    } catch {
      tocDeferred = false;
    }
  }

  // --- selection ---
  $effect(() => {
    const container = epubContainer;
    if (!container) return;

    const tryAttachSelectionListener = () => {
      const iframe = container.querySelector('iframe');
      if (!iframe) {
        setTimeout(tryAttachSelectionListener, 500);
        return;
      }

      try {
        const doc = iframe.contentDocument || iframe.contentWindow?.document;
        if (!doc) {
          setTimeout(tryAttachSelectionListener, 500);
          return;
        }

        doc.addEventListener('mouseup', () => {
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
            placement: rect.top > 120 ? 'above' : 'below',
          });

          if (debugState.enabled) {
            const rectCount = range.getClientRects().length;
            if (text) {
              const r = range.getClientRects()[0];
              debugState.selection = {
                text,
                source: 'epub',
                rectCount,
                firstRect: { top: r.top, left: r.left, width: r.width, height: r.height },
              };
            } else {
              debugState.selection = null;
            }
          }
        });

        const clearOutside = (e: MouseEvent) => {
          if (debugState.enabled && e.target !== iframe && !iframe.contains(e.target as Node)) {
            debugState.selection = null;
          }
        };
        doc.addEventListener('mouseup', clearOutside);
      } catch {
        console.warn('Cannot access EPUB iframe selection (cross-origin or sandbox)');
      }
    };

    tryAttachSelectionListener();
  });

  // --- init ---
  async function initEpub() {
    if (!filePath) return;

    isLoading = true;
    error = null;
    toc = [];
    tocDeferred = false;

    try {
      if (book) {
        book.destroy();
        book = null;
      }

      const cached = getCachedEpub(filePath);
      let epubData: ArrayBuffer;

      if (cached) {
        epubData = cached.data;
      } else if (preloadedBytes && preloadedBytes.length > 0) {
        epubData = new Uint8Array(preloadedBytes).buffer;
        setCachedEpub(filePath, { data: epubData, toc: [], tocLoaded: false });
      } else {
        const bytes = await getFileBytes(filePath);
        epubData = new Uint8Array(bytes).buffer;
        setCachedEpub(filePath, { data: epubData, toc: [], tocLoaded: false });
      }

      book = ePub(epubData) as unknown as Book;
      const metadata = await (book as Book).loaded.metadata;
      console.log('Loaded book:', metadata.title);

      const cachedToc = getCachedEpubToc(filePath);
      if (cachedToc) {
        toc = cachedToc;
        tocDeferred = true;
      }

      await renderBook();
    } catch (err) {
      error = err instanceof Error ? err.message : t('epub.error');
    } finally {
      isLoading = false;
    }
  }

  async function renderBook() {
    if (!book || !epubContainer) return;

    rendition = book.renderTo(epubContainer, {
      width: '100%',
      height: '100%',
      spread: 'none',
      flow: 'paginated',
    });

    await rendition.display();

    applyDisplaySettings(rendition as any, displaySettings);

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
        'font-size': `${displaySettings.fontSize}%`,
        'font-family': displaySettings.fontFamily,
      },
    });

    rendition.on('locationChanged', (loc: { start: { cfi: string; percentage: number } }) => {
      currentLocation = loc.start.cfi;
      currentPercentage = loc.start.percentage * 100;
      onLocationChange?.(currentLocation, currentPercentage);
      onLocationContext?.({
        locator: currentLocation,
        percentage: currentPercentage,
      });

      if (debugState.enabled) {
        debugState.readerInfo = {
          format: 'epub',
          isTocOpen: showToc,
          isSearchOpen: false,
          isFullscreen: !!document.fullscreenElement,
          pageInfo: `${Math.round(currentPercentage)}%`,
          scale: displaySettings.fontSize,
        };
      }
    });

    rendition.on('relocated', (loc: { start: { cfi: string } }) => {
      currentLocation = loc.start.cfi;
    });
  }

  // --- settings sync ---
  $effect(() => {
    readerSettings;
    displaySettings.fontSize = clamp(readerSettings.epub.fontSize, FONT_SIZE_MIN, FONT_SIZE_MAX);
    displaySettings.fontFamily =
      readerSettings.epub.fontFamily?.trim().length > 0 ? readerSettings.epub.fontFamily : 'serif';
    displaySettings.theme = readerSettings.themeMode;
    if (rendition) {
      applyDisplaySettings(rendition as any, displaySettings);
    }
  });

  const visualFilterStyle = $derived(
    `brightness(${clamp(readerSettings.brightness, 50, 150)}%) contrast(${clamp(readerSettings.contrast, 50, 150)}%)`,
  );

  // --- search jump ---
  $effect(() => {
    const target = searchTargetLocator?.trim();
    if (!target || !rendition || target === lastJumpTarget) {
      return;
    }

    lastJumpTarget = target;
    void rendition.display(target);
  });

  // --- navigation ---
  const goToPrev = () => {
    if (!rendition) return;
    rendition.prev();
  };

  const goToNext = () => {
    if (!rendition) return;
    rendition.next();
  };

  const goToChapter = (chapter: { id: string; href: string }) => {
    if (!rendition) return;
    rendition.display(chapter.href);
    showToc = false;
  };

  const handleFontSizeChange = (size: number) => {
    displaySettings.fontSize = Math.max(FONT_SIZE_MIN, Math.min(FONT_SIZE_MAX, size));
    if (rendition) {
      applyDisplaySettings(rendition as any, displaySettings);
    }
  };

  const toggleToc = () => {
    showToc = !showToc;
  };

  // --- keyboard ---
  function handleViewerKeydown(event: KeyboardEvent) {
    if (!isViewerFocused) {
      return;
    }

    if ((event.ctrlKey || event.metaKey) && (event.key === '=' || event.key === '+' || event.key === '-')) {
      event.preventDefault();
      const step = event.key === '-' ? -10 : 10;
      handleFontSizeChange(displaySettings.fontSize + step);
      return;
    }

    const intent = resolveReaderArrowIntent(event);
    if (!intent) {
      return;
    }

    if (intent === 'prevPage') {
      event.preventDefault();
      goToPrev();
      return;
    }

    if (intent === 'nextPage') {
      event.preventDefault();
      goToNext();
      return;
    }

    if (intent === 'scrollUp') {
      event.preventDefault();
      if (epubContainer) {
        scrollByVerticalStep(epubContainer, -VERTICAL_SCROLL_STEP_PX);
      }
      return;
    }

    if (intent === 'scrollDown') {
      event.preventDefault();
      if (epubContainer) {
        scrollByVerticalStep(epubContainer, VERTICAL_SCROLL_STEP_PX);
      }
      return;
    }
  }
</script>

<svelte:window onkeydown={handleViewerKeydown} />

<div class="flex flex-col h-full bg-[var(--color-background)] text-[var(--color-primary)] outline-none" onfocusin={() => { isViewerFocused = true; }} onfocusout={() => { isViewerFocused = false; }}>
  {#if isLoading}
    <div class="flex items-center justify-center h-[200px] text-sm">{t('epub.loading')}</div>
  {:else if error}
    <div class="flex items-center justify-center h-[200px] text-sm text-red-600">{t('epub.error')}: {error}</div>
  {:else}
    <EpubControls
      {t}
      currentPercentage={currentPercentage}
      fontSize={displaySettings.fontSize}
      {showToc}
      onPrev={goToPrev}
      onNext={goToNext}
      onFontSizeChange={handleFontSizeChange}
      onToggleToc={toggleToc}
    />

    <div class="flex flex-1 overflow-hidden">
      {#if showToc}
        <EpubTocSidebar {toc} {t} onNavigate={(chapter) => goToChapter(chapter)} />
      {/if}

      <div bind:this={epubContainer} class="flex-1 overflow-hidden epub-container" style:filter={visualFilterStyle}></div>
    </div>
  {/if}
</div>

<style>
  .epub-container :global(iframe) {
    width: 100%;
    height: 100%;
    border: none;
  }
</style>
