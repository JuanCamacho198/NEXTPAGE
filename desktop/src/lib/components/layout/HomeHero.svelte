<script lang="ts">
  import type { Snippet } from "svelte";
  import type { MessageKey } from "../../i18n";
  import Button from "../ui/Button.svelte";

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    actions?: Snippet;
  };

  let { t, actions }: Props = $props();
</script>

<section class="relative overflow-hidden rounded-[24px] border border-[color:var(--color-border)] bg-[linear-gradient(180deg,rgba(18,30,47,0.92),rgba(12,20,32,0.84))] p-8 shadow-[var(--shadow-soft)] backdrop-blur-xl">
  <!-- Decorative background glows -->
  <div class="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-[var(--color-accent-blue)] opacity-[0.08] blur-[80px]"></div>
  <div class="pointer-events-none absolute -bottom-20 -left-20 h-48 w-48 rounded-full bg-blue-500 opacity-[0.05] blur-[60px]"></div>

  <div class="relative z-10 flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
    <div class="flex items-center gap-5">
      <div class="flex h-16 w-16 items-center justify-center rounded-[18px] bg-[var(--color-accent-soft)] shadow-[var(--shadow-glow)] border border-[color:var(--color-border-strong)]">
        <span class="text-xl font-bold text-[var(--color-accent-blue)]">NP</span>
      </div>
      <div>
        <h2 class="text-2xl font-bold tracking-tight text-[var(--color-primary)]">Hola, Usuario 👋</h2>
        <p class="mt-1 max-w-xl text-sm leading-relaxed text-[var(--color-text-muted)]">
          Aquí tienes un resumen de tu progreso de lectura. Importa nuevos libros o retoma donde lo dejaste.
        </p>
      </div>
    </div>
    
    <div class="flex items-center gap-3">
      {#if actions}
        {@render actions()}
      {/if}
    </div>
  </div>
</section>
