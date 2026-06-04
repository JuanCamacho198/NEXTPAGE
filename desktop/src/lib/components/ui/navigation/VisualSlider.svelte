<script lang="ts">
  type Props = {
    value?: number;
    min?: number;
    max?: number;
    step?: number;
    label?: string;
    showValue?: boolean;
    unit?: string;
    gradient?: "brightness" | "contrast" | "size";
    onchange?: (value: { value: number }) => void;
  };

  let {
    value = 100,
    min = 0,
    max = 100,
    step = 1,
    label,
    showValue = true,
    unit = "%",
    gradient = "brightness",
    onchange,
  }: Props = $props();

  // Generate gradient based on type
  const gradientStyles = {
    brightness: "linear-gradient(to right, #1a1a1a 0%, #ffffff 100%)",
    contrast: "linear-gradient(to right, #666 0%, #000 50%, #fff 100%)",
    size: "linear-gradient(to right, var(--color-surface) 0%, var(--color-primary) 100%)",
  };

  function handleInput(e: Event): void {
    const target = e.target as HTMLInputElement;
    value = Number(target.value);
    onchange?.({ value });
  }

  // Calculate percentage for thumb position
  const percentage = $derived(((value - min) / (max - min)) * 100);
  let id = $state(crypto.randomUUID());
</script>

<div class="w-full">
  {#if label}
    <div class="flex justify-between items-center mb-1">
      <label for={id} class="mb-1 block text-xs text-zinc-600">{label}</label>
      {#if showValue}
        <span class="text-xs font-medium text-(--color-primary)">{value}{unit}</span>
      {/if}
    </div>
  {/if}
  
  <div class="relative w-full">
    <input
      type="range"
      id={id}
      {min}
      {max}
      {step}
      {value}
      oninput={handleInput}
      class="w-full h-2 rounded-lg bg-gradient-to-r outline-none cursor-pointer appearance-none"
      style="--progress: {percentage}%; --track-gradient: {gradientStyles[gradient]};"
    />
  </div>
</div>