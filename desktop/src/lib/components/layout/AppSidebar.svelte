<script lang="ts">
  import type { AppRoute } from "../../stores/homeState";
  import type { MessageKey } from "../../i18n";
  import ThemeToggle from "../ui/navigation/ThemeToggle.svelte";
  import Icon from "../ui/navigation/Icon.svelte";

  type Props = {
    activeRoute: AppRoute;
    onNavigateHome: () => void;
    onNavigateLibrary: () => void;
    onNavigateStats: () => void;
    onNavigateHighlights: () => void;
    onNavigateSettings: () => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let { activeRoute, onNavigateHome, onNavigateLibrary, onNavigateStats, onNavigateHighlights, onNavigateSettings, t }: Props = $props();

  let navItems = $derived([
    { id: "home", label: "Inicio", icon: "home" as const, action: onNavigateHome },
    { id: "library", label: "Estantería", icon: "library" as const, action: onNavigateLibrary },
    { id: "stats", label: "Estadísticas", icon: "stats" as const, action: onNavigateStats },
    { id: "highlights", label: "Notas y resaltados", icon: "highlights" as const, action: onNavigateHighlights },
    { id: "settings", label: "Ajustes", icon: "settings" as const, action: onNavigateSettings },
  ]);
</script>

<aside class="sticky top-0 h-screen w-64 flex-shrink-0 border-r border-(--color-border) bg-[rgba(12,20,32,0.6)] backdrop-blur-xl flex flex-col hidden lg:flex">
  <div class="p-6 pb-2">
    <div class="flex items-center gap-3">
      <div class="flex h-10 w-10 items-center justify-center rounded-full bg-(--color-accent-soft) text-sm font-bold text-(--color-accent-blue) border border-(--color-border-strong)">
        NP
      </div>
      <h1 class="text-base font-bold tracking-tight text-(--color-primary)">NextPage<br/><span class="text-xs font-normal text-(--color-text-muted)">Desktop</span></h1>
    </div>
  </div>

  <nav class="flex-1 space-y-1 overflow-y-auto p-4">
    {#each navItems as item}
      <button
        class={`w-full flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-all duration-200 ${
          activeRoute === item.id || (activeRoute === 'home' && item.id === 'home')
            ? "bg-(--color-accent-blue) text-(--color-background) shadow-(--shadow-glow)"
            : "text-(--color-text-muted) hover:bg-(--color-panel-accent) hover:text-(--color-primary)"
        }`}
        onclick={item.action}
      >
        <Icon name={item.icon} size="md" title={item.label} />
        {item.label}
      </button>
    {/each}
  </nav>

  <div class="p-4 border-t border-(--color-border) flex flex-col gap-2">
    <!-- Theme toggle button -->
    <ThemeToggle />

    <!-- User section -->
    <button class="w-full flex items-center justify-between rounded-xl p-3 bg-(--color-surface) border border-(--color-border) hover:border-(--color-border-strong) transition-colors">
      <div class="flex items-center gap-3">
        <div class="flex h-8 w-8 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white">
          U
        </div>
        <div class="text-left">
          <p class="text-sm font-medium text-(--color-primary)">Usuario</p>
          <p class="text-xs text-(--color-text-muted)">Ver perfil</p>
        </div>
      </div>
    </button>
  </div>
</aside>
