<script lang="ts">
  type Props = {
    src?: string;
    name?: string;
    size?: "sm" | "md" | "lg";
    class?: string;
  };

  let {
    src = "",
    name = "",
    size = "md",
    class: className = "",
  }: Props = $props();

  let isBroken = $state(false);

  const sizeClasses = {
    sm: "w-8 h-8 text-xs",
    md: "w-10 h-10 text-sm",
    lg: "w-14 h-14 text-base",
  };

  const initials = $derived(
    name
      .split(" ")
      .map((n) => n[0])
      .join("")
      .toUpperCase()
      .slice(0, 2)
  );

  function handleError(): void {
    isBroken = true;
  }
</script>

<div class="relative shrink-0 {sizeClasses[size]} {className}">
  {#if src && !isBroken}
    <img
      {src}
      alt={name}
      class="w-full h-full rounded-full border border-(--color-border) object-fit block"
      onerror={handleError}
    />
  {:else}
    <div class="w-full h-full rounded-full border border-(--color-border) flex items-center justify-center font-bold text-(--color-primary) bg-[color-mix(in_srgb,var(--color-primary)_12%,var(--color-surface))]">
      {initials}
    </div>
  {/if}
</div>