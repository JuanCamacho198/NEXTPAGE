<script lang="ts">
  type Props = {
    text?: string;
    position?: "top" | "bottom" | "left" | "right";
    children?: import("svelte").Snippet;
  };

  let {
    text = "",
    position = "top",
    children,
  }: Props = $props();

  let isVisible = $state(false);

  const positionClasses = {
    top: "bottom-full left-1/2 -translate-x-1/2 mb-2",
    bottom: "top-full left-1/2 -translate-x-1/2 mt-2",
    left: "right-full top-1/2 -translate-y-1/2 mr-2",
    right: "left-full top-1/2 -translate-y-1/2 ml-2",
  };
</script>

<div
  class="relative inline-block"
  role="button"
  tabindex="0"
  onmouseenter={() => (isVisible = true)}
  onmouseleave={() => (isVisible = false)}
  onfocus={() => (isVisible = true)}
  onblur={() => (isVisible = false)}
>
  {@render children?.()}

  {#if isVisible && text}
    <div
      class="absolute z-50 whitespace-nowrap rounded bg-[var(--color-primary)] px-2 py-1 text-xs text-[var(--color-background)] {positionClasses[position]}"
      role="tooltip"
    >
      {text}
    </div>
  {/if}
</div>