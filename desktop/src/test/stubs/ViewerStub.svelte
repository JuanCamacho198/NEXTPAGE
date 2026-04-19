<script lang="ts">
  type Props = {
    filePath?: string;
  };

  let { filePath = "" }: Props = $props();
</script>

<div data-testid="viewer-stub">{filePath}</div>
