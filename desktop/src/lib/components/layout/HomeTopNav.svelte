<script lang="ts">
  import type { Snippet } from "svelte";
  import type { MessageKey } from "../../i18n";

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    activeRoute?: "home" | "highlights" | "settings";
    onNavigateHome?: () => void;
    onNavigateHighlights?: () => void;
    onNavigateSettings?: () => void;
    actions?: Snippet;
  };

  let {
    t,
    activeRoute = "home",
    onNavigateHome,
    onNavigateHighlights,
    onNavigateSettings,
    actions,
  }: Props = $props();
</script>

<header class="rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm">
  <div class="flex flex-wrap items-center justify-between gap-3">
    <div class="flex items-center gap-3">
      <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-[color:color-mix(in_srgb,var(--color-primary)_15%,var(--color-surface))] text-sm font-semibold text-[var(--color-primary)]">
        NP
      </div>
      <div>
        <p class="text-base font-semibold text-[var(--color-primary)]">{t("app.title")}</p>
        <p class="text-xs text-[var(--color-text-muted)]">{t("app.brandPlaceholder")}</p>
      </div>
    </div>

    <nav aria-label={t("app.homeNavLabel")}>
      <ul class="flex items-center gap-2 text-sm">
        <li>
          <button
            type="button"
            class={`rounded-md px-3 py-1.5 font-medium ${activeRoute === "home" ? "bg-[color:color-mix(in_srgb,var(--color-primary)_14%,var(--color-surface))] text-[var(--color-primary)]" : "border border-[color:var(--color-border)] text-[var(--color-text-muted)] hover:bg-[color:var(--color-border)]"}`}
            aria-current={activeRoute === "home" ? "page" : undefined}
            onclick={onNavigateHome}
          >
            {t("app.navBookshelf")}
          </button>
        </li>
        <li>
          <button
            type="button"
            class={`rounded-md px-3 py-1.5 font-medium ${activeRoute === "highlights" ? "bg-[color:color-mix(in_srgb,var(--color-primary)_14%,var(--color-surface))] text-[var(--color-primary)]" : "border border-[color:var(--color-border)] text-[var(--color-text-muted)] hover:bg-[color:var(--color-border)]"}`}
            aria-current={activeRoute === "highlights" ? "page" : undefined}
            onclick={onNavigateHighlights}
          >
            {t("home.highlightsTitle")}
          </button>
        </li>
        <li>
          <button
            type="button"
            class={`rounded-md px-3 py-1.5 font-medium ${activeRoute === "settings" ? "bg-[color:color-mix(in_srgb,var(--color-primary)_14%,var(--color-surface))] text-[var(--color-primary)]" : "border border-[color:var(--color-border)] text-[var(--color-text-muted)] hover:bg-[color:var(--color-border)]"}`}
            aria-current={activeRoute === "settings" ? "page" : undefined}
            onclick={onNavigateSettings}
          >
            {t("app.settings")}
          </button>
        </li>
      </ul>
    </nav>

    <div class="flex items-center gap-2">
      {@render actions?.()}
    </div>
  </div>
</header>
