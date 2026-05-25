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

  function handleError() {
    isBroken = true;
  }
</script>

<div class="relative shrink-0 {sizeClasses[size]} {className}">
  {#if src && !isBroken}
    <img
      {src}
      alt={name}
      class="block size-full rounded-full border border-(--color-border) object-cover"
      onerror={handleError}
    />
  {:else}
    <div
      class="flex size-full items-center justify-center rounded-full border border-(--color-border) font-bold text-(--color-primary)"
      style="background: color-mix(in srgb, var(--color-primary) 12%, var(--color-surface));"
    >
      {initials}
    </div>
  {/if}
</div>