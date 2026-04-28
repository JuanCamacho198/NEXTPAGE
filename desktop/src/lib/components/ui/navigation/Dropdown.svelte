<script lang="ts">
  import type { Snippet } from "svelte";

  export type Option = {
    value: string;
    label: string;
  };

  type Props = {
    options?: Option[];
    value?: string | null;
    placeholder?: string;
    disabled?: boolean;
    onchange?: (detail: { value: string }) => void;
    trigger?: Snippet;
    option?: Snippet<[Option]>;
  };

  let {
    options = [],
    value = $bindable(null),
    placeholder = "Select...",
    disabled = false,
    onchange,
    trigger,
    option,
  }: Props = $props();

  let isOpen = $state(false);

  function toggle() {
    if (!disabled) {
      isOpen = !isOpen;
    }
  }

  function select(opt: Option) {
    value = opt.value;
    isOpen = false;
    onchange?.({ value: opt.value });
  }

  function handleClickOutside(node: HTMLElement) {
    const handle = (e: MouseEvent) => {
      if (node && !node.contains(e.target as Node)) {
        isOpen = false;
      }
    };
    document.addEventListener("click", handle, true);
    return {
      destroy() {
        document.removeEventListener("click", handle, true);
      }
    };
  }

  const selectedLabel = $derived(
    options.find((o) => o.value === value)?.label ?? placeholder
  );
</script>

<div class="relative inline-block" use:handleClickOutside>
  <button
    type="button"
    class="inline-flex items-center justify-between rounded-lg border border-[color:var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)] disabled:cursor-not-allowed disabled:opacity-50"
    onclick={toggle}
    {disabled}
  >
    {#if trigger}
      {@render trigger()}
    {:else}
      <span>{selectedLabel}</span>
      <svg
        class="ml-2 h-4 w-4"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          d="M19 9l-7 7-7-7"
        />
      </svg>
    {/if}
  </button>

  {#if isOpen}
    <div
      class="absolute z-10 mt-1 w-full min-w-[160px] rounded-md bg-[var(--color-surface)] shadow-lg ring-1 ring-[var(--color-border)]"
    >
      <ul class="py-1">
        {#each options as opt}
          <li>
            <button
              type="button"
              class="w-full px-3 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
              onclick={() => select(opt)}
            >
              {#if option}
                {@render option(opt)}
              {:else}
                {opt.label}
              {/if}
            </button>
          </li>
        {/each}
      </ul>
    </div>
  {/if}
</div>