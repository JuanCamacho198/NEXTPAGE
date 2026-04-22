<script lang="ts">
  type Props = {
    variant?: "text" | "circular" | "rectangular" | "rounded";
    width?: string;
    height?: string;
    class?: string;
  };

  let {
    variant = "text",
    width,
    height,
    class: className = "",
  }: Props = $props();

  const variantClasses = {
    text: "rounded",
    circular: "rounded-full",
    rectangular: "rounded-none",
    rounded: "rounded-lg",
  };
</script>

<div
  class="animate-pulse bg-[color:var(--color-border)] {variantClasses[variant]} {className}"
  style="width: {width ?? '100%'}; height: {height ?? '1em'};"
></div>