<script lang="ts">
  import type { Snippet } from "svelte";

  type Props = {
    title?: string;
    subtitle?: string;
    variant?: "default" | "surface" | "interactive";
    padding?: "none" | "sm" | "md" | "lg";
    size?: "sm" | "md" | "lg";
    clickable?: boolean;
    media?: Snippet;
    default?: Snippet;
    actions?: Snippet;
    onclick?: () => void;
    class?: string;
  };

  let {
    title,
    subtitle,
    variant = "default",
    padding = "md",
    size = "md",
    clickable = false,
    media,
    default: content,
    actions,
    onclick,
    class: className = ""
  }: Props = $props();

  const paddingClasses: Record<string, string> = {
    none: "",
    sm: "p-3",
    md: "p-4",
    lg: "p-6"
  };

  const sizeClasses: Record<string, string> = {
    sm: "rounded-lg",
    md: "rounded-xl",
    lg: "rounded-2xl"
  };

  const variantClasses: Record<string, string> = {
    default: "",
    surface: "bg-[var(--color-surface-dim)]",
    interactive: "hover:border-[var(--color-primary)] hover:shadow-md transition-all duration-200"
  };

  let baseClasses = $derived(`${sizeClasses[size]} border border-[var(--color-border)] bg-[var(--color-surface)] overflow-hidden`);

  function handleKeydown(e: KeyboardEvent) {
    if (clickable && (e.key === "Enter" || e.key === " ")) {
      e.preventDefault();
      onclick?.();
    }
  }
</script>

{#if clickable}
  <button
    type="button"
    class="{baseClasses} {variantClasses[variant]} cursor-pointer {className}"
    onclick={onclick}
  >
    {#if media}
      <div class="media">
        {@render media()}
      </div>
    {/if}

    <div class={paddingClasses[padding]}>
      {#if title || subtitle}
        <div class="mb-3">
          {#if title}
            <h3 class="text-lg font-semibold text-[var(--color-primary)]">{title}</h3>
          {/if}
          {#if subtitle}
            <p class="mt-1 text-sm text-[var(--color-text-muted)]">{subtitle}</p>
          {/if}
        </div>
      {/if}

      {#if content}
        <div class="content">
          {@render content()}
        </div>
      {/if}
    </div>

    {#if actions}
      <div class="flex items-center gap-2 border-t border-[var(--color-border)] px-4 py-3">
        {@render actions()}
      </div>
    {/if}
  </button>
{:else}
  <div class="{baseClasses} {variantClasses[variant]} {className}">
    {#if media}
      <div class="media">
        {@render media()}
      </div>
    {/if}

    <div class={paddingClasses[padding]}>
      {#if title || subtitle}
        <div class="mb-3">
          {#if title}
            <h3 class="text-lg font-semibold text-[var(--color-primary)]">{title}</h3>
          {/if}
          {#if subtitle}
            <p class="mt-1 text-sm text-[var(--color-text-muted)]">{subtitle}</p>
          {/if}
        </div>
      {/if}

      {#if content}
        <div class="content">
          {@render content()}
        </div>
      {/if}
    </div>

    {#if actions}
      <div class="flex items-center gap-2 border-t border-[var(--color-border)] px-4 py-3">
        {@render actions()}
      </div>
    {/if}
  </div>
{/if}

<style>
  .media :global(img) {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
</style>