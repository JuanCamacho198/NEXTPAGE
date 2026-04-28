<script lang="ts">
  import type { Snippet } from "svelte";

  type Props = {
    format: "pdf" | "epub";
    i18n: (key: string, params?: Record<string, string | number>) => string;
    state: {
      tocOpen: boolean;
      currentPage: number;
      totalPages: number;
      progress: number;
      zoom?: number;
      fontSize?: number;
    };
    callbacks: {
      onToggleToc: () => void;
      onPrevPage: () => void;
      onNextPage: () => void;
    };
    pdfCallbacks?: {
      onZoomIn?: () => void;
      onZoomOut?: () => void;
      onFullscreen?: () => void;
      onSetScale?: (scale: number) => void;
    };
    epubCallbacks?: {
      onFontSizeChange?: (size: number) => void;
    };
    pdfActions?: Snippet;
    epubActions?: Snippet;
    class?: string;
  };

  let {
    format,
    i18n,
    state,
    callbacks,
    pdfCallbacks,
    epubCallbacks,
    pdfActions,
    epubActions,
    class: className = ""
  }: Props = $props();

  const scaleOptions = Array.from({ length: 26 }, (_, index) => (50 + index * 10) / 100);
</script>

<div class="flex flex-wrap items-center gap-3 border-b border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 {className}">
  <button
    type="button"
    class="rounded border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-1.5 text-sm text-[var(--color-primary)] transition-colors hover:bg-[color:var(--color-border)]"
    onclick={callbacks.onToggleToc}
  >
    {i18n(format === "pdf" ? "pdf.contents" : "epub.contents")}
  </button>

  <button
    type="button"
    class="reader-nav-button flex items-center gap-1.5 rounded border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-1.5 text-sm text-[var(--color-primary)] transition-colors hover:bg-[color:var(--color-border)] disabled:opacity-50 disabled:cursor-not-allowed"
    aria-label={i18n("pdf.previous")}
    onclick={callbacks.onPrevPage}
    disabled={state.currentPage <= 1}
  >
    <span aria-hidden="true">&#8592;</span>
    {i18n("pdf.previous")}
  </button>

  <span class="flex items-center gap-1 text-sm text-[var(--color-primary)]">
    {state.currentPage} / {state.totalPages}
  </span>

  <button
    type="button"
    class="reader-nav-button flex items-center gap-1.5 rounded border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-1.5 text-sm text-[var(--color-primary)] transition-colors hover:bg-[color:var(--color-border)] disabled:opacity-50 disabled:cursor-not-allowed"
    aria-label={i18n("pdf.next")}
    onclick={callbacks.onNextPage}
    disabled={state.currentPage >= state.totalPages}
  >
    <span aria-hidden="true">&#8594;</span>
    {i18n("pdf.next")}
  </button>

  {#if state.progress > 0}
    <span class="text-sm text-[var(--color-muted)]">
      {Math.round(state.progress)}%
    </span>
  {/if}

  {#if format === "pdf"}
    {#if pdfActions}
      {@render pdfActions()}
    {/if}
  {:else if format === "epub"}
    {#if epubActions}
      {@render epubActions()}
    {/if}
  {/if}
</div>