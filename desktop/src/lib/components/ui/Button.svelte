<script lang="ts">
  import type { Snippet } from "svelte";

  type ButtonProps = {
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
  }: ButtonProps = $props();

  let isPressed = $state(false);

  const baseClasses = "inline-flex items-center justify-center font-sans font-medium rounded-lg transition-all duration-150 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-[var(--color-bg-app)] disabled:opacity-50 disabled:cursor-not-allowed";
  
  const pressStyles = isPressed ? "scale-[0.96] shadow-inner" : "scale-100 shadow-sm";

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
  class="{baseClasses} {pressStyles} {variants[variant]} {sizes[size]} {className}"
  {disabled}
  onclick={onclick}
  onmousedown={() => isPressed = true}
  onmouseup={() => isPressed = false}
  onmouseleave={() => isPressed = false}
>
  {@render children?.()}
</button>
