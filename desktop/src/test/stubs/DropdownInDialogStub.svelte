<script lang="ts">
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';
  import Modal from '$lib/shared/ui/layout/Modal.svelte';

  type Option = { value: string; label: string };

  type Props = {
    options?: Option[];
    placeholder?: string;
    onchange?: (detail: { value: string }) => void;
    onvalue?: (value: string | null) => void;
  };

  let { options = [], placeholder, onchange, onvalue }: Props = $props();

  let open = $state(true);
  let value = $state<string | null>(null);

  $effect(() => {
    onvalue?.(value);
  });
</script>

<!-- Mirrors the two Dropdowns that live inside an open `ShelfDetailModal`
     (`w-full`, `placeholder`, `bind:value`), so the in-dialog interaction is
     covered in jsdom and not only in the real-browser gate. -->
<Modal bind:open title="Edit metadata" size="md">
  <Dropdown {options} bind:value {placeholder} class="w-full" {onchange} />
</Modal>
