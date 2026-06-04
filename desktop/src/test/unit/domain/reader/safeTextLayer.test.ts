import { describe, expect, it, vi, beforeEach } from "vitest";

// ── Mock pdfjs-dist TextLayer simulating DPR-inflation bug ──
const { mockTextLayer } = vi.hoisted(() => {
  // NOTE: must be a `function`, not an arrow — arrow functions lack [[Construct]]
  // and are rejected by `new TextLayer(...)`.
  const ml = vi.fn().mockImplementation(
    function ({ container, viewport }: { container: HTMLElement; viewport: { scale: number; width: number; height: number } }) {
      // Simulate pdfjs-dist v5.6.205 DPR inflation: #scale multiplied by 2
      // AND setLayerDimensions writing container width/height
      const instance: Record<string, unknown> = {
        "#scale": viewport.scale * 2,
        render: vi.fn().mockResolvedValue(undefined),
        update: vi.fn().mockImplementation(function (this: Record<string, unknown>, opts: { viewport: { scale: number } }) {
          this["#scale"] = opts.viewport.scale * 2; // re-inflates on update
        }),
        cancel: vi.fn(),
      };
      container.style.width = `${viewport.width}px`;
      container.style.height = `${viewport.height}px`;
      return instance;
    },
  );
  return { mockTextLayer: ml };
});

vi.mock("pdfjs-dist", () => ({
  TextLayer: mockTextLayer,
  GlobalWorkerOptions: { workerSrc: "" },
  getDocument: vi.fn(),
}));

// ── SUT —──────────────────────────────────────────────────
import { SafeTextLayer } from "$lib/features/reader/pdf/safeTextLayer";

// ── Helpers ───────────────────────────────────────────────
function createViewport(overrides: Partial<{ scale: number; width: number; height: number }> = {}) {
  return { scale: 1.5, width: 800, height: 1100, ...overrides };
}

function getLastMockInstance(): Record<string, unknown> {
  const results = mockTextLayer.mock.results;
  return results[results.length - 1].value as Record<string, unknown>;
}

// ── Suite ─────────────────────────────────────────────────
describe("SafeTextLayer", () => {
  let container: HTMLDivElement;

  beforeEach(() => {
    container = document.createElement("div");
    vi.clearAllMocks();
  });

  // ── Constructor ──────────────────────────────────────────
  describe("constructor", () => {
    it("sets position absolute, left 0, top 0 on container", () => {
      new SafeTextLayer({ container, viewport: createViewport(), textContentSource: {} as unknown as import("$lib/features/reader/pdf/safeTextLayer").SafeTextLayerParams["textContentSource"] });

      expect(container.style.position).toBe("absolute");
      // jsdom coerces unitless 0 → "0px"; real browsers preserve "0"
      expect(container.style.left).toBe("0px");
      expect(container.style.top).toBe("0px");
    });

    it("sets CSS vars --scale-factor and --total-scale-factor to viewport.scale", () => {
      new SafeTextLayer({ container, viewport: createViewport({ scale: 1.5 }), textContentSource: {} as any });

      expect(container.style.getPropertyValue("--scale-factor")).toBe("1.5");
      expect(container.style.getPropertyValue("--total-scale-factor")).toBe("1.5");
    });

    it("sets CSS vars --scale-round-x and --scale-round-y to 1px", () => {
      new SafeTextLayer({ container, viewport: createViewport(), textContentSource: {} as unknown as import("$lib/features/reader/pdf/safeTextLayer").SafeTextLayerParams["textContentSource"] });

      expect(container.style.getPropertyValue("--scale-round-x")).toBe("1px");
      expect(container.style.getPropertyValue("--scale-round-y")).toBe("1px");
    });

    it("sets container width and height from viewport dimensions", () => {
      new SafeTextLayer({ container, viewport: createViewport({ width: 800, height: 1100 }), textContentSource: {} as any });

      expect(container.style.width).toBe("800px");
      expect(container.style.height).toBe("1100px");
    });

    it("fixes DPR-inflated #scale back to viewport.scale", () => {
      new SafeTextLayer({ container, viewport: createViewport({ scale: 1.5 }), textContentSource: {} as any });

      const instance = getLastMockInstance();
      expect(instance["#scale"]).toBe(1.5);
    });

    it("calls TextLayer constructor with container, viewport and textContentSource", () => {
      const tcs = { on: vi.fn() };
      new SafeTextLayer({ container, viewport: createViewport(), textContentSource: tcs });

      expect(mockTextLayer).toHaveBeenCalledTimes(1);
      expect(mockTextLayer).toHaveBeenCalledWith({ container, viewport: createViewport(), textContentSource: tcs });
    });
  });

  // ── render() ─────────────────────────────────────────────
  describe("render", () => {
    it("delegates to instance.render() and awaits its promise", async () => {
      const layer = new SafeTextLayer({ container, viewport: createViewport(), textContentSource: {} as any });

      await layer.render();

      const instance = getLastMockInstance();
      expect(instance.render).toHaveBeenCalledTimes(1);
    });
  });

  // ── update() ─────────────────────────────────────────────
  describe("update", () => {
    it("re-sets CSS vars for the new viewport", () => {
      const layer = new SafeTextLayer({ container, viewport: createViewport({ scale: 1.5 }), textContentSource: {} as any });
      const newVp = createViewport({ scale: 2.0, width: 1067, height: 1467 });

      layer.update({ viewport: newVp });

      expect(container.style.getPropertyValue("--scale-factor")).toBe("2");
      expect(container.style.getPropertyValue("--total-scale-factor")).toBe("2");
      expect(container.style.getPropertyValue("--scale-round-x")).toBe("1px");
      expect(container.style.getPropertyValue("--scale-round-y")).toBe("1px");
    });

    it("re-sets container dimensions for the new viewport", () => {
      const layer = new SafeTextLayer({ container, viewport: createViewport({ width: 800, height: 1100 }), textContentSource: {} as any });
      const newVp = createViewport({ width: 1067, height: 1467 });

      layer.update({ viewport: newVp });

      expect(container.style.width).toBe("1067px");
      expect(container.style.height).toBe("1467px");
    });

    it("re-patches DPR-inflated #scale after update", () => {
      const layer = new SafeTextLayer({ container, viewport: createViewport({ scale: 1.5 }), textContentSource: {} as any });
      const newVp = createViewport({ scale: 2.0 });

      layer.update({ viewport: newVp });

      const instance = getLastMockInstance();
      expect(instance["#scale"]).toBe(2.0);
    });

    it("delegates to instance.update() with the new viewport", () => {
      const layer = new SafeTextLayer({ container, viewport: createViewport(), textContentSource: {} as any });
      const newVp = createViewport({ scale: 2.0 });

      layer.update({ viewport: newVp });

      const instance = getLastMockInstance();
      expect(instance.update).toHaveBeenCalledTimes(1);
      expect(instance.update).toHaveBeenCalledWith({ viewport: newVp });
    });
  });

  // ── cancel() ─────────────────────────────────────────────
  describe("cancel", () => {
    it("delegates to instance.cancel()", () => {
      const layer = new SafeTextLayer({ container, viewport: createViewport(), textContentSource: {} as any });

      layer.cancel();

      const instance = getLastMockInstance();
      expect(instance.cancel).toHaveBeenCalledTimes(1);
    });

    it("makes subsequent update() a no-op (cancelled guard)", () => {
      const layer = new SafeTextLayer({
        container,
        viewport: createViewport({ scale: 1.5, width: 800, height: 1100 }),
        textContentSource: {} as any,
      });
      const instance = getLastMockInstance();
      layer.cancel();

      const newVp = createViewport({ scale: 2.0, width: 1067, height: 1467 });
      layer.update({ viewport: newVp });

      expect(container.style.getPropertyValue("--scale-factor")).toBe("1.5");
      expect(container.style.width).toBe("800px");
      expect(instance.update).not.toHaveBeenCalled();
    });
  });
});
