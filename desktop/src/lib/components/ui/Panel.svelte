<script lang="ts">
  import type { Snippet } from "svelte";

  type Props = {
    title?: string;
    subtitle?: string;
    hint?: string;
    variant?: "default" | "surface";
    padding?: "none" | "sm" | "md" | "lg";
    actions?: Snippet;
    children?: Snippet;
    class?: string;
  };

  let {
    title,
    subtitle,
    hint,
    variant = "default",
    padding = "md",
    actions,
    children,
    class: className = ""
  }: Props = $props();

  const paddingClasses = {
    none: "",
    sm: "p-3",
    md: "p-4",
    lg: "p-6"
  };

  const variantClasses = {
    default: "bg-[var(--color-surface)]",
    surface: "bg-[var(--color-surface-dim)]"
  };
</script>

<section class="rounded-xl border border-[var(--color-border)] {variantClasses[variant]} shadow-sm {className}">
  {#if title || subtitle || hint || actions}
    <div class="mb-3 flex items-center justify-between gap-2">
      <div class="min-w-0">
        {#if title}
          <h2 class="text-lg font-semibold text-[var(--color-primary)]">{title}</h2>
        {/if}
        {#if subtitle}
          <p class="text-sm text-[var(--color-text-muted)]">{subtitle}</p>
        {/if}
        {#if hint}
          <span class="text-xs text-[var(--color-text-muted)]">{hint}</span>
        {/if}
      </div>
      {#if actions}
        <div class="flex shrink-0 items-center gap-2">
          {@render actions()}
        </div>
      {/if}
    </div>
  {/if}

  <div class={paddingClasses[padding]}>
    {#if children}
      {@render children()}
    {/if}
  </div>
</section>