<script lang="ts">
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';

  type Option = { value: string; label: string };

  type Props = {
    options?: Option[];
    value?: string | null;
    onchange?: (detail: { value: string }) => void;
  };

  let { options = [], value = null, onchange }: Props = $props();
</script>

<!-- The `option` snippet is public API with no live call site, so this stub is
     the only consumer of that path: it renders the per-item snippet the way a
     caller would, one custom node per option. -->
<Dropdown {options} {value} {onchange}>
  {#snippet option(opt)}
    <span data-testid="custom-option">{opt.value}:{opt.label}</span>
  {/snippet}
</Dropdown>
