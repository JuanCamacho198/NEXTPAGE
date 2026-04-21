<script lang="ts">
  import type { Snippet } from "svelte";
  import type { LibraryBookDto } from "$lib/types";
  import type { MessageKey } from "$lib/i18n";
  import { getSafeProgressPercentage } from "$lib/stores/homeState";
  import SafeCover from "./SafeCover.svelte";

  type Variant = "shelf" | "continue-reading";

  type Props = {
    book: LibraryBookDto;
    variant: Variant;
    selected?: boolean;
    compact?: boolean;
    showReadButton?: boolean;
    onSelect?: () => void;
    onRead?: () => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    actions?: Snippet;
  };

  let {
    book,
    variant,
    selected = false,
    compact = false,
    showReadButton = true,
    onSelect,
    onRead,
    t,
    actions,
  }: Props = $props();

  const progress = $derived(Math.round(getSafeProgressPercentage(book)));
  const showProgress = $derived(variant === "continue-reading");

  const containerClass = $derived.by(() => {
    const selectedClass = selected
      ? "border-[var(--color-primary)] bg-[color:color-mix(in_srgb,var(--color-primary)_10%,var(--color-surface))]"
      : "border-[color:var(--color-border)] bg-[var(--color-background)]";
    const base = compact ? "rounded-lg border p-3" : "rounded-xl border p-4";
    const continueClass =
      variant === "continue-reading" && !compact
        ? "bg-[color:color-mix(in_srgb,var(--color-primary)_8%,var(--color-surface))]"
        : "";
    return `${base} ${selectedClass} ${continueClass}`;
  });
</script>

<article class={containerClass}>
  <div class="flex items-start justify-between gap-3">
    <button type="button" class="min-w-0 flex-1 text-left" onclick={onSelect}>
      <div class="flex items-start gap-3">
        <SafeCover path={book.coverPath ?? ""} alt={`Cover for ${book.title}`} className={compact ? "h-14 w-10 rounded object-cover" : "h-16 w-12 rounded object-cover"}>
          {#snippet fallback()}
            <div class={`${compact ? "h-14 w-10" : "h-16 w-12"} flex items-center justify-center rounded bg-[var(--color-surface)] text-[10px] uppercase text-[var(--color-text-muted)]`}>
              {t("library.cover")}
            </div>
          {/snippet}
        </SafeCover>

        <div class="min-w-0 flex-1">
          <p class={`${compact ? "text-sm" : "text-base"} line-clamp-2 font-semibold text-[var(--color-primary)]`}>{book.title}</p>
          <p class="truncate text-xs text-[var(--color-text-muted)]">{book.author || t("app.unknownAuthor")} · {book.format.toUpperCase()}</p>
          <p class="mt-1 text-xs text-[var(--color-text-muted)]">{book.currentPage}/{book.totalPages || "-"}</p>
          {#if showProgress}
            <div class="mt-2">
              <div class="mb-1 flex items-center justify-between text-[11px] text-[var(--color-text-muted)]">
                <span>{t("home.shelfSort.progress")}</span>
                <span>{progress}%</span>
              </div>
              <div class="h-1.5 w-full overflow-hidden rounded bg-[color:var(--color-border)]">
                <div class="h-full rounded bg-[var(--color-primary)]" style={`width:${progress}%`}></div>
              </div>
            </div>
          {/if}
        </div>
      </div>
    </button>

    <div class="flex items-start gap-2">
      {#if showReadButton}
        <button
          type="button"
          class="rounded-md bg-[var(--color-primary)] px-3 py-1.5 text-xs font-medium text-[var(--color-background)] hover:opacity-90"
          onclick={onRead}
        >
          {t("app.read")}
        </button>
      {/if}
      {@render actions?.()}
    </div>
  </div>
</article>
