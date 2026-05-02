<script lang="ts">
  import type { Snippet } from "svelte";
  import type { MessageKey } from "$lib/shared/i18n";
  import { Panel } from "$lib/shared/ui";

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    continueSection?: Snippet;
    shelfSection?: Snippet;
  };

  let { t, continueSection, shelfSection }: Props = $props();
</script>

<div class="grid grid-cols-1 xl:grid-cols-3 gap-6">
  <!-- Columna Izquierda: Continuar Lectura -->
  <div class="xl:col-span-1 space-y-4">
    <div class="rounded-[24px] border border-[color:var(--color-border)] bg-[var(--color-surface)] shadow-[var(--shadow-soft)] p-5">
      <div class="mb-4 flex items-center justify-between">
        <h3 class="text-base font-semibold tracking-tight text-[var(--color-primary)]">Continuar lectura</h3>
        <button class="text-xs font-medium text-[var(--color-accent-blue)] hover:underline">Ver todo</button>
      </div>
      <div class="space-y-4">
        {#if continueSection}
          {@render continueSection()}
        {:else}
          <p class="text-sm text-[var(--color-text-muted)]">{t("home.continueReadingPlaceholder")}</p>
        {/if}
      </div>
    </div>
  </div>

  <!-- Columna Derecha: Mi Estantería -->
  <div class="xl:col-span-2 space-y-4">
    <div class="rounded-[24px] border border-[color:var(--color-border)] bg-[var(--color-surface)] shadow-[var(--shadow-soft)] p-5 h-full">
      <div class="mb-4 flex items-center justify-between">
        <h3 class="text-base font-semibold tracking-tight text-[var(--color-primary)]">Mi estantería</h3>
      </div>
      <div>
        {#if shelfSection}
          {@render shelfSection()}
        {:else}
          <p class="text-sm text-[var(--color-text-muted)]">{t("home.myShelfPlaceholder")}</p>
        {/if}
      </div>
    </div>
  </div>
</div>
