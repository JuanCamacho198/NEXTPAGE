<script lang="ts">
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';

  type Option = { value: string; label: string };

  type Props = {
    options?: Option[];
    initialValue?: string | null;
    placeholder?: string;
    disabled?: boolean;
    class?: string;
    onchange?: (detail: { value: string }) => void;
    onvalue?: (value: string | null) => void;
  };

  let {
    options = [],
    initialValue = null,
    placeholder,
    disabled = false,
    class: className,
    onchange,
    onvalue,
  }: Props = $props();

  // `initialValue` is a seed, not a two-way prop: only the first value counts.
  // svelte-ignore state_referenced_locally
  let value = $state<string | null>(initialValue);

  $effect(() => {
    onvalue?.(value);
  });
</script>

<!-- Mirrors the `bind:value` call sites (LibraryShelfScreen, HighlightsView,
     ReadingStatisticsView, the genre control in ShelfDetailModal) and reports
     every write back so a test can count them. -->
<Dropdown {options} bind:value {placeholder} {disabled} class={className} {onchange} />
