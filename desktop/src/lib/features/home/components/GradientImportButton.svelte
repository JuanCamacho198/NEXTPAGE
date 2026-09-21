<script lang="ts">
  import Plus from 'lucide-svelte/icons/plus';
  import type { Icon as LucideIcon } from 'lucide-svelte';

  type Props = {
    label: string;
    onclick: () => void;
    icon?: typeof LucideIcon;
    disabled?: boolean;
  };

  let { label, onclick, icon: ItemIcon = Plus, disabled = false }: Props = $props();
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
  <ItemIcon size={14} strokeWidth={1.8} />
  {label}
</button>
