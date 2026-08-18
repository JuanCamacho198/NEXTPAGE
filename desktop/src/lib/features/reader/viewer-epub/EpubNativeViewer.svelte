<script lang="ts">
  import { onMount } from 'svelte';
  import { invoke } from '@tauri-apps/api/core';
  import { convertFileSrc } from '@tauri-apps/api/core';
  import type { MessageKey } from '$lib/shared/i18n';
  import type {
    ReaderSettings,
    ReaderThemeMode,
    ReaderTextAlign,
    ReaderDirection,
  } from '$lib/shared/types';
  import EpubControls from './EpubControls.svelte';
  import { setReaderError, clearReaderError } from '$lib/stores/readerErrorState.svelte';
  import { debugState } from '$lib/shared/debug/debugState.svelte';
  import { IFRAME_CFI_BRIDGE_SCRIPT } from '$lib/features/reader/viewer-epub/cfiBridgeIframe';
  import { IFRAME_HIGHLIGHT_OVERLAY_SCRIPT } from '$lib/features/reader/viewer-epub/epubHighlightOverlayIframe';
  import {
    HIGHLIGHT_COLORS,
    highlightFillRgba,
  } from '$lib/features/reader/highlight/highlightColors';
  import type { HighlightActionKind, HighlightActionOpts } from '$lib/shared/types/book';
  import { locatorFromCfi, locatorToJson } from '$lib/shared/sync/LocatorCodec';

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
    /**
     * External navigation target (e.g. from "View in book" on a highlight
     * or from full-text search). When set to an EPUB CFI, the viewer
     * navigates to the owning chapter and scrolls the target into view.
     * The caller re-sets this value to trigger a fresh jump.
     */
    searchTargetLocator?: string | null;
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
    /**
     * All persisted highlights for the current book. The EPUB iframe
     * renders the highlights whose `pageNumber === currentChapterIndex`
     * via the CSS Custom Highlight API (zero DOM mutation). Typed as a
     * slim shape (subset of `HighlightDto`) so callers don't have to
     * build the full DTO to feed the iframe.
     */
    persistedHighlights?: Array<{
      id: string;
      color: string;
      pageNumber: number;
      cfi?: string | null;
      text?: string | null;
    }>;
    /**
     * Called when the user clicks a persisted highlight inside the EPUB
     * iframe. The parent uses this to render the Menu 2 toolbar
     * (color picker + delete + close) at the click point in
     * parent-viewport coordinates.
     */
    onHighlightAction?: (
      action: HighlightActionKind,
      id: string,
      opts?: HighlightActionOpts,
    ) => void;
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
    searchTargetLocator = null,
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
    persistedHighlights = [],
    onHighlightAction,
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
  /** CFI que se debe mostrar una vez que el capítulo objetivo cargue. */
  let pendingCfiScroll = $state<string | null>(null);

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

    // Debug: capture every postMessage so the panel can show what arrived.
    if (debugState.enabled) {
      debugState.epub.postMessageCount++;
      debugState.epub.lastRawMessage = {
        type: String(event.data.type ?? ''),
        pageNumber: typeof event.data.pageNumber === 'number' ? event.data.pageNumber : null,
        hasText: typeof event.data.text === 'string' && event.data.text.length > 0,
        textPreview: typeof event.data.text === 'string' ? event.data.text.slice(0, 60) : '',
        hasBounds: !!event.data.bounds,
        hasContainer: !!event.data.container,
        hasCfi: typeof event.data.cfi === 'string' && event.data.cfi.length > 0,
        cfiPreview: typeof event.data.cfi === 'string' ? event.data.cfi.slice(0, 60) : '',
      };
      debugState.epub.guardResult = 'none';
    }

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

    if (event.data.type === 'epub-hl-failed') {
      handleEpubHighlightFailed(event.data);
      return;
    }

    if (event.data.type !== 'epub-selection') {
      if (debugState.enabled) debugState.epub.guardResult = 'drop-unknown-type';
      return;
    }

    if (
      typeof event.data.pageNumber === 'number' &&
      event.data.pageNumber !== currentChapterIndex
    ) {
      // Stale message from a chapter the user has already navigated
      // away from. Drop silently.
      if (debugState.enabled) debugState.epub.guardResult = 'drop-chapter-mismatch';
      return;
    }

    if (!event.data.text) {
      if (debugState.enabled) {
        debugState.epub.guardResult = 'drop-empty-text';
        debugState.epub.emptyTextMessageCount++;
      }
      debugState.epub.onselectionclearCalled++;
      onselectionclear?.();
      return;
    }

    if (!onselection) {
      if (debugState.enabled) debugState.epub.guardResult = 'drop-no-handler';
      return;
    }

    if (debugState.enabled) debugState.epub.guardResult = 'pass';
    debugState.epub.onselectionCalled++;
    debugState.epub.rectCount = Array.isArray(event.data.rects) ? event.data.rects.length : 0;

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

  // ─── Menu 2 (highlight click) ──────────────────────────────
  function handleEpubHighlightClick(msg: {
    id: string;
    x: number;
    y: number;
    color: string;
    text?: string;
    pageNumber: number;
  }): void {
    // HM-4: pageNumber guard (already enforced by the switch above
    // for messages with the field; this is a defensive duplicate).
    if (msg.pageNumber !== currentChapterIndex) return;
    if (!iframeEl || !onHighlightAction) return;
    // Translate iframe-local click point to parent-viewport coords
    // using the iframe element's bounding rect.
    const frameRect = iframeEl.getBoundingClientRect();
    onHighlightAction('open', msg.id, {
      color: msg.color,
      text: msg.text,
      x: msg.x + frameRect.left,
      y: msg.y + frameRect.top,
    });
  }

  // ─── epub-hl-failed: apply failure observability ──────────────
  // The iframe overlay posts this when cfiToRange returns null (cfi-
  // unresolved) or hexToRgba returns null (invalid-color). We mirror
  // the id to debugState with Set-like dedup and log a warning. Bounded
  // growth: n < 100 in practice. See design Decision 2.
  function handleEpubHighlightFailed(msg: {
    id: string;
    reason: string;
    pageNumber: number;
    cfi?: string;
    color?: string;
  }): void {
    if (msg.id && !debugState.epub.failedHighlightIds.includes(msg.id)) {
      debugState.epub.failedHighlightIds.push(msg.id);
    }
    console.warn('epub-hl: highlight failed to apply', {
      id: msg.id,
      reason: msg.reason,
      pageNumber: msg.pageNumber,
    });
  }

  function syncIframeHeight(): void {
    if (!iframeEl?.contentDocument) return;

    const doc = iframeEl.contentDocument;
    // `documentElement` and `body` can be null while the iframe is still
    // mid-load (e.g. when an `epub-resize` postMessage arrives before the
    // chapter DOM is ready). Bail out instead of throwing.
    const docEl = doc.documentElement;
    const body = doc.body;
    if (!docEl || !body) return;

    const height = Math.max(docEl.scrollHeight, body.scrollHeight);
    iframeContentHeight = height > 0 ? height : 0;
  }

  onMount(() => {
    initReader();
    window.addEventListener('message', handleIframeMessage);
    return () => {
      window.removeEventListener('message', handleIframeMessage);
    };
  });

  // ─── Debug: track iframe rect so the panel can show where the iframe sits ──
  $effect(() => {
    const el = iframeEl;
    if (!el || !debugState.enabled) return;
    const update = (): void => {
      const r = el.getBoundingClientRect();
      debugState.epub.iframeRect = { left: r.left, top: r.top, width: r.width, height: r.height };
    };
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    window.addEventListener('scroll', update, true);
    window.addEventListener('resize', update);
    return () => {
      ro.disconnect();
      window.removeEventListener('scroll', update, true);
      window.removeEventListener('resize', update);
    };
  });

  $effect(() => {
    debugState.epub.currentChapterIndex = currentChapterIndex;
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

  // ─── Re-render persisted highlights inside the iframe ──────
  // Runs after every chapter change (when the iframe document is
  // fresh) and whenever the parent's `persistedHighlights` reference
  // changes (e.g. after a color update or delete). The overlay is
  // idempotent -- it clears existing wraps before re-applying.
  type EpubHighlightShape = {
    id: string;
    color: string;
    pageNumber: number;
    cfi?: string | null;
    text?: string | null;
  };
  $effect(() => {
    if (!metadata || isLoading || !iframeEl) return;
    if (lastRenderedChapter !== currentChapterIndex) return; // wait for chapter to load
    const win = iframeEl.contentWindow as
      | (Window & {
          __epubHighlightOverlay?: {
            render: (h: EpubHighlightShape[], chapterHref: string, idx: number) => void;
          };
        })
      | null;
    if (!win || !win.__epubHighlightOverlay) return;
    const chapterHref = metadata.chapters[currentChapterIndex]?.href ?? '';
    // Touch persistedHighlights to track it reactively.
    void persistedHighlights;
    try {
      win.__epubHighlightOverlay.render(persistedHighlights, chapterHref, currentChapterIndex);
    } catch (err) {
      console.warn('epub-hl: render failed', err);
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

  // ─── Search / "View in book" target navigation ───────────
  // When `searchTargetLocator` carries an EPUB CFI, parse the owning
  // chapter from the CFI's spine index (`epubcfi(/6/N!...)`), navigate
  // there, and once the chapter has rendered, scroll the CFI range into
  // view (via the iframe bridge). Re-setting the prop re-triggers the jump.
  $effect(() => {
    const target = searchTargetLocator;
    if (!target || !target.startsWith('epubcfi(') || !metadata) return;

    const spineMatch = /epubcfi\(\/6\/(\d+)!/.exec(target);
    if (!spineMatch) return;

    const spineIndex = Number.parseInt(spineMatch[1], 10); // 1-based
    const chapterIdx = spineIndex - 1;
    if (chapterIdx < 0 || chapterIdx >= totalChapters) return;

    if (chapterIdx !== currentChapterIndex) {
      pendingCfiScroll = target;
      goToChapter(chapterIdx);
    } else {
      scrollToCfi(target);
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

  /**
   * Scan the chapter HTML for `@font-face` rules whose `src:` points to a
   * local font file (the EPUB declares them but the editor forgot to embed
   * the .ttf/.otf/.woff/.woff2 inside the archive). Returns the list of
   * resolved `asset://` URLs so the caller can probe them with a HEAD
   * request and drop the ones that 404/403.
   *
   * EPUBs in the wild routinely reference Neutraface, Felt Tip Roman and
   * other commercial fonts in their CSS without bundling the file. Without
   * this filter, the iframe fires a 403 per missing font on every chapter.
   */
  function collectFontFaceAssetUrls(
    html: string,
    chapterPath: string,
    resourcesPath: string,
  ): string[] {
    const urls = new Set<string>();
    const fontExt = /\.(ttf|otf|woff2?|eot)(\?|$|#)/i;
    const urlPattern = /url\(\s*(['"]?)([^'")]+)\1\s*\)/gi;

    const parser = new DOMParser();
    const doc = parser.parseFromString(html, 'text/html');
    const normalizedChapter = chapterPath.replace(/\\/g, '/');

    const collectFromCss = (cssText: string): void => {
      // Only consider rules that look like @font-face (a heuristic: contain
      // `font-face`). This avoids touching unrelated background-image urls.
      if (!/font-face/i.test(cssText)) return;
      let match: RegExpExecArray | null;
      urlPattern.lastIndex = 0;
      while ((match = urlPattern.exec(cssText)) !== null) {
        const rawUrl = match[2];
        if (
          !rawUrl ||
          rawUrl.startsWith('http') ||
          rawUrl.startsWith('data:') ||
          rawUrl.startsWith('asset:') ||
          rawUrl.startsWith('#') ||
          rawUrl.startsWith('local(')
        ) {
          continue;
        }
        if (!fontExt.test(rawUrl)) continue;
        const resourcePath = resolveResourcePath(normalizedChapter, rawUrl);
        urls.add(toAssetUrl(resourcesPath, resourcePath));
      }
    };

    for (const styleEl of doc.querySelectorAll('style')) {
      collectFromCss(styleEl.textContent ?? '');
    }
    // We don't probe linked stylesheets (they're loaded async by the iframe
    // and their body isn't in the HTML string we get from Rust). The 403s
    // for those are handled by the same `missingFonts` filter applied to
    // their `@font-face` rules once they're parsed by the browser.
    return Array.from(urls);
  }

  /**
   * HEAD-probe a batch of asset URLs in parallel and return the set of
   * URLs that didn't respond OK. Any error (network, 4xx, 5xx) is treated
   * as "missing" — the iframe would 403 anyway, so it's safe to drop the
   * corresponding `@font-face` rule.
   */
  async function probeMissingFontUrls(urls: string[]): Promise<Set<string>> {
    const missing = new Set<string>();
    if (urls.length === 0) return missing;
    const results = await Promise.allSettled(
      urls.map(async (url) => {
        try {
          const res = await fetch(url, { method: 'HEAD' });
          if (!res.ok) throw new Error(`status ${res.status}`);
        } catch {
          throw new Error('missing');
        }
      }),
    );
    urls.forEach((url, i) => {
      if (results[i].status === 'rejected') {
        missing.add(url);
      }
    });
    return missing;
  }

  /**
   * Drop `@font-face` rules from a CSS string whose rewritten `src:` URL
   * appears in `missingFonts`. We keep the rest of the CSS intact so the
   * cascade falls back to the system font instead of a broken custom one.
   */
  function stripMissingFontFaces(cssText: string, missingFonts: Set<string>): string {
    if (missingFonts.size === 0) return cssText;
    return cssText.replace(/@font-face\s*\{[^{}]*\}/gi, (rule) => {
      const urlMatch = rule.match(/url\(\s*(['"]?)([^'")]+)\1\s*\)/i);
      if (!urlMatch) return rule;
      if (missingFonts.has(urlMatch[2])) return '';
      return rule;
    });
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

  function buildChapterSrcdoc(
    chapterData: EpubChapterContent,
    resourcesPath: string,
    spineHrefs: string[],
    currentChapterHref: string,
    missingFonts: Set<string> = new Set(),
  ): string {
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
      if (!src || src.startsWith('http') || src.startsWith('data:') || src.startsWith('asset:'))
        continue;

      const resourcePath = resolveResourcePath(chapterPath, src);
      el.setAttribute(attrName, toAssetUrl(resourcesPath, resourcePath));
    }

    for (const styleEl of doc.querySelectorAll('style')) {
      let cssText = styleEl.textContent ?? '';
      if (!cssText.includes('url(')) continue;

      cssText = cssText.replace(
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
      // After rewriting, drop @font-face rules whose URL was probed and
      // found missing — they'd 403 the iframe and pollute the console.
      if (missingFonts.size > 0) {
        cssText = stripMissingFontFaces(cssText, missingFonts);
      }
      styleEl.textContent = cssText;
    }

    let readerStyle = doc.getElementById('nextpage-reader-overrides');
    if (!readerStyle) {
      readerStyle = doc.createElement('style');
      readerStyle.id = 'nextpage-reader-overrides';
      doc.head.appendChild(readerStyle);
    }
    readerStyle.textContent = buildReaderOverrideCss();

    // ::highlight() rules for the CSS Custom Highlight API must live in
    // their OWN style element — NEVER inside nextpage-reader-overrides,
    // because refreshReaderStyles() rewrites that element's textContent
    // on every settings change and would wipe the rules. One rule per
    // canonical color (the overlay maps every highlight to a canonical
    // color before registering, so static rules suffice). Injecting
    // here means the rules survive settings changes and chapter reloads.
    const highlightStyle = doc.createElement('style');
    highlightStyle.id = 'nextpage-highlight-styles';
    highlightStyle.textContent = HIGHLIGHT_COLORS.map(
      (color) =>
        `::highlight(epub-hl-${color.label}) { background-color: ${highlightFillRgba(color.hex, 0.4)}; }`,
    ).join('\n');
    doc.head.appendChild(highlightStyle);

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

    // The highlight overlay registers persisted highlights via the CSS
    // Custom Highlight API (CSS.highlights + ::highlight(), zero DOM
    // mutation). It must run after the bridge (which it calls via
    // __cfiBridge.cfiToRange) and before the selection script (which
    // calls its hitTest/rangeOverlapsHighlight helpers).
    const highlightOverlayScript = doc.createElement('script');
    highlightOverlayScript.textContent = IFRAME_HIGHLIGHT_OVERLAY_SCRIPT;

    const selectionScript = doc.createElement('script');
    selectionScript.textContent = `
      (function() {
        var timer = null;
        var mouseupDebounceTimer = null;
        var CHAPTER_HREF = ${JSON.stringify(currentChapterHref)};
        var CHAPTER_INDEX = ${currentChapterIndex};

        // HM-3 / HM-4: click handler via overlay hit-testing (caretRangeFromPoint
        // + inclusive boundary math against the overlay's per-id side-table).
        // The DOM is never mutated, so there are no .epub-hl spans to walk.
        // The iframe script owns this because the parent can't access the
        // iframe's DOM directly via postMessage alone (no Range object). We
        // stopPropagation to prevent the document-level mouseup from
        // triggering Menu 1.
        document.addEventListener('click', function(ev) {
          var hit = null;
          try {
            if (window.__epubHighlightOverlay &&
                typeof window.__epubHighlightOverlay.hitTest === 'function') {
              hit = window.__epubHighlightOverlay.hitTest(ev.clientX, ev.clientY);
            }
          } catch (e) {
            hit = null;
          }
          if (!hit) return;
          ev.stopPropagation();
          if (ev.preventDefault) ev.preventDefault();
          if (mouseupDebounceTimer) {
            clearTimeout(mouseupDebounceTimer);
            mouseupDebounceTimer = null;
          }
          if (timer) {
            clearTimeout(timer);
            timer = null;
          }
          var sel = window.getSelection();
          if (sel) sel.removeAllRanges();
          window.parent.postMessage({
            type: 'epub-highlight-click',
            id: hit.id,
            x: ev.clientX,
            y: ev.clientY,
            color: hit.color,
            text: hit.text,
            pageNumber: CHAPTER_INDEX
          }, '*');
        }, true);

        document.addEventListener('mouseup', function() {
          if (timer) clearTimeout(timer);
          if (mouseupDebounceTimer) clearTimeout(mouseupDebounceTimer);
          mouseupDebounceTimer = setTimeout(function() {
            mouseupDebounceTimer = null;
            var sel = window.getSelection();
            if (!sel || sel.isCollapsed || !sel.toString().trim()) return;
            var text = sel.toString().trim();
            var range = sel.getRangeAt(0);
            // If the selection overlaps a registered highlight, skip
            // Menu 1 -- Menu 2 owns that gesture. The overlay tests the
            // live selection Range against its per-id side-table.
            var overlapsHl = false;
            try {
              if (window.__epubHighlightOverlay &&
                  typeof window.__epubHighlightOverlay.rangeOverlapsHighlight === 'function') {
                overlapsHl = window.__epubHighlightOverlay.rangeOverlapsHighlight(range);
              }
            } catch (e) {
              overlapsHl = false;
            }
            if (overlapsHl) return;
            // SEL-1 / SEL-3: SelectionToolbar treats bounds as
            // CONTAINER-relative (the iframe is the container here) and
            // container as PARENT-viewport. So we must NOT add frameRect
            // to bounds -- the iframe-local rect IS already in container-
            // relative coords. We only add frameRect to container.
            var frameRect = (window.frameElement && window.frameElement.getBoundingClientRect)
              ? window.frameElement.getBoundingClientRect()
              : { left: 0, top: 0, right: 0, bottom: 0, width: 0, height: 0 };
            var rect = range.getBoundingClientRect();
            var bounds = {
              left: rect.left,
              top: rect.top,
              right: rect.right,
              bottom: rect.bottom
            };
            var containerRect = {
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
                left: r.left,
                top: r.top,
                width: r.width,
                height: r.height
              });
            }
            // CFI-1: compute the CFI for the selection in this chapter.
            // Done in the iframe because the bridge needs the chapter's
            // Document, which only exists here. The DOM is never
            // mutated by highlight rendering (CSS Highlights never
            // touch the document), so the live DOM IS the pristine DOM
            // -- capture and resolution always see identical structure.
            // The direct bridge call is therefore sufficient; the
            // pristine-copy computeCFI fallback is gone.
            var cfi = null;
            try {
              if (window.__cfiBridge &&
                  typeof window.__cfiBridge.rangeToCFI === 'function') {
                cfi = window.__cfiBridge.rangeToCFI(range, CHAPTER_HREF, document);
              }
            } catch (e) {
              console.warn('epub-cfi: rangeToCFI threw', e);
            }
            window.parent.postMessage({
              type: 'epub-selection',
              text: text,
              bounds: bounds,
              container: containerRect,
              rects: rects,
              pageNumber: CHAPTER_INDEX,
              cfi: cfi
            }, '*');
          }, 100);
        });
        document.addEventListener('selectionchange', function() {
          var sel = window.getSelection();
          if (!sel || sel.isCollapsed) {
            if (timer) clearTimeout(timer);
            timer = setTimeout(function() {
              window.parent.postMessage({ type: 'epub-selection', text: '', pageNumber: CHAPTER_INDEX }, '*');
            }, 200);
          }
        });
        // Scroll dismiss: when the user scrolls the chapter, the selection
        // is still in the DOM but the user can't see it -- dismiss the
        // Menu 1 toolbar so it stops "floating" over unrelated content.
        // Browsers don't fire selectionchange on scroll, so we need this.
        var scrollDismissTimer = null;
        function dismissOnScroll() {
          if (scrollDismissTimer) clearTimeout(scrollDismissTimer);
          scrollDismissTimer = setTimeout(function() {
            window.parent.postMessage({ type: 'epub-selection', text: '', pageNumber: CHAPTER_INDEX }, '*');
          }, 50);
        }
        window.addEventListener('scroll', dismissOnScroll, true);
        document.addEventListener('scroll', dismissOnScroll, true);
      })();
    `;

    // CRITICAL ORDER: strip dangerous EPUB scripts BEFORE appending our own.
    // A previous version of this code stripped scripts AFTER appending, which
    // also stripped our injected bridge/spine/overlay/resize/selection scripts
    // and left the iframe with zero JavaScript — selection events fired but no
    // postMessage ever reached the parent (see git history for the bug).
    for (const script of doc.querySelectorAll('script')) {
      script.remove();
    }

    // Scripts run in document order. The bridge must mount before the
    // selection script (which calls window.__cfiBridge.rangeToCFI) and
    // before the spine init (which calls setSpine). The highlight
    // overlay must mount after the bridge (it calls __cfiBridge.cfiToRange)
    // but before the selection script (so the click handler is in place
    // when the user clicks a rendered highlight).
    doc.body.appendChild(bridgeScript);
    doc.body.appendChild(spineScript);
    doc.body.appendChild(highlightOverlayScript);
    doc.body.appendChild(resizeScript);
    doc.body.appendChild(selectionScript);

    // Use outerHTML instead of XMLSerializer for HTML5-compliant serialization
    // XMLSerializer produces XHTML self-closing tags that break HTML5 parsing
    const serialized = doc.documentElement.outerHTML;
    return `<!DOCTYPE html>\n${serialized}`;
  }

  function refreshReaderStyles(): void {
    if (!iframeEl?.contentDocument) return;

    const doc = iframeEl.contentDocument;
    // `head` can be null while the iframe is still mid-load (e.g. about:srcdoc
    // before the chapter DOM is parsed). Bail out instead of throwing — the
    // next settings tick or chapter render will apply the styles.
    const head = doc.head;
    if (!head) return;
    let readerStyle = doc.getElementById('nextpage-reader-overrides');
    if (!readerStyle) {
      readerStyle = doc.createElement('style');
      readerStyle.id = 'nextpage-reader-overrides';
      head.appendChild(readerStyle);
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

  /**
   * Scroll a resolved CFI range into view inside the chapter iframe.
   * Uses the in-iframe CFI bridge (`cfiToRange`) to resolve the CFI to a
   * DOM range, then scrolls the first element of the range into view.
   */
  function scrollToCfi(cfi: string): void {
    if (!metadata || !iframeEl?.contentDocument || !iframeEl.contentWindow) return;
    const doc = iframeEl.contentDocument;
    const bridge = (
      iframeEl.contentWindow as Window & {
        __cfiBridge?: { cfiToRange: (cfi: string, href: string, doc: Document) => Range | null };
      }
    ).__cfiBridge;
    const chapterHref = metadata.chapters[currentChapterIndex]?.href ?? '';
    if (!bridge || !chapterHref) return;

    try {
      const range = bridge.cfiToRange(cfi, chapterHref, doc);
      if (!range || !range.startContainer) return;
      const targetNode =
        range.startContainer.nodeType === Node.TEXT_NODE
          ? range.startContainer.parentElement
          : (range.startContainer as Element);
      if (targetNode) {
        (targetNode as Element).scrollIntoView({ block: 'center', behavior: 'smooth' });
      }
      pendingCfiScroll = null;
    } catch (err) {
      console.warn('epub-cfi: scrollToCfi failed', err);
    }
  }

  /** Emit the precise CFI at the top visible text node in the chapter iframe. */
  function emitPreciseLocation(): void {
    if (!metadata || !iframeEl?.contentDocument || !iframeEl.contentWindow) return;

    const doc = iframeEl.contentDocument;
    const bridge = (
      iframeEl.contentWindow as Window & {
        __cfiBridge?: {
          rangeToCFI: (range: Range, href: string, document: Document) => string | null;
        };
      }
    ).__cfiBridge;
    const chapterHref = metadata.chapters[currentChapterIndex]?.href ?? '';
    if (!bridge || !chapterHref) return;

    const nodes: Text[] = [];
    const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT);
    let node = walker.nextNode();
    while (node) {
      if ((node.nodeValue ?? '').trim().length > 0) nodes.push(node as Text);
      node = walker.nextNode();
    }
    const chapterChars = nodes.reduce((total, textNode) => total + (textNode.data?.length ?? 0), 0);
    if (chapterChars <= 0) return;

    const visibleNode = nodes.find((textNode) => {
      const range = doc.createRange();
      range.selectNodeContents(textNode);
      const rect = range.getBoundingClientRect();
      return rect.bottom >= 0 && rect.top <= (iframeEl?.clientHeight ?? window.innerHeight);
    });
    if (!visibleNode) return;

    const range = doc.createRange();
    range.setStart(visibleNode, 0);
    range.setEnd(visibleNode, Math.min(1, visibleNode.data.length));
    const preciseCfi = bridge.rangeToCFI(range, chapterHref, doc);
    if (!preciseCfi) return;

    const charOffset = nodes
      .slice(0, nodes.indexOf(visibleNode))
      .reduce((total, textNode) => total + textNode.data.length, 0);
    const locator = locatorFromCfi(
      metadata.chapters.map((chapter) => chapter.href),
      preciseCfi,
      {
        chapterChars,
        charOffset,
      },
    );
    if (!locator) return;

    const progression = locator.locations.progression ?? 0;
    const percentage = ((currentChapterIndex + progression) / totalChapters) * 100;
    onLocationChange?.(preciseCfi, percentage);
    onLocationContext?.({ locator: locatorToJson(locator), percentage });
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
      // Probe every @font-face URL the chapter declares. Those that 404/403
      // (typically because the EPUB references commercial fonts like
      // Neutraface or Felt Tip Roman without bundling the file) get their
      // rule stripped from the CSS so the iframe doesn't 403 on every load.
      const fontUrls = collectFontFaceAssetUrls(
        chapterData.html,
        chapterData.chapterPath,
        metadata.resourcesPath,
      );
      const missingFonts = await probeMissingFontUrls(fontUrls);
      const srcdoc = buildChapterSrcdoc(
        chapterData,
        metadata.resourcesPath,
        spineHrefs,
        chapterHref,
        missingFonts,
      );

      iframeEl.onload = () => {
        syncIframeHeight();
        if (zoomContainerEl) {
          zoomContainerEl.scrollTop = 0;
        }
        // Defensive: re-set the spine in case the inline spine script
        // didn't run (e.g. if the iframe document was replaced before
        // the script executed). The bridge's setSpine is idempotent.
        type EpubHighlightShape = {
          id: string;
          color: string;
          pageNumber: number;
          cfi?: string | null;
          text?: string | null;
        };
        try {
          const win = iframeEl?.contentWindow as
            | (Window & {
                __cfiBridge?: { setSpine: (h: string[]) => void };
                __epubHighlightOverlay?: {
                  render: (h: EpubHighlightShape[], chapterHref: string, idx: number) => void;
                };
              })
            | null;
          if (win?.__cfiBridge && typeof win.__cfiBridge.setSpine === 'function') {
            win.__cfiBridge.setSpine(spineHrefs);
          }
          // Render persisted highlights for the new chapter.
          if (win?.__epubHighlightOverlay) {
            win.__epubHighlightOverlay.render(persistedHighlights, chapterHref, index);
          }
        } catch (e) {
          console.warn('epub-cfi: failed to re-init iframe on load', e);
        }
        requestAnimationFrame(emitPreciseLocation);
        // After the new chapter renders, resolve any pending CFI scroll
        // ("View in book" / search jump that landed on a different chapter).
        if (pendingCfiScroll) {
          const cfi = pendingCfiScroll;
          requestAnimationFrame(() => scrollToCfi(cfi));
        }
      };
      iframeEl.srcdoc = srcdoc;
      lastRenderedChapter = index;
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

<div
  class="flex flex-col h-full w-full min-h-0 outline-none relative"
  tabindex="-1"
  role="presentation"
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
      {fontSize}
      {isFullscreen}
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
      onscroll={emitPreciseLocation}
    >
      <iframe
        bind:this={iframeEl}
        class="w-full border-none block"
        class:outline-2={debugState.enabled}
        class:outline-dashed={debugState.enabled}
        class:outline-red-500={debugState.enabled}
        style:height={iframeContentHeight > 0 ? `${iframeContentHeight}px` : 'auto'}
        title="chapter"
      ></iframe>
    </div>
  {/if}
</div>
