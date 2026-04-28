<script lang="ts">
  type Props = {
    value?: string;
    onchange?: (value: { value: string }) => void;
    label?: string;
  };

  let {
    value = "#33bbff",
    onchange,
    label,
  }: Props = $props();

  let isOpen = $state(false);

  const presetColors = [
    "#33bbff", // blue (default)
    "#ff6b6b", // red
    "#4ecdc4", // teal
    "#ffe66d", // yellow
    "#95e1d3", // mint
    "#f38181", // coral
    "#aa96da", // lavender
    "#fcbad3", // pink
    "#a8d8ea", // light blue
    "#00d2d3", // cyan
  ];

  function selectColor(color: string) {
    value = color;
    onchange?.({ value: color });
    isOpen = false;
  }

  function handleInputChange(e: Event) {
    const target = e.target as HTMLInputElement;
    value = target.value;
    onchange?.({ value: target.value });
  }
</script>

<div class="color-picker">
  {#if label}
    <label class="mb-1 block text-xs text-zinc-600">{label}</label>
  {/if}
  
  <div class="flex items-center gap-2">
    <button
      type="button"
      class="h-8 w-8 rounded-lg border-2 border-[var(--color-border)] cursor-pointer transition-transform hover:scale-105"
      style="background-color: {value};"
      onclick={() => (isOpen = !isOpen)}
      aria-label="Select color"
    ></button>
    
    <input
      type="text"
      {value}
      oninput={handleInputChange}
      class="h-8 w-20 rounded border border-[var(--color-border)] px-2 text-xs font-mono"
    />
    
    <input
      type="color"
      {value}
      oninput={handleInputChange}
      class="h-8 w-8 cursor-pointer rounded border-none bg-transparent"
    />
  </div>

  {#if isOpen}
    <div class="color-picker-dropdown">
      <div class="color-grid">
        {#each presetColors as color}
          <button
            type="button"
            class="color-swatch"
            class:selected={value === color}
            style="background-color: {color};"
            onclick={() => selectColor(color)}
            aria-label="Select {color}"
          ></button>
        {/each}
      </div>
    </div>
  {/if}
</div>

<style>
  .color-picker {
    position: relative;
  }

  .color-picker-dropdown {
    position: absolute;
    top: 100%;
    left: 0;
    z-index: 50;
    margin-top: 4px;
    border-radius: 8px;
    background: var(--color-surface, white);
    border: 1px solid var(--color-border);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    padding: 8px;
  }

  .color-grid {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 4px;
  }

  .color-swatch {
    width: 24px;
    height: 24px;
    border-radius: 4px;
    border: 2px solid transparent;
    cursor: pointer;
    transition: transform 0.15s, border-color 0.15s;
  }

  .color-swatch:hover {
    transform: scale(1.15);
  }

  .color-swatch.selected {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 2px var(--color-background);
  }
</style>