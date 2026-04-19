<script lang="ts">
  import type { Snippet } from "svelte";
  import type { MessageKey } from "../../i18n";

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    continueSection?: Snippet;
    shelfSection?: Snippet;
  };

  let { t, continueSection, shelfSection }: Props = $props();
</script>

<section class="space-y-4">
  <article class="rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm">
    <div class="mb-3 flex items-center justify-between gap-2">
      <h2 class="text-lg font-semibold text-[var(--color-primary)]">{t("home.continueReadingTitle")}</h2>
      <span class="text-xs text-[var(--color-text-muted)]">{t("home.continueReadingHint")}</span>
    </div>
    {#if continueSection}
      {@render continueSection()}
    {:else}
      <p class="text-sm text-[var(--color-text-muted)]">{t("home.continueReadingPlaceholder")}</p>
    {/if}
  </article>

  <article class="rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm">
    <div class="mb-3 flex items-center justify-between gap-2">
      <h2 class="text-lg font-semibold text-[var(--color-primary)]">{t("home.myShelfTitle")}</h2>
      <span class="text-xs text-[var(--color-text-muted)]">{t("home.myShelfHint")}</span>
    </div>
    {#if shelfSection}
      {@render shelfSection()}
    {:else}
      <p class="text-sm text-[var(--color-text-muted)]">{t("home.myShelfPlaceholder")}</p>
    {/if}
  </article>
</section>
