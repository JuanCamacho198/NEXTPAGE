<script lang="ts">
  import type { Snippet } from "svelte";
  import type { MessageKey } from "../../i18n";
  import Panel from "../ui/Panel.svelte";

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    continueSection?: Snippet;
    shelfSection?: Snippet;
  };

  let { t, continueSection, shelfSection }: Props = $props();
</script>

<div class="space-y-4">
  <Panel title={t("home.continueReadingTitle")} hint={t("home.continueReadingHint")} padding="none">
    {#snippet children()}
      {#if continueSection}
        {@render continueSection()}
      {:else}
        <p class="p-4 text-sm text-[var(--color-text-muted)]">{t("home.continueReadingPlaceholder")}</p>
      {/if}
    {/snippet}
  </Panel>

  <Panel title={t("home.myShelfTitle")} hint={t("home.myShelfHint")} padding="none">
    {#snippet children()}
      {#if shelfSection}
        {@render shelfSection()}
      {:else}
        <p class="p-4 text-sm text-[var(--color-text-muted)]">{t("home.myShelfPlaceholder")}</p>
      {/if}
    {/snippet}
  </Panel>
</div>
