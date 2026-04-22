<script lang="ts">
  type Props = {
    placeholder?: string;
    value?: string;
    debounce?: number;
    icon?: boolean;
    onsearch?: (value: { value: string }) => void;
  };

  let {
    placeholder = "Search...",
    value = $bindable(""),
    debounce = 300,
    icon = true,
    onsearch,
  }: Props = $props();

  let debounceTimer: ReturnType<typeof setTimeout> | undefined = $state(undefined);

  function handleInput(e: Event) {
    const target = e.target as HTMLInputElement;
    value = target.value;
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      onsearch?.({ value });
    }, debounce);
  }

  function clear() {
    value = "";
    clearTimeout(debounceTimer);
    onsearch?.({ value: "" });
  }
</script>

<div class="relative">
  {#if icon}
    <svg
      class="absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-text-muted)]"
      fill="none"
      stroke="currentColor"
      viewBox="0 0 24 24"
    >
      <path
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="2"
        d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
      />
    </svg>
  {/if}
  <input
    type="text"
    {placeholder}
    class="h-8 w-[140px] sm:w-40 rounded-lg border border-[color:var(--color-border)] bg-[var(--color-background)] pl-9 pr-8 text-sm text-[var(--color-text)] placeholder-[var(--color-text-muted)] focus:border-[var(--color-primary)] focus:outline-none"
    {value}
    oninput={handleInput}
  />
  {#if value}
    <button
      type="button"
      class="absolute right-2 top-1/2 -translate-y-1/2 text-[var(--color-text-muted)] hover:text-[var(--color-text)]"
      aria-label="Clear search"
      onclick={clear}
    >
      <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          d="M6 18L18 6M6 6l12 12"
        />
      </svg>
    </button>
  {/if}
</div>
