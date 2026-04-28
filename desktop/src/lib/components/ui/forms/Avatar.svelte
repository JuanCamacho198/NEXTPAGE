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

<div class="avatar-container {sizeClasses[size]} {className}">
  {#if src && !isBroken}
    <img
      {src}
      alt={name}
      class="avatar-image"
      onerror={handleError}
    />
  {:else}
    <div class="avatar-fallback">
      {initials}
    </div>
  {/if}
</div>

<style>
  .avatar-container {
    position: relative;
    flex-shrink: 0;
  }

  .avatar-image,
  .avatar-fallback {
    width: 100%;
    height: 100%;
    border-radius: 9999px;
    border: 1px solid var(--color-border);
  }

  .avatar-image {
    object-fit: cover;
    display: block;
  }

  .avatar-fallback {
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    color: var(--color-primary);
    background: color-mix(in srgb, var(--color-primary) 12%, var(--color-surface));
  }
</style>