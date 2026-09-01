<script lang="ts">
  import type { Snippet } from 'svelte';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    continueSection?: Snippet;
    shelfSection?: Snippet;
  };

  let { t, continueSection, shelfSection }: Props = $props();
</script>

<div class="grid grid-cols-1 xl:grid-cols-3 gap-4">
  <!-- Columna Izquierda: Continuar Lectura -->
  <div class="xl:col-span-1 space-y-4">
    <div
      class="rounded-(--radius-xl) border border-(--color-border) bg-(--color-surface) shadow-(--shadow-soft) p-5"
    >
      <div>
        {#if continueSection}
          {@render continueSection()}
        {:else}
          <div class="min-h-[80px]"></div>
        {/if}
      </div>
    </div>
  </div>

  <!-- Columna Derecha: Mi Estantería -->
  <div class="xl:col-span-2 space-y-4">
    <div
      class="rounded-(--radius-xl) border border-(--color-border) bg-(--color-surface) shadow-(--shadow-soft) p-5 h-full"
    >
      <div class="mb-4 flex items-center justify-between">
        <h3 class="text-base font-semibold tracking-tight text-(--color-primary)">
          {t('home.myShelf')}
        </h3>
      </div>
      <div>
        {#if shelfSection}
          {@render shelfSection()}
        {:else}
          <p class="text-sm text-(--color-text-muted)">{t('home.myShelfPlaceholder')}</p>
        {/if}
      </div>
    </div>
  </div>
</div>
