import * as pdfjsLib from "pdfjs-dist";

export interface SafeTextLayerParams {
  container: HTMLElement;
  viewport: { scale: number; width: number; height: number };
  textContentSource:
    | { on: (event: string, callback: (...args: unknown[]) => void) => void }
    | Promise<unknown>;
}

export interface SafeTextLayerUpdateParams {
  viewport: { scale: number; width: number; height: number };
}

/**
 * Thin wrapper around pdfjs-dist's `TextLayer` that:
 * 1. Sets CSS custom properties on the container (`--scale-factor`, `--total-scale-factor`,
 *    `--scale-round-x`, `--scale-round-y`) on construction and update.
 * 2. Sets container `width` / `height` from viewport dimensions.
 * 3. Fixes the DPR-inflation bug (`#scale` multiplied by `OutputScale.pixelRatio`
 *    in pdfjs-dist v5.6.205) by patching `instance['#scale']` back to `viewport.scale`.
 *
 * The fallback `pdfjsLib.renderTextLayer?.()` path is NOT wrapped — it remains
 * PdfViewer's responsibility.
 */
export class SafeTextLayer {
  private instance: Record<string, unknown> | null = null;
  private cancelled = false;
  private container: HTMLElement;

  constructor(params: SafeTextLayerParams) {
    this.container = params.container;
    this.cancelled = false;

    // Position the container for overlay rendering
    this.container.style.position = "absolute";
    this.container.style.left = "0";
    this.container.style.top = "0";

    // Set CSS custom properties and dimensions BEFORE creating the TextLayer
    this.setLayerCssVars(params.viewport);
    this.setLayerDimensions(params.viewport);

    // Create the raw TextLayer instance
    this.instance = new (pdfjsLib as unknown as { TextLayer: new (args: Record<string, unknown>) => Record<string, unknown> }).TextLayer({
      container: params.container,
      viewport: params.viewport,
      textContentSource: params.textContentSource,
    });

    // Fix DPR inflation that pdfjs-dist applies in its constructor
    this.fixScale(params.viewport);
  }

  /**
   * Render the text layer. Delegates to `instance.render()`.
   */
  async render(): Promise<void> {
    const instance = this.instance;
    if (instance && typeof (instance as unknown as { render: () => Promise<void> }).render === "function") {
      await (instance as unknown as { render: () => Promise<void> }).render();
    }
  }

  /**
   * Update the text layer with a new viewport (e.g. after zoom).
   * Re-sets CSS vars + dimensions, delegates to `instance.update()`,
   * then re-patches `#scale` because the update path also inflates DPR.
   */
  update(params: SafeTextLayerUpdateParams): void {
    if (this.cancelled) return;

    this.setLayerCssVars(params.viewport);
    this.setLayerDimensions(params.viewport);

    const instance = this.instance;
    if (instance && typeof (instance as unknown as { update: (p: SafeTextLayerUpdateParams) => void }).update === "function") {
      (instance as unknown as { update: (p: SafeTextLayerUpdateParams) => void }).update(params);
    }

    // update() re-inflates #scale with DPR, so we patch again
    this.fixScale(params.viewport);
  }

  /**
   * Cancel the text layer and mark as cancelled. Subsequent update() calls
   * are no-ops.
   */
  cancel(): void {
    this.cancelled = true;
    const instance = this.instance;
    if (instance && typeof (instance as unknown as { cancel: () => void }).cancel === "function") {
      (instance as unknown as { cancel: () => void }).cancel();
    }
  }

  // ── Private helpers ───────────────────────────────────────

  private setLayerCssVars(viewport: { scale: number }): void {
    this.container.style.setProperty("--scale-factor", String(viewport.scale));
    this.container.style.setProperty("--total-scale-factor", String(viewport.scale));
    this.container.style.setProperty("--scale-round-x", "1px");
    this.container.style.setProperty("--scale-round-y", "1px");
  }

  private setLayerDimensions(viewport: { width: number; height: number }): void {
    this.container.style.width = `${viewport.width}px`;
    this.container.style.height = `${viewport.height}px`;
  }

  private fixScale(viewport: { scale: number }): void {
    if (this.instance && this.instance["#scale"] !== undefined) {
      this.instance["#scale"] = viewport.scale;
    }
  }
}
