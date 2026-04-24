<script lang="ts">
  import type { AppRoute } from "../../stores/homeState";
  import type { MessageKey } from "../../i18n";
  import Button from "../ui/Button.svelte";

  type Props = {
    activeRoute: AppRoute;
    onNavigateHome: () => void;
    onNavigateHighlights: () => void;
    onNavigateSettings: () => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { activeRoute, onNavigateHome, onNavigateHighlights, onNavigateSettings, t }: Props = $props();

  let navItems = $derived([
    { id: "home", label: "Inicio", icon: "🏠", action: onNavigateHome },
    { id: "library", label: "Estantería", icon: "📚", action: onNavigateHome }, // Todo: specific route
    { id: "continue", label: "Continuar", icon: "📖", action: onNavigateHome },
    { id: "stats", label: "Estadísticas", icon: "📊", action: onNavigateHome },
    { id: "highlights", label: "Notas y resaltados", icon: "📝", action: onNavigateHighlights },
    { id: "settings", label: "Ajustes", icon: "⚙️", action: onNavigateSettings },
  ]);
</script>

<aside class="sticky top-0 h-screen w-64 flex-shrink-0 border-r border-[color:var(--color-border)] bg-[rgba(12,20,32,0.6)] backdrop-blur-xl flex flex-col hidden lg:flex">
  <div class="p-6 pb-2">
    <div class="flex items-center gap-3">
      <div class="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--color-accent-soft)] text-sm font-bold text-[var(--color-accent-blue)] border border-[color:var(--color-border-strong)]">
        NP
      </div>
      <h1 class="text-base font-bold tracking-tight text-[var(--color-primary)]">NextPage<br/><span class="text-xs font-normal text-[var(--color-text-muted)]">Desktop</span></h1>
    </div>
  </div>

  <nav class="flex-1 space-y-1 overflow-y-auto p-4">
    {#each navItems as item}
      <button
        class={`w-full flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-all duration-200 ${
          activeRoute === item.id || (activeRoute === 'home' && item.id === 'home')
            ? "bg-[var(--color-accent-blue)] text-[var(--color-background)] shadow-[var(--shadow-glow)]"
            : "text-[var(--color-text-muted)] hover:bg-[var(--color-panel-accent)] hover:text-[var(--color-primary)]"
        }`}
        onclick={item.action}
      >
        <span class="text-lg">{item.icon}</span>
        {item.label}
      </button>
    {/each}
  </nav>

  <div class="p-4 border-t border-[color:var(--color-border)]">
    <button class="w-full flex items-center justify-between rounded-xl p-3 bg-[var(--color-surface)] border border-[color:var(--color-border)] hover:border-[color:var(--color-border-strong)] transition-colors">
      <div class="flex items-center gap-3">
        <div class="flex h-8 w-8 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white">
          U
        </div>
        <div class="text-left">
          <p class="text-sm font-medium text-[var(--color-primary)]">Usuario</p>
          <p class="text-xs text-[var(--color-text-muted)]">Ver perfil</p>
        </div>
      </div>
    </button>
  </div>
</aside>
