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

  function handleInput(e: Event) {
    const target = e.target as HTMLInputElement;
    value = Number(target.value);
    onchange?.({ value });
  }

  // Calculate percentage for thumb position
  const percentage = $derived(((value - min) / (max - min)) * 100);
</script>

<div class="visual-slider">
  {#if label}
    <div class="slider-header">
      <label class="mb-1 block text-xs text-zinc-600">{label}</label>
      {#if showValue}
        <span class="slider-value">{value}{unit}</span>
      {/if}
    </div>
  {/if}
  
  <div class="slider-container">
    <input
      type="range"
      {min}
      {max}
      {step}
      {value}
      oninput={handleInput}
      class="slider-input"
      style="--progress: {percentage}%; --track-gradient: {gradientStyles[gradient]};"
    />
  </div>
</div>

<style>
  .visual-slider {
    width: 100%;
  }

  .slider-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4px;
  }

  .slider-value {
    font-size: 12px;
    font-weight: 500;
    color: var(--color-primary);
  }

  .slider-container {
    position: relative;
    width: 100%;
  }

  .slider-input {
    -webkit-appearance: none;
    appearance: none;
    width: 100%;
    height: 8px;
    border-radius: 4px;
    background: var(--track-gradient);
    outline: none;
    cursor: pointer;
  }

  /* Slider track background */
  .slider-input::-webkit-slider-runnable-track {
    width: 100%;
    height: 8px;
    border-radius: 4px;
    background: var(--track-gradient);
  }

  /* Slider thumb */
  .slider-input::-webkit-slider-thumb {
    -webkit-appearance: none;
    appearance: none;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--color-surface, white);
    border: 2px solid var(--color-primary);
    cursor: pointer;
    margin-top: -5px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
    transition: transform 0.15s, box-shadow 0.15s;
  }

  .slider-input::-webkit-slider-thumb:hover {
    transform: scale(1.1);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }

  /* Firefox support */
  .slider-input::-moz-range-thumb {
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--color-surface, white);
    border: 2px solid var(--color-primary);
    cursor: pointer;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
    transition: transform 0.15s, box-shadow 0.15s;
  }

  .slider-input::-moz-range-thumb:hover {
    transform: scale(1.1);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }

  .slider-input::-moz-range-track {
    width: 100%;
    height: 8px;
    border-radius: 4px;
    background: var(--track-gradient);
  }

  .slider-input:focus {
    outline: none;
  }

  .slider-input:focus::-webkit-slider-thumb {
    box-shadow: 0 0 0 3px var(--color-primary-alpha, rgba(16, 185, 129, 0.3));
  }
</style>