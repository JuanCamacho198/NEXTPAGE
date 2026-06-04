<script lang="ts">
  import { onMount } from 'svelte';
  import { invoke } from '@tauri-apps/api/core';
  import { convertFileSrc } from '@tauri-apps/api/core';
  import type { MessageKey } from '$lib/i18n';
  import type { ReaderSettings, ReaderThemeMode } from '$lib/shared/types';
  import { indexEpubText } from '$lib/api/tauriClient';

  // ─── Types ───────────────────────────────────────────────
  interface EpubChapterMeta {
    index: number;
    id: string;
    label: string;
    href: string;
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
      rects?: Array<{ left: number; top: number; width: number; height: number }>;
      pageNumber?: number;
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

  // Reader settings
  let fontSize = $state(100);
  let fontFamily = $state('serif');
  let themeMode = $state<ReaderThemeMode>('paper');

  // ─── Lifecycle ───────────────────────────────────────────
  // ─── Iframe selection message handler ─────────────────────
  function handleSelectionMessage(event: MessageEvent) {
    if (event.data?.type !== 'epub-selection') return;
    if (!event.data.text) return;
    if (!onselection) return;

    onselection({
      text: event.data.text,
      bounds: event.data.bounds,
      container: event.data.container,
      placement: 'epub-chapter',
      rects: event.data.rects,
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

  // ─── Re-render chapter when settings or chapter changes ──
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

      // Emit TOC entries to parent
      if (onTocReady) {
        const entries = meta.chapters.map((ch) => ({
          id: ch.id,
          title: ch.label,
          depth: 0,
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

  // ─── Render Chapter ──────────────────────────────────────
  async function renderChapter(index: number) {
    if (!metadata || !iframeEl) return;

    try {
      const chapterData = await invoke<EpubChapterContent>('get_epub_chapter', {
        bookId,
        chapterIndex: index,
      });

      currentChapterIndex = index;

      // Build the base URL using Tauri asset protocol
      const baseUrl = convertFileSrc(metadata.resourcesPath) + '/';

      // Build theme CSS
      const themeCss = getThemeStyles();

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
<\/script>`;

      // Create srcdoc with base tag + theme + content + selection script
      const srcdoc = `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <base href="${baseUrl}">
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
  ${chapterData.html}
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
    <!-- Chapter iframe -->
    <div class="flex-1 w-full h-full overflow-hidden" style="background: {getThemeBgColor()};">
      <iframe
        bind:this={iframeEl}
        class="w-full h-full border-none"
        title="chapter"
      ></iframe>
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
      </div>
    </div>
  {/if}
</div>
