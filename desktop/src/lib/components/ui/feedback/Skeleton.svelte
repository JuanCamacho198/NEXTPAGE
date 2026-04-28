<script lang="ts">
  import type { Snippet } from "svelte";

  type SkeletonProps = {
    variant?: "text" | "circular" | "rectangular" | "rounded" | "card" | "book";
    width?: string;
    height?: string;
    class?: string;
  };

  let {
    variant = "text",
    width,
    height,
    class: className = ""
  }: SkeletonProps = $props();

  const baseShimmer = "animate-pulse bg-gradient-to-r from-[var(--color-border)] via-[var(--color-surface)] to-[var(--color-border)] bg-[length:200%_100%]";

  const variants = {
    text: "rounded h-4",
    circular: "rounded-full",
    rectangular: "rounded-none",
    rounded: "rounded-lg",
    card: "rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)]",
    book: "rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)]"
  };
</script>

{#if variant === "book" || variant === "card"}
  <div class="{variants[variant]} p-4 {className}" style="width: {width ?? '100%'}; height: {height ?? 'auto'};">
    <div class="flex gap-4">
      <div class="{baseShimmer} flex-shrink-0 rounded" style="width: 64px; height: 80px;"></div>
      <div class="flex flex-1 flex-col gap-3 pt-1">
        <div class="{baseShimmer} rounded h-4 w-3/4;"></div>
        <div class="{baseShimmer} rounded h-3 w-1/2;"></div>
        <div class="{baseShimmer} mt-auto rounded h-2 w-1/3;"></div>
      </div>
    </div>
  </div>
{:else}
  <div class="{baseShimmer} {variants[variant]} {className}" style="width: {width ?? '100%'}; height: {height ?? '1em'};"></div>
{/if}