<script lang="ts">
  import { onMount } from 'svelte';
  import { invoke } from '@tauri-apps/api/core';
  import { convertFileSrc } from '@tauri-apps/api/core';
  import type { MessageKey } from '$lib/i18n';
  import type { ReaderSettings, ReaderThemeMode } from '$lib/shared/types';

  // ─── Types ───────────────────────────────────────────────
  interface EpubChapterMeta {
    index: number;
    id: string;
    label: string;
    href: string;
    depth?: number;
  }

  interface EpubMetadataExtract {
    title: string;
    author: string;
    language: string | null;
    publisher: string | null;
    chapters: EpubChapterMeta[];
    totalChapters: number;
    resourcesPath: string;
  }

  interface EpubChapterContent {
    index: number;
    html: string;
    mime: string;
    chapterBasePath: string;
  }

  type Props = {
    filePath: string;
    bookId: string;
    initialLocation?: string;
    initialPercentage?: number;
    onLocationChange?: (cfiLocation: string, percentage: number) => void;
    onLocationContext?: (ctx: { locator: string; percentage: number }) => void;
    readerSettings?: ReaderSettings;
    onselection?: (event: {
      text: string;
      bounds: { left: number; top: number; right: number; bottom: number };
      container: { left: number; top: number; width: number; height: number };
      placement: string;
      rects: Array<{ left: number; top: number; width: number; height: number }>;
      pageNumber: number;
    }) => void;
    onselectionclear?: () => void;
    isFullscreen?: boolean;
    onToggleFullscreen?: () => void;
    onTocReady?: (entries: Array<{ id: string; title: string; depth: number }>) => void;
    externalTocNavigate?: { id: string } | null;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  // ─── Props ───────────────────────────────────────────────
  let {
    filePath,
    bookId,
    initialPercentage = 0,
    onLocationChange,
    onLocationContext,
    readerSettings = {
      themeMode: 'paper' as ReaderThemeMode,
      brightness: 100,
      contrast: 100,
      epub: { fontSize: 100, fontFamily: 'serif' },
      selectionColor: '#33bbff',
    },
    onselection,
    onselectionclear,
    isFullscreen = false,
    onToggleFullscreen,
    onTocReady,
    externalTocNavigate = null,
    t,
  }: Props = $props();

  // ─── State ───────────────────────────────────────────────
  let metadata = $state<EpubMetadataExtract | null>(null);
  let currentChapterIndex = $state(0);
  let isLoading = $state(true);
  let error = $state<string | null>(null);
  let iframeEl = $state<HTMLIFrameElement | null>(null);
  let totalChapters = $state(0);
  let zoomLevel = $state(100);
  let zoomContainerEl = $state<HTMLDivElement | null>(null);

  // Reader settings
  let fontSize = $state(100);
  let fontFamily = $state('serif');
  let themeMode = $state<ReaderThemeMode>('paper');

  // ─── Lifecycle ───────────────────────────────────────────
  // ─── Iframe selection message handler ─────────────────────
  function handleSelectionMessage(event: MessageEvent) {
    if (event.data?.type !== 'epub-selection') return;

    // Empty text from iframe means selection was cleared → notify parent
    if (!event.data.text) {
      onselectionclear?.();
      return;
    }

    if (!onselection) return;

    onselection({
      text: event.data.text,
      bounds: event.data.bounds,
      container: event.data.container,
      placement: 'epub-chapter',
      rects: event.data.rects ?? [],
      pageNumber: currentChapterIndex,
    });
  }

  onMount(() => {
    initReader();
    window.addEventListener('message', handleSelectionMessage);
    return () => {
      window.removeEventListener('message', handleSelectionMessage);
    };
  });

  // ─── Sync reader settings ────────────────────────────────
  $effect(() => {
    fontSize = readerSettings.epub.fontSize ?? 100;
    fontFamily = readerSettings.epub.fontFamily?.trim() || 'serif';
    themeMode = readerSettings.themeMode;
  });

  // ─── Re-render chapter when settings change ──
  $effect(() => {
    if (metadata && !isLoading) {
      renderChapter(currentChapterIndex);
    }
  });

  // ─── External TOC navigation ─────────────────────────────
  $effect(() => {
    if (externalTocNavigate && externalTocNavigate.id && metadata) {
      const chapterIdx = metadata.chapters.findIndex((c) => c.id === externalTocNavigate!.id);
      if (chapterIdx >= 0) {
        goToChapter(chapterIdx);
      }
    }
  });

  // ─── Init ────────────────────────────────────────────────
  async function initReader() {
    isLoading = true;
    error = null;

    try {
      const meta = await invoke<EpubMetadataExtract>('parse_epub', {
        filePath,
        bookId,
      });

      metadata = meta;
      totalChapters = meta.totalChapters;

      // Calculate initial chapter from percentage if available
      if (initialPercentage > 0 && initialPercentage < 100) {
        const chapterGuess = Math.floor((initialPercentage / 100) * totalChapters);
        currentChapterIndex = Math.min(chapterGuess, totalChapters - 1);
      }

      // Emit TOC entries to parent with depth from backend
      if (onTocReady) {
        const entries = meta.chapters.map((ch) => ({
          id: ch.id,
          title: ch.label,
          depth: ch.depth ?? 0,
        }));
        onTocReady(entries);
      }

      await renderChapter(currentChapterIndex);

      // Index EPUB text for FTS5 search (fire-and-forget, don't block UX)
      invoke('indexEpubText', { bookId }).catch((err: unknown) => {
        console.warn('Failed to index EPUB text:', err);
      });
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
    } finally {
      isLoading = false;
    }
  }

  // ─── Parse chapter head for CSS ──────────────────────────
  function extractChapterHead(chapterHtml: string): string {
    const parser = new DOMParser();
    const doc = parser.parseFromString(chapterHtml, 'text/html');
    const headChildren = Array.from(doc.head.childNodes).filter(
      (n) => n.nodeName === 'LINK' || n.nodeName === 'STYLE'
    );
    return headChildren.map((n) => (n as Element).outerHTML).join('\n');
  }

  // ─── Render Chapter ──────────────────────────────────────
  async function renderChapter(index: number) {
    if (!metadata || !iframeEl) return;

    try {
      const chapterData = await invoke<EpubChapterContent>('get_epub_chapter', {
        bookId,
        chapterIndex: index,
      });

      currentChapterIndex = index;

      // Build the base URL: combine resources root + chapter's relative directory
      // This ensures relative URLs (../images/foo.jpg) resolve correctly
      const resourcesUrl = convertFileSrc(metadata.resourcesPath) + '/';
      const baseUrl = chapterData.chapterBasePath
        ? resourcesUrl + chapterData.chapterBasePath + '/'
        : resourcesUrl;

      // Build theme CSS
      const themeCss = getThemeStyles();

      // Extract chapter head elements (link, style) to preserve EPUB author CSS
      const chapterHeadCss = extractChapterHead(chapterData.html);

      // Build selection detection script injected into iframe
      const selectionScript = `
<script>
(function() {
  var timer = null;
  document.addEventListener('mouseup', function() {
    if (timer) clearTimeout(timer);
    timer = setTimeout(function() {
      var sel = window.getSelection();
      if (!sel || sel.isCollapsed || !sel.toString().trim()) return;
      var text = sel.toString().trim();
      var range = sel.getRangeAt(0);
      var rect = range.getBoundingClientRect();
      var bounds = { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom };
      var container = { left: 0, top: 0, width: window.innerWidth, height: window.innerHeight };
      var rects = [];
      for (var i = 0; i < range.getClientRects().length; i++) {
        var r = range.getClientRects()[i];
        rects.push({ left: r.left, top: r.top, width: r.width, height: r.height });
      }
      window.parent.postMessage({
        type: 'epub-selection',
        text: text,
        bounds: bounds,
        container: container,
        rects: rects,
        pageNumber: ${currentChapterIndex}
      }, '*');
    }, 100);
  });
  document.addEventListener('selectionchange', function() {
    var sel = window.getSelection();
    if (!sel || sel.isCollapsed) {
      if (timer) clearTimeout(timer);
      timer = setTimeout(function() {
        window.parent.postMessage({ type: 'epub-selection', text: '' }, '*');
      }, 200);
    }
  });
})();
<\\/script>`;

      // Clean the chapter body HTML: remove the original <head> content since we inject our own
      const bodyHtml = stripHeadFromHtml(chapterData.html);

      // Create srcdoc with base tag + EPUB author CSS + theme + content + selection script
      const srcdoc = `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <base href="${baseUrl}">
  ${chapterHeadCss}
  <style>
    ${themeCss}
    html { overflow-y: auto; }
    body {
      font-size: ${fontSize}%;
      font-family: ${fontFamily}, serif;
      line-height: 1.8;
      padding: 1.5rem 2rem;
      max-width: 38rem;
      margin: 0 auto;
    }
    img { max-width: 100%; height: auto; }
    a { color: inherit; }
  </style>
</head>
<body>
  ${bodyHtml}
  ${selectionScript}
</body>
</html>`;

      iframeEl.srcdoc = srcdoc;

      // Report progress
      const pct = ((index + 0.5) / totalChapters) * 100;
      onLocationChange?.(`chapter:${index}`, pct);
      onLocationContext?.({ locator: `chapter:${index}`, percentage: pct });
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
    }
  }

  // ─── Strip <head> from chapter HTML (keep only <body> content) ──
  function stripHeadFromHtml(html: string): string {
    // Simple approach: extract everything between <body> and </body>
    const bodyMatch = html.match(/<body[^>]*>([\s\S]*)<\/body>/i);
    if (bodyMatch && bodyMatch[1]) {
      return bodyMatch[1];
    }
    // Fallback: if no body tags, return the whole HTML (the srcdoc already wraps it)
    return html;
  }

  // ─── Navigation ──────────────────────────────────────────
  function goToPrev() {
    if (currentChapterIndex > 0) {
      renderChapter(currentChapterIndex - 1);
    }
  }

  function goToNext() {
    if (currentChapterIndex < totalChapters - 1) {
      renderChapter(currentChapterIndex + 1);
    }
  }

  function goToChapter(index: number) {
    if (index >= 0 && index < totalChapters) {
      renderChapter(index);
    }
  }

  // ─── Zoom ─────────────────────────────────────────────────
  function changeZoom(delta: number) {
    const newZoom = Math.max(50, Math.min(200, zoomLevel + delta));
    if (newZoom !== zoomLevel) {
      zoomLevel = newZoom;
    }
  }

  function resetZoom() {
    zoomLevel = 100;
  }

  function handleWheel(e: WheelEvent) {
    if (!e.ctrlKey && !e.metaKey) return;
    e.preventDefault();
    const delta = e.deltaY > 0 ? -5 : 5;
    changeZoom(delta);
  }

  function handleZoomKeydown(e: KeyboardEvent) {
    if ((e.ctrlKey || e.metaKey) && (e.key === '=' || e.key === '+' || e.key === '-')) {
      e.preventDefault();
      const step = e.key === '-' ? -10 : 10;
      changeZoom(step);
    }
  }

  // ─── Theme ────────────────────────────────────────────────
  function getThemeStyles(): string {
    // Base themes
    const themes: Record<string, string> = {
      paper: `
        body { background: #faf8f5; color: #333; }
        a { color: #3366cc; }
      `,
      sepia: `
        body { background: #f5eedd; color: #5b4636; }
        a { color: #8b6914; }
      `,
      dark: `
        body { background: #1a1a2e; color: #e0e0e0; }
        a { color: #66bbff; }
      `,
    };

    return themes[themeMode] || themes.paper;
  }

  function getThemeBgColor(): string {
    const bgs: Record<string, string> = {
      paper: '#faf8f5',
      sepia: '#f5eedd',
      dark: '#1a1a2e',
    };
    return bgs[themeMode] || bgs.paper;
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'ArrowLeft') goToPrev();
    if (e.key === 'ArrowRight') goToNext();
  }
</script>

<div class="flex flex-col h-full w-full outline-none relative" tabindex="-1" role="presentation" onkeydown={handleKeydown}
  class:w-full={isFullscreen}
  class:h-full={isFullscreen}
>
  {#if isLoading}
    <div class="flex flex-col items-center justify-center h-full gap-4">
      <div class="h-1 w-full max-w-48 bg-(--color-border)/20 overflow-hidden rounded-full">
        <div class="h-full w-1/3 bg-(--color-accent) animate-pulse rounded-full"></div>
      </div>
      <span class="text-sm opacity-60">{t('epub.loading')}</span>
    </div>
  {:else if error}
    <div class="flex items-center justify-center h-full text-sm text-red-600 px-4 text-center">
      {t('epub.error')}: {error}
    </div>
  {:else}
    <!-- Chapter iframe with zoom support -->
    <div
      class="flex-1 w-full h-full overflow-hidden"
      style="background: {getThemeBgColor()};"
      bind:this={zoomContainerEl}
      onwheel={handleWheel}
      onkeydown={handleZoomKeydown}
    >
      <div
        class="w-full h-full origin-top-left transition-transform duration-200 ease-out"
        style="transform: scale({zoomLevel / 100}); width: {10000 / zoomLevel}%; height: {10000 / zoomLevel}%;"
      >
        <iframe
          bind:this={iframeEl}
          class="w-full h-full border-none"
          title="chapter"
        ></iframe>
      </div>
    </div>

    <!-- Minimal overlay navigation (bottom) -->
    <div class="absolute bottom-4 left-0 right-0 flex justify-center pointer-events-none">
      <div class="flex items-center gap-4 bg-black/60 backdrop-blur-md text-white px-4 py-2 rounded-full shadow-lg pointer-events-auto text-xs font-medium">
        <button
          type="button"
          onclick={goToPrev}
          disabled={currentChapterIndex <= 0}
          class="disabled:opacity-30 cursor-pointer hover:text-(--color-accent)"
        >
          ← {t('epub.previous')}
        </button>

        <span class="opacity-80">
          {currentChapterIndex + 1} / {totalChapters}
        </span>

        <button
          type="button"
          onclick={goToNext}
          disabled={currentChapterIndex >= totalChapters - 1}
          class="disabled:opacity-30 cursor-pointer hover:text-(--color-accent)"
        >
          {t('epub.next')} →
        </button>

        <!-- Zoom controls -->
        <span class="w-px h-4 bg-white/20 mx-1"></span>

        <button
          type="button"
          onclick={() => changeZoom(-10)}
          disabled={zoomLevel <= 50}
          class="disabled:opacity-30 cursor-pointer hover:text-(--color-accent) font-bold"
          title={t('pdf.zoomLevel', { level: zoomLevel })}
        >
          −
        </button>

        <button
          type="button"
          onclick={resetZoom}
          class="cursor-pointer hover:text-(--color-accent) min-w-10 text-center"
          title={t('pdf.zoomLevel', { level: zoomLevel })}
        >
          {zoomLevel}%
        </button>

        <button
          type="button"
          onclick={() => changeZoom(10)}
          disabled={zoomLevel >= 200}
          class="disabled:opacity-30 cursor-pointer hover:text-(--color-accent) font-bold"
          title={t('pdf.zoomLevel', { level: zoomLevel })}
        >
          +
        </button>
      </div>
    </div>
  {/if}
</div>
