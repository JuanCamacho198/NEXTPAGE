<script lang="ts">
  import Icon from '$lib/shared/ui/navigation/Icon.svelte';
  import type { IconName } from '$lib/shared/ui/navigation/Icon.svelte';

  type Props = {
    label: string;
    onclick: () => void;
    icon?: IconName;
    disabled?: boolean;
  };

  let { label, onclick, icon = 'add', disabled = false }: Props = $props();
</script>

<!--
  Gradient import button (AD-6, PR #2). The gradient is a background-image,
  so it is applied inline via `var(--gradient-import)` — Tailwind v4's
  `bg-(--var)` compiles to background-color, which rejects gradient values.
-->
<button
  type="button"
  {onclick}
  {disabled}
  class="inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-(--color-background) focus-visible:ring-2 ring-(--color-accent) disabled:opacity-50"
  style="background-image: var(--gradient-import)"
>
  <Icon name={icon} size="sm" />
  {label}
</button>
