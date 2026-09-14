<script lang="ts">
  import DropMenu from '$lib/shared/ui/navigation/DropMenu.svelte';
</script>

<DropMenu>
  {#snippet trigger()}
    <button type="button" data-testid="dm-trigger">Open menu</button>
  {/snippet}
  <button type="button" data-testid="dm-item" onclick={() => {}}>Option A</button>
</DropMenu>
