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

<header class="overflow-hidden rounded-[28px] border border-[color:var(--color-border-strong)] bg-[linear-gradient(135deg,rgba(16,28,44,0.92),rgba(12,20,34,0.88))] p-5 shadow-[var(--shadow-panel)] backdrop-blur-xl">
  <div class="flex flex-col gap-5">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div class="flex min-w-0 items-center gap-4">
        <div class="flex h-12 w-12 items-center justify-center rounded-2xl border border-[color:var(--color-border-strong)] bg-[radial-gradient(circle_at_top,rgba(148,236,255,0.34),rgba(73,212,255,0.08)_55%,transparent_70%)] text-sm font-semibold tracking-[0.24em] text-[var(--color-primary)] shadow-[inset_0_1px_0_rgba(255,255,255,0.18)]">
          NP
        </div>
        <div class="min-w-0">
          <p class="text-[11px] font-semibold uppercase tracking-[0.32em] text-[var(--color-text-link)] opacity-80">{t("app.brandPlaceholder")}</p>
          <p class="mt-1 text-xl font-semibold tracking-tight text-[var(--color-primary)]">{t("app.title")}</p>
          <p class="mt-1 max-w-2xl text-sm text-[var(--color-secondary)]">{t("home.heroDescription")}</p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        {@render actions?.()}
      </div>
    </div>

    <div class="flex flex-wrap items-center justify-between gap-3">
      <nav aria-label={t("app.homeNavLabel")}>
        <ul class="flex flex-wrap items-center gap-2 text-sm">
        <li>
          <button
            type="button"
            class={`rounded-full px-4 py-2 font-medium transition-all ${activeRoute === "home" ? "border border-[color:var(--color-border-strong)] bg-[var(--color-accent-soft)] text-[var(--color-primary)] shadow-[inset_0_1px_0_rgba(255,255,255,0.12)]" : "border border-[color:var(--color-border)] text-[var(--color-text-muted)] hover:border-[color:var(--color-border-strong)] hover:bg-[rgba(255,255,255,0.03)] hover:text-[var(--color-primary)]"}`}
            aria-current={activeRoute === "home" ? "page" : undefined}
            onclick={onNavigateHome}
          >
            {t("app.navBookshelf")}
          </button>
        </li>
        <li>
          <button
            type="button"
            class={`rounded-full px-4 py-2 font-medium transition-all ${activeRoute === "highlights" ? "border border-[color:var(--color-border-strong)] bg-[var(--color-accent-soft)] text-[var(--color-primary)] shadow-[inset_0_1px_0_rgba(255,255,255,0.12)]" : "border border-[color:var(--color-border)] text-[var(--color-text-muted)] hover:border-[color:var(--color-border-strong)] hover:bg-[rgba(255,255,255,0.03)] hover:text-[var(--color-primary)]"}`}
            aria-current={activeRoute === "highlights" ? "page" : undefined}
            onclick={onNavigateHighlights}
          >
            {t("home.highlightsTitle")}
          </button>
        </li>
        <li>
          <button
            type="button"
            class={`rounded-full px-4 py-2 font-medium transition-all ${activeRoute === "settings" ? "border border-[color:var(--color-border-strong)] bg-[var(--color-accent-soft)] text-[var(--color-primary)] shadow-[inset_0_1px_0_rgba(255,255,255,0.12)]" : "border border-[color:var(--color-border)] text-[var(--color-text-muted)] hover:border-[color:var(--color-border-strong)] hover:bg-[rgba(255,255,255,0.03)] hover:text-[var(--color-primary)]"}`}
            aria-current={activeRoute === "settings" ? "page" : undefined}
            onclick={onNavigateSettings}
          >
            {t("app.settings")}
          </button>
        </li>
        </ul>
      </nav>
      <div class="flex flex-wrap items-center gap-3 text-xs text-[var(--color-text-muted)]">
        <span>{t("home.heroEyebrow")}</span>
      </div>
    </div>
  </div>
</header>
