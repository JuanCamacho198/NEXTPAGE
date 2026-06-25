<script lang="ts">
  import { onMount } from 'svelte';
  import { invoke } from '@tauri-apps/api/core';
  import { convertFileSrc } from '@tauri-apps/api/core';
  import type { MessageKey } from '$lib/shared/i18n';
  import type { ReaderSettings, ReaderThemeMode, ReaderTextAlign, ReaderDirection } from '$lib/shared/types';
  import EpubControls from './epub/EpubControls.svelte';
  import { setReaderError, clearReaderError } from "$lib/stores/readerErrorState.svelte";
  import { debugState } from "$lib/shared/debug/debugState.svelte";
  import { IFRAME_CFI_BRIDGE_SCRIPT } from '$lib/features/reader/epub/cfiBridgeIframe';

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
    chapterPath: string;
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
      cfi: string | null;
    }) => void;
    onselectionclear?: () => void;
    isFullscreen?: boolean;
    onToggleFullscreen?: () => void;
    onTocReady?: (entries: Array<{ id: string; title: string; depth: number }>) => void;
    externalTocNavigate?: { id: string } | null;
    showToc?: boolean;
    onToggleToc?: () => void;
    onSettingsChange?: (settings: ReaderSettings) => void;
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
    onTocReady,
    externalTocNavigate = null,
    onToggleFullscreen,
    onToggleToc,
    onSettingsChange,
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
  let iframeContentHeight = $state(0);

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
    margins = readerSettings.margins;
  });

  // ─── Lifecycle ───────────────────────────────────────────
  function handleIframeMessage(event: MessageEvent): void {
    if (!event.data || typeof event.data !== 'object') return;

    // SEL-4: drop any in-flight postMessage from a chapter that is no
    // longer current. The chapter-change $effect can run between the
    // mouseup and the postMessage delivery, so we always sanity-check
    // the pageNumber. (The epub-resize message has no pageNumber and
    // is treated as a global signal -- but the resize handler reads
    // the current iframe state, so it is also safe to drop stale
    // resize events. We keep it for now since resizes are cheap.)
    if (event.data.type === 'epub-resize') {
      syncIframeHeight();
      return;
    }

    if (event.data.type === 'epub-highlight-click') {
      handleEpubHighlightClick(event.data);
      return;
    }

    if (event.data.type !== 'epub-selection') return;

    if (typeof event.data.pageNumber === 'number' && event.data.pageNumber !== currentChapterIndex) {
      // Stale message from a chapter the user has already navigated
      // away from. Drop silently.
      return;
    }

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
      cfi: typeof event.data.cfi === 'string' ? event.data.cfi : null,
    });
  }

  // Placeholder for the Menu 2 click handler, fleshed out in commit 7.
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  function handleEpubHighlightClick(_msg: unknown): void {
    // No-op for now; commit 7 wires this up.
  }

  function syncIframeHeight(): void {
    if (!iframeEl?.contentDocument) return;

    const doc = iframeEl.contentDocument;
    const height = Math.max(doc.documentElement.scrollHeight, doc.body.scrollHeight);
    iframeContentHeight = height > 0 ? height : 0;
  }

  onMount(() => {
    initReader();
    window.addEventListener('message', handleIframeMessage);
    return () => {
      window.removeEventListener('message', handleIframeMessage);
    };
  });

  let lastRenderedChapter = $state(-1);

  // ─── Render chapter or refresh styles when settings change ──
  $effect(() => {
    if (!metadata || isLoading || !iframeEl) return;

    void fontSize;
    void fontFamily;
    void themeMode;
    void lineHeight;
    void letterSpacing;
    void paragraphSpacing;
    void textAlign;
    void direction;
    void hyphenation;
    void margins.top;
    void margins.right;
    void margins.bottom;
    void margins.left;
    void zoomLevel;

    if (lastRenderedChapter !== currentChapterIndex) {
      renderChapter(currentChapterIndex);
      return;
    }

    refreshReaderStyles();
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
  async function initReader(): Promise<void> {
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

      // Index EPUB text for full-text search.
      // The Rust command `index_epub_text` (camelCase: `indexEpubText`) reads the
      // cached chapter files and indexes into FTS5.
      // NOTE: hidden behind debug flag since the command is not yet implemented in Rust.
      if (debugState.enabled) {
        invoke('indexEpubText', { bookId }).catch((err: unknown) => {
          console.warn('Failed to index EPUB text for search', err);
        });
      }
      clearReaderError();
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
      setReaderError(error);
    } finally {
      isLoading = false;
    }
  }

  function resolveResourcePath(chapterPath: string, href: string): string {
    const chapterDir = chapterPath.includes('/')
      ? chapterPath.slice(0, chapterPath.lastIndexOf('/') + 1)
      : '';
    const parts = `${chapterDir}${href}`.split('/');
    const resolved: string[] = [];

    for (const part of parts) {
      if (part === '..') {
        resolved.pop();
      } else if (part !== '.' && part !== '') {
        resolved.push(part);
      }
    }

    return resolved.join('/');
  }

  function toAssetUrl(resourcesPath: string, resourcePath: string): string {
    const normalized = resourcePath.replace(/\\/g, '/');
    const base = resourcesPath.replace(/\\/g, '/').replace(/\/$/, '');
    return `${convertFileSrc(`${base}/${normalized}`)}`;
  }

  function buildReaderOverrideCss(): string {
    const hyphensValue = hyphenation ? 'auto' : 'none';
    const effectiveFontSize = (fontSize * zoomLevel) / 100;
    const themeCss = getThemeStyles();

    return `
      ${themeCss}
      html {
        height: auto !important;
        max-height: none !important;
        overflow-x: hidden !important;
        overflow-y: visible !important;
      }
      body {
        height: auto !important;
        min-height: 100% !important;
        max-height: none !important;
        overflow: visible !important;
        font-size: ${effectiveFontSize}% !important;
        line-height: ${lineHeight} !important;
        letter-spacing: ${letterSpacing}px !important;
        text-align: ${textAlign} !important;
        direction: ${direction} !important;
        hyphens: ${hyphensValue} !important;
        padding: ${margins.top}rem ${margins.right}rem ${margins.bottom}rem ${margins.left}rem !important;
        max-width: 38rem !important;
        margin: 0 auto !important;
        box-sizing: border-box !important;
      }
      body p {
        margin-bottom: ${paragraphSpacing}em;
      }
      body img {
        max-width: 100%;
        height: auto;
      }
    `;
  }

  function buildChapterSrcdoc(chapterData: EpubChapterContent, resourcesPath: string, spineHrefs: string[], currentChapterHref: string): string {
    const parser = new DOMParser();
    const doc = parser.parseFromString(chapterData.html, 'text/html');
    const chapterPath = chapterData.chapterPath.replace(/\\/g, '/');

    const baseUrl = chapterData.chapterBasePath
      ? toAssetUrl(resourcesPath, `${chapterData.chapterBasePath}/`)
      : toAssetUrl(resourcesPath, '');

    let baseEl = doc.querySelector('base');
    if (!baseEl) {
      baseEl = doc.createElement('base');
      doc.head.prepend(baseEl);
    }
    baseEl.setAttribute('href', baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`);

    for (const link of doc.querySelectorAll('link[rel="stylesheet"]')) {
      const href = link.getAttribute('href');
      if (!href || href.startsWith('http') || href.startsWith('data:')) continue;

      const resourcePath = resolveResourcePath(chapterPath, href);
      link.setAttribute('href', toAssetUrl(resourcesPath, resourcePath));
    }

    // Process images, SVG images, and media elements — convert relative/absolute paths to asset URLs
    for (const el of doc.querySelectorAll('img, image, video, audio, source, object')) {
      const attrName = el.tagName.toLowerCase() === 'image' ? 'href' : 'src';
      const src = el.getAttribute(attrName);
      if (!src || src.startsWith('http') || src.startsWith('data:') || src.startsWith('asset:')) continue;

      const resourcePath = resolveResourcePath(chapterPath, src);
      el.setAttribute(attrName, toAssetUrl(resourcesPath, resourcePath));
    }

    for (const styleEl of doc.querySelectorAll('style')) {
      const cssText = styleEl.textContent ?? '';
      if (!cssText.includes('url(')) continue;

      styleEl.textContent = cssText.replace(
        /url\(\s*(['"]?)([^'")]+)\1\s*\)/gi,
        (_match, quote: string, rawUrl: string) => {
          if (
            rawUrl.startsWith('http') ||
            rawUrl.startsWith('data:') ||
            rawUrl.startsWith('asset:') ||
            rawUrl.startsWith('#')
          ) {
            return `url(${quote}${rawUrl}${quote})`;
          }

          const resourcePath = resolveResourcePath(chapterPath, rawUrl);
          return `url(${quote}${toAssetUrl(resourcesPath, resourcePath)}${quote})`;
        },
      );
    }

    let readerStyle = doc.getElementById('nextpage-reader-overrides');
    if (!readerStyle) {
      readerStyle = doc.createElement('style');
      readerStyle.id = 'nextpage-reader-overrides';
      doc.head.appendChild(readerStyle);
    }
    readerStyle.textContent = buildReaderOverrideCss();

    // Inlined CFI bridge (see cfiBridgeIframe.ts). Mounts as
    // `window.__cfiBridge` for the selection script to call. Must run
    // before the selection script and before the spine-init script.
    const bridgeScript = doc.createElement('script');
    bridgeScript.textContent = IFRAME_CFI_BRIDGE_SCRIPT;

    // Spine init: register the ordered chapter hrefs so the bridge can
    // compute the spine index for the current chapter's CFI. The
    // hrefs are JSON-serialised; we trust them because they come from
    // the parent (which got them from the Rust `parse_epub` command,
    // a trusted source).
    const spineScript = doc.createElement('script');
    spineScript.textContent = `
      (function() {
        try {
          window.__cfiBridge.setSpine(${JSON.stringify(spineHrefs)});
        } catch (e) {
          console.warn('epub-cfi: failed to set spine', e);
        }
      })();
    `;

    const resizeScript = doc.createElement('script');
    resizeScript.textContent = `
      (function() {
        function notifyResize() {
          window.parent.postMessage({ type: 'epub-resize' }, '*');
        }
        window.addEventListener('load', notifyResize);
        if (document.readyState === 'complete') notifyResize();
        if (typeof ResizeObserver !== 'undefined') {
          new ResizeObserver(notifyResize).observe(document.body);
        }
      })();
    `;

    const selectionScript = doc.createElement('script');
    selectionScript.textContent = `
      (function() {
        var timer = null;
        var mouseupDebounceTimer = null;
        var CHAPTER_HREF = ${JSON.stringify(currentChapterHref)};
        document.addEventListener('mouseup', function() {
          if (timer) clearTimeout(timer);
          if (mouseupDebounceTimer) clearTimeout(mouseupDebounceTimer);
          mouseupDebounceTimer = setTimeout(function() {
            mouseupDebounceTimer = null;
            var sel = window.getSelection();
            if (!sel || sel.isCollapsed || !sel.toString().trim()) return;
            var text = sel.toString().trim();
            var range = sel.getRangeAt(0);
            // SEL-1 / SEL-3: translate iframe-local coordinates to
            // parent-viewport so the SelectionToolbar lands at the
            // visible selection (not at the top-left of the screen).
            var frameRect = (window.frameElement && window.frameElement.getBoundingClientRect)
              ? window.frameElement.getBoundingClientRect()
              : { left: 0, top: 0, right: 0, bottom: 0, width: 0, height: 0 };
            var rect = range.getBoundingClientRect();
            var bounds = {
              left: rect.left + frameRect.left,
              top: rect.top + frameRect.top,
              right: rect.right + frameRect.left,
              bottom: rect.bottom + frameRect.top
            };
            var container = {
              left: frameRect.left,
              top: frameRect.top,
              width: frameRect.width,
              height: frameRect.height
            };
            var clientRects = range.getClientRects();
            var rects = [];
            for (var i = 0; i < clientRects.length; i++) {
              var r = clientRects[i];
              rects.push({
                left: r.left + frameRect.left,
                top: r.top + frameRect.top,
                width: r.width,
                height: r.height
              });
            }
            // CFI-1: compute the CFI for the selection in this chapter.
            // Done in the iframe because the bridge needs the chapter's
            // Document, which only exists here.
            var cfi = null;
            try {
              if (window.__cfiBridge && typeof window.__cfiBridge.rangeToCFI === 'function') {
                cfi = window.__cfiBridge.rangeToCFI(range, CHAPTER_HREF, document);
              }
            } catch (e) {
              console.warn('epub-cfi: rangeToCFI threw', e);
            }
            window.parent.postMessage({
              type: 'epub-selection',
              text: text,
              bounds: bounds,
              container: container,
              rects: rects,
              pageNumber: ${currentChapterIndex},
              cfi: cfi
            }, '*');
          }, 100);
        });
        document.addEventListener('selectionchange', function() {
          var sel = window.getSelection();
          if (!sel || sel.isCollapsed) {
            if (timer) clearTimeout(timer);
            timer = setTimeout(function() {
              window.parent.postMessage({ type: 'epub-selection', text: '', pageNumber: ${currentChapterIndex} }, '*');
            }, 200);
          }
        });
      })();
    `;

    // Scripts run in document order. The bridge must mount before the
    // selection script (which calls window.__cfiBridge.rangeToCFI) and
    // before the spine init (which calls setSpine).
    doc.body.appendChild(bridgeScript);
    doc.body.appendChild(spineScript);
    doc.body.appendChild(resizeScript);
    doc.body.appendChild(selectionScript);

    // Strip untrusted <script> tags from EPUB content before serialization
    for (const script of doc.querySelectorAll('script')) {
      script.remove();
    }

    // Use outerHTML instead of XMLSerializer for HTML5-compliant serialization
    // XMLSerializer produces XHTML self-closing tags that break HTML5 parsing
    const serialized = doc.documentElement.outerHTML;
    return `<!DOCTYPE html>\n${serialized}`;
  }

  function refreshReaderStyles(): void {
    if (!iframeEl?.contentDocument) return;

    const doc = iframeEl.contentDocument;
    let readerStyle = doc.getElementById('nextpage-reader-overrides');
    if (!readerStyle) {
      readerStyle = doc.createElement('style');
      readerStyle.id = 'nextpage-reader-overrides';
      doc.head.appendChild(readerStyle);
    }

    let css = buildReaderOverrideCss();
    const userFont = fontFamily.trim();
    if (userFont && userFont !== 'serif') {
      css += `
        body {
          font-family: ${userFont}, serif !important;
        }
      `;
    }
    readerStyle.textContent = css;
    syncIframeHeight();
  }

  // ─── Render Chapter ──────────────────────────────────────
  async function renderChapter(index: number): Promise<void> {
    if (!metadata || !iframeEl) return;

    try {
      const chapterData = await invoke<EpubChapterContent>('get_epub_chapter', {
        bookId,
        chapterIndex: index,
      });

      currentChapterIndex = index;
      iframeContentHeight = 0;

      const spineHrefs = metadata.chapters.map((c) => c.href);
      const chapterHref = metadata.chapters[index]?.href ?? '';
      const srcdoc = buildChapterSrcdoc(chapterData, metadata.resourcesPath, spineHrefs, chapterHref);

      iframeEl.onload = () => {
        syncIframeHeight();
        if (zoomContainerEl) {
          zoomContainerEl.scrollTop = 0;
        }
        // Defensive: re-set the spine in case the inline spine script
        // didn't run (e.g. if the iframe document was replaced before
        // the script executed). The bridge's setSpine is idempotent.
        try {
          const win = iframeEl?.contentWindow as (Window & { __cfiBridge?: { setSpine: (h: string[]) => void } }) | null;
          if (win?.__cfiBridge && typeof win.__cfiBridge.setSpine === 'function') {
            win.__cfiBridge.setSpine(spineHrefs);
          }
        } catch (e) {
          console.warn('epub-cfi: failed to re-set spine on iframe load', e);
        }
      };
      iframeEl.srcdoc = srcdoc;
      lastRenderedChapter = index;

      const pct = ((index + 0.5) / totalChapters) * 100;
      onLocationChange?.(`chapter:${index}`, pct);
      onLocationContext?.({ locator: `chapter:${index}`, percentage: pct });
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
      setReaderError(error);
    }
  }

  // ─── Navigation ──────────────────────────────────────────
  function goToPrev(): void {
    if (currentChapterIndex > 0) {
      currentChapterIndex -= 1;
    }
  }

  function goToNext(): void {
    if (currentChapterIndex < totalChapters - 1) {
      currentChapterIndex += 1;
    }
  }

  function goToChapter(index: number): void {
    if (index >= 0 && index < totalChapters) {
      currentChapterIndex = index;
    }
  }

  async function handleGoToPage(page: number): Promise<boolean> {
    const chapterIndex = page - 1;
    if (chapterIndex >= 0 && chapterIndex < totalChapters) {
      goToChapter(chapterIndex);
      return true;
    }
    return false;
  }

  // ─── Zoom ─────────────────────────────────────────────────
  function changeZoom(delta: number): void {
    const newZoom = Math.max(50, Math.min(200, zoomLevel + delta));
    if (newZoom !== zoomLevel) {
      zoomLevel = newZoom;
    }
  }

  function handleWheel(e: WheelEvent): void {
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

  function handleKeydown(e: KeyboardEvent): void {
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

<div class="flex flex-col h-full w-full min-h-0 outline-none relative" tabindex="-1" role="presentation"
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
    <!-- EpubControls top bar -->
    <EpubControls
      currentPage={currentChapterIndex + 1}
      totalPages={totalChapters}
      currentPercentage={((currentChapterIndex + 0.5) / totalChapters) * 100}
      fontSize={fontSize}
      isFullscreen={isFullscreen}
      {t}
      onPrev={goToPrev}
      onNext={goToNext}
      onGoToPage={handleGoToPage}
      onFontSizeChange={(size: number) => {
        fontSize = size;
        const updated: ReaderSettings = {
          ...readerSettings,
          epub: { ...readerSettings.epub, fontSize: size },
        };
        onSettingsChange?.(updated);
      }}
      onToggleFullscreen={() => onToggleFullscreen?.()}
      onToggleToc={() => onToggleToc?.()}
    />

    <!-- Chapter iframe — parent scrolls, iframe grows to content height -->
    <div
      class="flex-1 w-full min-h-0 overflow-y-auto pb-16"
      style="background: {getThemeBgColor()};"
      bind:this={zoomContainerEl}
      onwheel={handleWheel}
    >
      <iframe
        bind:this={iframeEl}
        class="w-full border-none block"
        style:height={iframeContentHeight > 0 ? `${iframeContentHeight}px` : 'auto'}
        title="chapter"
      ></iframe>
    </div>
  {/if}
</div>
