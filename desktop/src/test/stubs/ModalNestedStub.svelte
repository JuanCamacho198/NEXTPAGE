<script lang="ts">
  import Modal from '$lib/shared/ui/layout/Modal.svelte';

  type Props = {
    onouter?: (open: boolean) => void;
    oninner?: (open: boolean) => void;
  };

  let { onouter, oninner }: Props = $props();

  let outer = $state(false);
  let inner = $state(false);

  $effect(() => {
    onouter?.(outer);
  });

  $effect(() => {
    oninner?.(inner);
  });
</script>

<!-- Mirrors `ShelfSection` opening `ShelfDetailModal` and that dialog opening a
     second dialog from inside itself. Each opener focuses itself before writing
     its flag, which is what a real mousedown does, so bits-ui's focus scope has a
     pre-focus element to restore to. -->
<button
  id="open-outer"
  onclick={(e) => {
    e.currentTarget.focus();
    outer = true;
  }}
>
  open outer
</button>

<Modal bind:open={outer} title="Outer dialog" size="lg">
  <button
    id="open-inner"
    onclick={(e) => {
      e.currentTarget.focus();
      inner = true;
    }}
  >
    open inner
  </button>
  <Modal bind:open={inner} title="Inner dialog" size="sm">
    <p>inner body</p>
  </Modal>
</Modal>
