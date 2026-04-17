<script lang="ts">
  import { convertFileSrc } from "@tauri-apps/api/core";
  import DropMenu from "../ui/DropMenu.svelte";
  import type { LibraryBookDto } from "../../types";
  import type { MessageKey } from "../../i18n";

  const LIBRARY_VIEW_MODE = {
    LIST: "list",
    GRID: "grid",
  } as const;

  type LibraryViewMode = (typeof LIBRARY_VIEW_MODE)[keyof typeof LIBRARY_VIEW_MODE];

  type Props = {
    books: LibraryBookDto[];
    selectedBookId?: string | null;
    isLoading?: boolean;
    disabledReason?: string | null;
    viewMode?: LibraryViewMode;
    onSelect?: (book: LibraryBookDto) => void;
    onOpen?: (book: LibraryBookDto) => void;
    onHide?: (book: LibraryBookDto) => void;
    onToggleView?: (mode: LibraryViewMode) => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let {
    books,
    selectedBookId = null,
    isLoading = false,
    disabledReason = null,
    viewMode = LIBRARY_VIEW_MODE.LIST,
    onSelect,
    onOpen,
    onHide,
    onToggleView,
    t,
  }: Props = $props();

  const formatUpdatedAt = (iso: string) => {
    const parsed = new Date(iso);
    if (Number.isNaN(parsed.getTime())) {
      return t("settings.unknownBook");
    }

    return parsed.toLocaleDateString();
  };

  const formatProgress = (progress: number) => `${Math.round(progress)}%`;

  let coverErrorByBookId = $state<Record<string, boolean>>({});

  const isAbsolutePath = (value: string) => /^[a-zA-Z]:[\\/]/.test(value) || value.startsWith("/");

  const normalizeFileProtocolPath = (value: string) => {
    const withoutScheme = value.replace(/^file:\/\//i, "");
    const normalized = /^[a-zA-Z]:[\\/]/.test(withoutScheme)
      ? withoutScheme
      : withoutScheme.replace(/^\/+/, "");

    try {
      return decodeURIComponent(normalized);
    } catch {
      return normalized;
    }
  };

  const normalizeCoverSource = (coverPath: string | null) => {
    if (!coverPath) {
      return null;
    }

    const trimmed = coverPath.trim();
    if (trimmed.length === 0) {
      return null;
    }

    if (/^(https?:|data:|blob:|asset:)/i.test(trimmed)) {
      return trimmed;
    }

    if (trimmed.startsWith("file://")) {
      return convertFileSrc(normalizeFileProtocolPath(trimmed));
    }

    if (isAbsolutePath(trimmed)) {
      return convertFileSrc(trimmed);
    }

    return trimmed;
  };

  const resolveCoverSrc = (book: LibraryBookDto) => {
    if (coverErrorByBookId[book.id]) {
      return null;
    }

    return normalizeCoverSource(book.coverPath);
  };

  const markCoverAsFailed = (bookId: string) => {
    coverErrorByBookId = {
      ...coverErrorByBookId,
      [bookId]: true,
    };
  };

</script>

<section class="rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm">
  <div class="mb-4 flex items-center justify-between">
    <h2 class="text-lg font-semibold text-[var(--color-primary)]">{t("library.title")}</h2>
    <div class="inline-flex rounded-lg border border-[color:var(--color-border)] bg-[var(--color-background)] p-1">
      <button
        type="button"
        class={`rounded px-3 py-1 text-xs font-medium ${viewMode === LIBRARY_VIEW_MODE.LIST ? "bg-[var(--color-surface)] text-[var(--color-primary)]" : "text-[var(--color-text-muted)]"}`}
        onclick={() => onToggleView?.(LIBRARY_VIEW_MODE.LIST)}
      >
        {t("library.list")}
      </button>
      <button
        type="button"
        class={`rounded px-3 py-1 text-xs font-medium ${viewMode === LIBRARY_VIEW_MODE.GRID ? "bg-[var(--color-surface)] text-[var(--color-primary)]" : "text-[var(--color-text-muted)]"}`}
        onclick={() => onToggleView?.(LIBRARY_VIEW_MODE.GRID)}
      >
        {t("library.grid")}
      </button>
    </div>
  </div>

  {#if disabledReason}
    <div class="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
      {disabledReason}
    </div>
  {:else if isLoading}
    <p class="text-sm text-[var(--color-text-muted)]">{t("library.loading")}</p>
  {:else if books.length === 0}
    <p class="text-sm text-[var(--color-text-muted)]">{t("library.empty")}</p>
  {:else if viewMode === LIBRARY_VIEW_MODE.GRID}
    <ul class="grid grid-cols-1 gap-3 sm:grid-cols-2">
      {#each books as book}
        <li class={`min-w-0 rounded-xl border p-3 ${selectedBookId === book.id ? "border-[var(--color-primary)] bg-[color:color-mix(in_srgb,var(--color-primary)_10%,var(--color-surface))]" : "border-[color:var(--color-border)] bg-[var(--color-surface)]"}`}>
          <div class="mb-2 flex items-start justify-end">
            <DropMenu position="bottom-right">
              {#snippet trigger()}
                <button
                  type="button"
                  class="rounded-md border border-[color:var(--color-border)] px-2 py-1 text-xs text-[var(--color-text-muted)] hover:bg-[color:var(--color-border)]"
                  aria-label={t("library.optionsFor", { title: book.title })}
                >
                  ...
                </button>
              {/snippet}
              <button
                type="button"
                class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
                onclick={() => onHide?.(book)}
              >
                {t("library.hide")}
              </button>
            </DropMenu>
          </div>
          <button type="button" class="w-full text-left" onclick={() => onSelect?.(book)}>
            {#if resolveCoverSrc(book)}
              <img
                src={resolveCoverSrc(book) ?? undefined}
                alt={`Cover for ${book.title}`}
                class="mb-2 h-32 w-full rounded object-cover"
                onerror={() => markCoverAsFailed(book.id)}
              />
            {:else}
              <div class="mb-2 flex h-32 w-full items-center justify-center rounded bg-[var(--color-background)] text-sm text-[var(--color-text-muted)]">
                {t("library.noCover")}
              </div>
            {/if}
            <p class="line-clamp-2 min-w-0 break-words text-sm font-semibold text-[var(--color-primary)]">{book.title}</p>
            <p class="line-clamp-1 min-w-0 truncate text-xs text-[var(--color-text-muted)]">{book.author || t("app.unknownAuthor")}</p>
            <p class="mt-2 min-w-0 truncate text-xs text-[var(--color-text-muted)]">{formatProgress(book.progressPercentage)} · {book.minutesRead} {t("library.min")}</p>
          </button>
          <button
            type="button"
            class="mt-3 w-full rounded-md bg-[var(--color-primary)] px-3 py-1.5 text-xs font-medium text-[var(--color-background)] hover:opacity-90"
            onclick={() => onOpen?.(book)}
          >
            {t("library.open")}
          </button>
        </li>
      {/each}
    </ul>
  {:else}
    <ul class="space-y-2">
      {#each books as book}
        <li class={`min-w-0 rounded-xl border p-3 ${selectedBookId === book.id ? "border-[var(--color-primary)] bg-[color:color-mix(in_srgb,var(--color-primary)_10%,var(--color-surface))]" : "border-[color:var(--color-border)] bg-[var(--color-surface)]"}`}>
          <div class="flex items-start gap-3">
            <button type="button" class="min-w-0 flex-1 text-left" onclick={() => onSelect?.(book)}>
              <div class="flex items-start gap-3">
                {#if resolveCoverSrc(book)}
                  <img
                    src={resolveCoverSrc(book) ?? undefined}
                    alt={`Cover for ${book.title}`}
                    class="h-14 w-10 rounded object-cover"
                    onerror={() => markCoverAsFailed(book.id)}
                  />
                {:else}
                  <div class="flex h-14 w-10 items-center justify-center rounded bg-[var(--color-background)] text-[10px] uppercase text-[var(--color-text-muted)]">
                    {t("library.cover")}
                  </div>
                {/if}
                <div class="min-w-0 flex-1">
                  <p class="line-clamp-2 min-w-0 break-words text-sm font-semibold text-[var(--color-primary)]">{book.title}</p>
                  <p class="truncate text-xs text-[var(--color-text-muted)]">{book.author || t("app.unknownAuthor")} · {book.format.toUpperCase()}</p>
                  <p class="mt-1 min-w-0 truncate text-xs text-[var(--color-text-muted)]">
                    {book.currentPage}/{book.totalPages || "-"} · {formatProgress(book.progressPercentage)} · {t("library.updated")} {formatUpdatedAt(book.updatedAt)}
                  </p>
                </div>
              </div>
            </button>
            <button
              type="button"
              class="rounded-md bg-[var(--color-primary)] px-3 py-1.5 text-xs font-medium text-[var(--color-background)] hover:opacity-90"
              onclick={() => onOpen?.(book)}
            >
              {t("library.open")}
            </button>
            <DropMenu position="bottom-right">
              {#snippet trigger()}
                <button
                  type="button"
                  class="rounded-md border border-[color:var(--color-border)] px-2 py-1 text-xs text-[var(--color-text-muted)] hover:bg-[color:var(--color-border)]"
                  aria-label={t("library.optionsFor", { title: book.title })}
                >
                  ...
                </button>
              {/snippet}
              <button
                type="button"
                class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
                onclick={() => onHide?.(book)}
              >
                {t("library.hide")}
              </button>
            </DropMenu>
          </div>
        </li>
      {/each}
    </ul>
  {/if}
</section>
