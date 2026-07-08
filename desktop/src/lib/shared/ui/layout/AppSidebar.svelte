<script lang="ts">
  import type { AppRoute } from '$lib/shared/stores/homeState';
  import type { MessageKey } from '../../i18n';
  import ThemeToggle from '$lib/shared/ui/navigation/ThemeToggle.svelte';
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
import { theme, toggleTheme } from '$lib/shared/stores/theme';

  type Props = {
    activeRoute: AppRoute;
    onNavigateHome: () => void;
    onNavigateLibrary: () => void;
    onNavigateStats: () => void;
    onNavigateHighlights: () => void;
    onNavigateSettings: () => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let {
    activeRoute,
    onNavigateHome,
    onNavigateLibrary,
    onNavigateStats,
    onNavigateHighlights,
    onNavigateSettings,
  }: Props = $props();

  let collapsed = $state(false);

  let navItems = $derived([
    { id: 'home', label: 'Inicio', icon: 'home' as const, action: onNavigateHome },
    { id: 'library', label: 'Estantería', icon: 'library' as const, action: onNavigateLibrary },
    { id: 'stats', label: 'Estadísticas', icon: 'stats' as const, action: onNavigateStats },
    {
      id: 'highlights',
      label: 'Notas y resaltados',
      icon: 'highlights' as const,
      action: onNavigateHighlights,
    },
    { id: 'settings', label: 'Ajustes', icon: 'settings' as const, action: onNavigateSettings },
  ]);
</script>

<aside
  class="sticky top-0 h-screen shrink-0 border-r border-(--color-border) bg-[rgba(12,20,32,0.6)] backdrop-blur-xl max-lg:hidden lg:flex lg:flex-col transition-all duration-300"
  class:w-64={!collapsed}
  class:w-18={collapsed}
>
  <div class="flex items-center justify-center p-4 pb-2">
    {#if collapsed}
      <button
        onclick={() => (collapsed = !collapsed)}
        class="flex items-center justify-center rounded-lg p-1.5 text-(--color-text-muted) hover:bg-(--color-panel-accent) hover:text-(--color-primary) transition-colors"
        aria-label="Expandir sidebar"
      >
        <Icon name="chevron-right" size="sm" />
      </button>
    {:else}
      <div class="flex items-center gap-3 w-full">
        <div
          class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-(--color-accent-soft) text-sm font-bold text-(--color-accent-blue) border border-(--color-border-strong)"
        >
          NP
        </div>
        <h1 class="text-base font-bold tracking-tight text-(--color-primary)">
          NextPage<br /><span class="text-xs font-normal text-(--color-text-muted)">Desktop</span>
        </h1>
        <button
          onclick={() => (collapsed = !collapsed)}
          class="ml-auto flex items-center justify-center rounded-lg p-1.5 text-(--color-text-muted) hover:bg-(--color-panel-accent) hover:text-(--color-primary) transition-colors shrink-0"
          aria-label="Colapsar sidebar"
        >
          <Icon name="chevron-left" size="sm" />
        </button>
      </div>
    {/if}
  </div>

  <nav class="flex-1 space-y-1 overflow-y-auto p-4">
    {#each navItems as item}
      <button
        class={`w-full rounded-xl px-3 py-2.5 text-sm font-medium transition-all duration-200 ${
          collapsed
            ? 'flex items-center justify-center'
            : 'flex items-center gap-3'
        } ${
          activeRoute === item.id || (activeRoute === 'home' && item.id === 'home')
            ? 'bg-(--color-accent-blue) text-(--color-background) shadow-(--shadow-glow)'
            : 'text-(--color-text-muted) hover:bg-(--color-panel-accent) hover:text-(--color-primary)'
        }`}
        onclick={item.action}
      >
        <Icon name={item.icon} size="md" title={item.label} />
        {#if !collapsed}
          {item.label}
        {/if}
      </button>
    {/each}
  </nav>

  <div class="p-4 border-t border-(--color-border) flex flex-col gap-2">
    <!-- Theme toggle button -->
    {#if collapsed}
      <button
        onclick={toggleTheme}
        class="flex items-center justify-center rounded-lg size-8 bg-transparent border border-(--color-border) text-(--color-text-muted) hover:bg-(--color-panel-accent) hover:text-(--color-primary) transition-all duration-200 shrink-0"
        aria-label={$theme === 'dark' ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'}
      >
        <Icon name={$theme === 'dark' ? 'moon' : 'sun'} size="sm" />
      </button>
    {:else}
      <ThemeToggle />
    {/if}

    <!-- User section -->
    <button
      class="w-full flex items-center rounded-xl p-3 transition-colors"
      class:justify-between={!collapsed}
      class:justify-center={collapsed}
      class:bg-(--color-surface)={!collapsed}
      class:border={!collapsed}
      class:border-(--color-border)={!collapsed}
      class:hover:border-(--color-border-strong)={!collapsed}
    >
      <div class="flex items-center gap-3">
        <div
          class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-blue-600 text-xs font-bold text-white"
        >
          U
        </div>
        {#if !collapsed}
          <div class="text-left">
            <p class="text-sm font-medium text-(--color-primary)">Usuario</p>
            <p class="text-xs text-(--color-text-muted)">Ver perfil</p>
          </div>
        {/if}
      </div>
    </button>
  </div>
</aside>