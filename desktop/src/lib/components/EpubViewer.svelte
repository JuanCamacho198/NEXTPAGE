<script lang="ts">
  import { onMount } from "svelte";
  import type { Book, Rendition } from "epubjs";
  import ePub from "epubjs";

  type Props = {
    filePath: string;
    initialLocation?: string;
    initialPercentage?: number;
    onLocationChange?: (cfiLocation: string, percentage: number) => void;
    searchTargetLocator?: string | null;
    onLocationContext?: (context: { locator: string; percentage: number }) => void;
  };

  let {
    filePath,
    initialLocation = "",
    initialPercentage = 0,
    onLocationChange,
    searchTargetLocator = null,
    onLocationContext,
  }: Props = $props();

  let viewerContainer: HTMLDivElement | undefined = $state();
  let book: Book | null = $state(null);
  let rendition: Rendition | null = $state(null);
  let isLoading = $state(true);
  let error = $state<string | null>(null);

  let currentLocation = $state("");
  let currentPercentage = $state(initialPercentage);
  let lastJumpTarget = "";
  let displaySettings = $state({
    fontSize: 100,
    fontFamily: "Georgia",
    margin: 20,
    theme: "light",
  });

  let toc = $state<Array<{ id: string; label: string; href: string }>>([]);
  let showToc = $state(false);

  let epubContainer: HTMLDivElement | undefined = $state();

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

  async function initEpub() {
    if (!filePath) return;

    isLoading = true;
    error = null;

    try {
      if (book) {
        book.destroy();
      }

      book = ePub(filePath) as unknown as Book;

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
      error = err instanceof Error ? err.message : "Failed to load EPUB";
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
    });

    rendition.on("relocated", (loc: { start: { cfi: string } }) => {
      currentLocation = loc.start.cfi;
    });
  }

  function applyDisplaySettings() {
    if (!rendition) return;

    (rendition as Rendition).themes.fontSize(`${displaySettings.fontSize}%`);
    (rendition as Rendition).themes.font(displaySettings.fontFamily);
  }

  $effect(() => {
    const target = searchTargetLocator?.trim();
    if (!target || !rendition || target === lastJumpTarget) {
      return;
    }

    lastJumpTarget = target;
    void rendition.display(target);
  });

  function goToPrev() {
    if (!rendition) return;
    rendition.prev();
  }

  function goToNext() {
    if (!rendition) return;
    rendition.next();
  }

  function goToChapter(chapter: { id: string; href: string }) {
    if (!rendition) return;
    rendition.display(chapter.href);
    showToc = false;
  }

  function updateFontSize(size: number) {
    displaySettings.fontSize = Math.max(50, Math.min(200, size));
    applyDisplaySettings();
  }

  function updateMargin(margin: number) {
    displaySettings.margin = Math.max(0, Math.min(50, margin));
    applyDisplaySettings();
  }
</script>

<div class="epub-viewer">
  {#if isLoading}
    <div class="loading">Loading EPUB...</div>
  {:else if error}
    <div class="error">Error: {error}</div>
  {:else}
    <div class="toolbar">
      <button type="button" onclick={() => (showToc = !showToc)} class="toc-btn">
        {showToc ? "Hide" : "TOC"}
      </button>
      <span class="progress">{Math.round(currentPercentage)}%</span>
      <div class="nav-buttons">
        <button type="button" onclick={goToPrev} class="nav-btn">Previous</button>
        <button type="button" onclick={goToNext} class="nav-btn">Next</button>
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
          <h3>Table of Contents</h3>
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

      <div bind:this={epubContainer} class="epub-container"></div>
    </div>
  {/if}
</div>

<style>
  .epub-viewer {
    display: flex;
    flex-direction: column;
    height: 100%;
    background: #faf9f7;
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
    background: #fff;
    border-bottom: 1px solid #e5e7eb;
    flex-wrap: wrap;
  }

  .toc-btn,
  .nav-btn,
  .size-btn {
    padding: 6px 12px;
    border: 1px solid #d1d5db;
    border-radius: 4px;
    background: #fff;
    cursor: pointer;
    font-size: 13px;
  }

  .toc-btn:hover,
  .nav-btn:hover,
  .size-btn:hover {
    background: #f3f4f6;
  }

  .nav-buttons {
    display: flex;
    gap: 8px;
  }

  .progress {
    font-size: 13px;
    color: #6b7280;
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
    background: #fff;
    border-right: 1px solid #e5e7eb;
    overflow-y: auto;
    flex-shrink: 0;
  }

  .toc-sidebar h3 {
    padding: 12px;
    font-size: 14px;
    font-weight: 600;
    border-bottom: 1px solid #e5e7eb;
    margin: 0;
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
    background: #f3f4f6;
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