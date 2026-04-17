<script lang="ts">
  import type { Snippet } from "svelte";

  type Props = {
    children?: Snippet;
    onclick?: () => void;
    variant?: "primary" | "secondary" | "danger" | "ghost";
    size?: "sm" | "md" | "lg";
    disabled?: boolean;
    class?: string;
  };

  let {
    children,
    onclick,
    variant = "primary",
    size = "md",
    disabled = false,
    class: className = ""
  }: Props = $props();

  const baseClasses = "inline-flex items-center justify-center font-medium rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed";

  const variants = {
    primary: "bg-[var(--color-primary)] text-[var(--color-background)] hover:opacity-90 focus:ring-[var(--color-primary)]",
    secondary: "bg-[var(--color-surface)] text-[var(--color-primary)] border border-[var(--color-border)] hover:bg-[color:var(--color-border)] focus:ring-[var(--color-primary)]",
    danger: "bg-[var(--color-error)] text-[var(--color-background)] hover:opacity-90 focus:ring-[var(--color-error)]",
    ghost: "bg-transparent text-[var(--color-primary)] hover:bg-[color:var(--color-border)] focus:ring-[var(--color-primary)]"
  };

  const sizes = {
    sm: "px-3 py-1.5 text-sm",
    md: "px-4 py-2 text-base",
    lg: "px-6 py-3 text-lg"
  };
</script>

<button
  type="button"
  class="{baseClasses} {variants[variant]} {sizes[size]} {className}"
  {disabled}
  {onclick}
>
  {@render children?.()}
</button>
