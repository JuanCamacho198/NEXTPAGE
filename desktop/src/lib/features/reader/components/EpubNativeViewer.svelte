<script lang="ts">
  import { onMount } from 'svelte';
  import { invoke } from '@tauri-apps/api/core';
  import { convertFileSrc } from '@tauri-apps/api/core';
  import type { MessageKey } from '$lib/i18n';
  import type { ReaderSettings, ReaderThemeMode, ReaderTextAlign, ReaderDirection } from '$lib/shared/types';

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
      lineHeight: 1.8,
      letterSpacing: 0,
      paragraphSpacing: 1,
      textAlign: 'left' as ReaderTextAlign,
      direction: 'ltr' as ReaderDirection,
      hyphenation: false,
      verticalScrolling: false,
      margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 },
      showHeader: true,
      showFooter: true,
      showPageNumbers: true,
      progressIndicator: 'percentage',
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

  // ─── Reader settings cache (synced from prop for reactivity) ─
  let fontSize = $state(100);
  let fontFamily = $state('serif');
  let themeMode = $state<ReaderThemeMode>('paper');
  let lineHeight = $state(1.8);
  let letterSpacing = $state(0);
  let paragraphSpacing = $state(1);
  let textAlign = $state<ReaderTextAlign>('left');
  let direction = $state<ReaderDirection>('ltr');
  let hyphenation = $state(false);
  let verticalScrolling = $state(false);
  let margins = $state<ReaderSettings['margins']>({ top: 1.5, bottom: 1.5, left: 2, right: 2 });

  $effect(() => {
    fontSize = readerSettings.epub.fontSize ?? 100;
    fontFamily = readerSettings.epub.fontFamily?.trim() || 'serif';
    themeMode = readerSettings.themeMode;
    lineHeight = readerSettings.lineHeight;
    letterSpacing = readerSettings.letterSpacing;
    paragraphSpacing = readerSettings.paragraphSpacing;
    textAlign = readerSettings.textAlign;
    direction = readerSettings.direction;
    hyphenation = readerSettings.hyphenation;
    verticalScrolling = readerSettings.verticalScrolling;
    margins = readerSettings.margins;
  });

  // ─── Lifecycle ───────────────────────────────────────────
  function handleSelectionMessage(event: MessageEvent) {
    if (event.data?.type !== 'epub-selection') return;

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

  // ─── Re-render chapter when any layout setting changes ──
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

      if (initialPercentage > 0 && initialPercentage < 100) {
        const chapterGuess = Math.floor((initialPercentage / 100) * totalChapters);
        currentChapterIndex = Math.min(chapterGuess, totalChapters - 1);
      }

      if (onTocReady) {
        const entries = meta.chapters.map((ch) => ({
          id: ch.id,
          title: ch.label,
          depth: ch.depth ?? 0,
        }));
        onTocReady(entries);
      }

      await renderChapter(currentChapterIndex);

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

      const resourcesUrl = convertFileSrc(metadata.resourcesPath) + '/';
      const baseUrl = chapterData.chapterBasePath
        ? resourcesUrl + chapterData.chapterBasePath + '/'
        : resourcesUrl;

      const themeCss = getThemeStyles();
      const chapterHeadCss = extractChapterHead(chapterData.html);

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
<\\\\/script>`;

      // Clean the chapter body HTML: remove the original <head> content since we inject our own
      const bodyHtml = stripHeadFromHtml(chapterData.html);

      // Build layout CSS with all dynamic settings
      const hyphensValue = hyphenation ? 'auto' : 'none';

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
      line-height: ${lineHeight};
      letter-spacing: ${letterSpacing}px;
      text-align: ${textAlign};
      direction: ${direction};
      hyphens: ${hyphensValue};
      padding: ${margins.top}rem ${margins.right}rem ${margins.bottom}rem ${margins.left}rem;
      max-width: 38rem;
      margin: 0 auto;
    }
    p { margin-bottom: ${paragraphSpacing}em; }
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

      const pct = ((index + 0.5) / totalChapters) * 100;
      onLocationChange?.(`chapter:${index}`, pct);
      onLocationContext?.({ locator: `chapter:${index}`, percentage: pct });
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
    }
  }

  // ─── Strip <head> from chapter HTML (keep only <body> content) ──
  function stripHeadFromHtml(html: string): string {
    const bodyMatch = html.match(/<body[^>]*>([\s\S]*)<\/body>/i);
    if (bodyMatch && bodyMatch[1]) {
      return bodyMatch[1];
    }
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

  // ─── Theme ────────────────────────────────────────────────
  function getThemeStyles(): string {
    const themes: Record<string, string> = {
      paper: `
        body { background: #faf8f5; color: #333; }
        a { color: #3366cc; }
      `,
      sepia: `
        body { background: #f5eedd; color: #5b4636; }
        a { color: #8b6914; }
      `,
      night: `
        body { background: #0f1320; color: #c8ccd8; }
        a { color: #7bb8ff; }
      `,
      dark: `
        body { background: #1a1a2e; color: #e0e0e0; }
        a { color: #66bbff; }
      `,
      blue: `
        body { background: #1e3a5f; color: #d6e4f0; }
        a { color: #88ccff; }
      `,
    };

    return themes[themeMode] || themes.paper;
  }

  function getThemeBgColor(): string {
    const bgs: Record<string, string> = {
      paper: '#faf8f5',
      sepia: '#f5eedd',
      night: '#0f1320',
      dark: '#1a1a2e',
      blue: '#1e3a5f',
    };
    return bgs[themeMode] || bgs.paper;
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === 'ArrowLeft') goToPrev();
    if (e.key === 'ArrowRight') goToNext();
    if ((e.ctrlKey || e.metaKey) && (e.key === '=' || e.key === '+' || e.key === '-')) {
      e.preventDefault();
      const step = e.key === '-' ? -10 : 10;
      changeZoom(step);
    }
  }
</script>

<svelte:window onkeydown={handleKeydown} />

<div class="flex flex-col h-full w-full outline-none relative" tabindex="-1" role="presentation"
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
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div
      class="flex-1 w-full h-full"
      class:overflow-hidden={!verticalScrolling}
      class:overflow-y-auto={verticalScrolling}
      style="background: {getThemeBgColor()};"
      bind:this={zoomContainerEl}
      onwheel={handleWheel}
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

    <!-- Minimal overlay navigation (bottom) — hidden in vertical scroll mode -->
    {#if !verticalScrolling}
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
  {/if}
</div>
