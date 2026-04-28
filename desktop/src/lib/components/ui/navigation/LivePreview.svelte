<script lang="ts">
  type Props = {
    text?: string;
    themeMode?: "paper" | "sepia" | "night";
    fontSize?: number;
    fontFamily?: string;
    margins?: { top: number; bottom: number; left: number; right: number };
    brightness?: number;
    contrast?: number;
  };

  let {
    text = "",
    themeMode = "paper",
    fontSize = 100,
    fontFamily = "sans",
    margins = { top: 5, bottom: 5, left: 5, right: 5 },
    brightness = 100,
    contrast = 100,
  }: Props = $props();

  // Default sample text (realistic EPUB content)
  const defaultSampleText = `Chapter One

It was a bright cold day in April, and the clocks were striking thirteen. Winston Smith, his chin nuzzled into his breast in an effort to escape the vile wind, slipped quickly through the glass doors of Victory Mansions, though not quickly enough to prevent a swirl of gritty dust from entering along with him.

The hallway smelt of boiled cabbage and old rag mats. At one end of it a coloured poster, too large for indoor display, had been tacked to the wall. It depicted simply an enormous face, more than a metre wide: the face of a man of about forty-five, with a heavy black moustache and ruggedly handsome features.

Winston made for the stairs. It was no use trying the lift. Even at the best of times it was seldom working, and at present the electric current was cut off during daylight hours. It was part of the economy drive in preparation for Hate Week.`;

  const displayText = text || defaultSampleText;

  // Theme colors
  const themeColors = {
    paper: { bg: "#fafafa", text: "#1a1a1a", muted: "#6b7280" },
    sepia: { bg: "#f4ecd8", text: "#5b4636", muted: "#8b7355" },
    night: { bg: "#1a1a1a", text: "#e8e8e8", muted: "#9ca3af" },
  };

  const colors = $derived(themeColors[themeMode]);
  const effectiveFontSize = $derived(Math.round(16 * (fontSize / 100)));
  const effectiveBrightness = $derived(brightness / 100);
  const effectiveContrast = $derived(contrast / 100);
</script>

<div
  class="live-preview"
  style="
    --preview-bg: {colors.bg};
    --preview-text: {colors.text};
    --preview-muted: {colors.muted};
    --preview-font-size: {effectiveFontSize}px;
    --preview-font-family: {fontFamily === 'serif' ? 'Georgia, serif' : fontFamily === 'monospace' ? 'monospace' : 'system-ui, sans-serif'};
    --preview-margin: {margins.top}% {margins.right}% {margins.bottom}% {margins.left}%;
    --preview-brightness: {effectiveBrightness};
    --preview-contrast: {effectiveContrast};
  "
>
  <div class="preview-page">
    <div class="preview-header">
      <span class="preview-chapter">Chapter One</span>
      <span class="preview-page-num">1</span>
    </div>
    <div class="preview-content">
      <p>{displayText.split('\n\n')[0]}</p>
    </div>
    <div class="preview-footer">
      <span class="preview-progress">12%</span>
    </div>
  </div>
</div>

<style>
  .live-preview {
    width: 100%;
    border-radius: 8px;
    overflow: hidden;
    filter: brightness(var(--preview-brightness)) contrast(var(--preview-contrast));
  }

  .preview-page {
    background: var(--preview-bg);
    color: var(--preview-text);
    font-family: var(--preview-font-family);
    font-size: var(--preview-font-size);
    line-height: 1.6;
    padding: var(--preview-margin);
    min-height: 200px;
    display: flex;
    flex-direction: column;
    border: 1px solid var(--color-border);
    border-radius: 4px;
  }

  .preview-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--preview-muted);
    margin-bottom: 12px;
    opacity: 0.7;
  }

  .preview-chapter {
    font-size: 0.75em;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .preview-page-num {
    font-size: 0.75em;
  }

  .preview-content {
    flex: 1;
  }

  .preview-content p {
    margin: 0;
    text-indent: 1.5em;
  }

  .preview-footer {
    display: flex;
    justify-content: flex-end;
    padding-top: 12px;
    border-top: 1px solid var(--preview-muted);
    margin-top: 12px;
    opacity: 0.7;
  }

  .preview-progress {
    font-size: 0.75em;
  }
</style>