<script lang="ts">
  import DropMenu from '$lib/shared/ui/navigation/DropMenu.svelte';

  type Props = {
    position?: 'bottom-left' | 'bottom-right';
    onPick?: (value: string) => void;
  };

  let { position = 'bottom-right', onPick }: Props = $props();
</script>

<!-- Mirrors the `HighlightsView` call-site shape: the trigger is a caller-owned
     button and the items sit inside a plain layout wrapper rather than as
     direct children of the menu content. -->
<DropMenu {position}>
  {#snippet trigger()}
    <button
      type="button"
      class="w-8 h-8 flex items-center justify-center rounded-md border border-transparent"
      aria-label="Opciones">…</button
    >
  {/snippet}
  <div class="flex flex-col">
    <button type="button" onclick={() => onPick?.('copy')}>Copy text</button>
    <button type="button" onclick={() => onPick?.('view')}>View in book</button>
  </div>
</DropMenu>
