<script lang="ts">
  import type { Snippet } from "svelte";

  type Tab = {
    id: string;
    label: string;
  };

  type Props = {
    tabs: Tab[];
    active?: string;
    children?: Snippet;
    class?: string;
  };

  let {
    tabs,
    active = $bindable(tabs[0]?.id ?? ""),
    children,
    class: className = ""
  }: Props = $props();

  function handleTabClick(tabId: string) {
    active = tabId;
  }
</script>

<div class="{className}">
  <div class="flex border-b border-[var(--color-border)]" role="tablist">
    {#each tabs as tab}
      <button
        type="button"
        class="relative px-4 py-2.5 text-sm font-medium transition-colors hover:text-[var(--color-primary)] {active === tab.id ? 'text-[var(--color-primary)]' : 'text-[var(--color-muted)]'}"
        role="tab"
        aria-selected={active === tab.id}
        onclick={() => handleTabClick(tab.id)}
      >
        {tab.label}
        {#if active === tab.id}
          <div class="absolute bottom-0 left-0 right-0 h-0.5 bg-[var(--color-primary)]"></div>
        {/if}
      </button>
    {/each}
  </div>

  {#if children}
    <div class="pt-4" role="tabpanel">
      {@render children()}
    </div>
  {/if}
</div>