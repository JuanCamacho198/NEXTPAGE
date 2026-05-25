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
  let id = $state(crypto.randomUUID());
</script>

<div class="relative">
  {#if label}
    <label for={id} class="mb-1 block text-xs text-zinc-600">{label}</label>
  {/if}
  
  <div class="flex items-center gap-2">
    <button
      type="button"
      class="h-8 w-8 rounded-lg border-2 border-(--color-border) cursor-pointer transition-transform hover:scale-105"
      style="background-color: {value};"
      onclick={() => (isOpen = !isOpen)}
      aria-label="Select color"
    ></button>
    
    <input
      type="text"
      id={id}
      {value}
      oninput={handleInputChange}
      class="h-8 w-20 rounded border border-(--color-border) px-2 text-xs font-mono"
    />
    
    <input
      type="color"
      {value}
      oninput={handleInputChange}
      class="h-8 w-8 cursor-pointer rounded border-none bg-transparent"
    />
  </div>

  {#if isOpen}
    <div class="absolute top-full left-0 z-50 mt-1 rounded-lg bg-(--color-surface,white) border border-(--color-border) shadow-lg p-2">
      <div class="grid grid-cols-5 gap-1">
        {#each presetColors as color}
          <button
            type="button"
            class="w-6 h-6 rounded border-2 border-transparent cursor-pointer transition-transform duration-150 hover:scale-115"
            class:!border-(--color-primary)={value === color}
            class:shadow-[0_0_0_2px_var(--color-background)]={value === color}
            style="background-color: {color};"
            onclick={() => selectColor(color)}
            aria-label="Select {color}"
          ></button>
        {/each}
      </div>
    </div>
  {/if}
</div>