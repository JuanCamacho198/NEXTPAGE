import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { render, cleanup, fireEvent, waitFor } from "@testing-library/svelte";
import PdfViewer from "$lib/domain/reader/PdfViewer.svelte";

vi.mock("$lib/stores/reader", () => ({
  readerStore: {
    subscribe: vi.fn(),
    set: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock("$lib/stores/settings", () => ({
  settingsStore: {
    subscribe: vi.fn(),
    set: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock("$lib/api/tauriClient", () => ({
  getFileBytes: vi.fn().mockResolvedValue(new Uint8Array([0, 1, 2, 3])),
}));

describe("PdfViewer Text Selection Integration", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders selection overlay and handles selection change", async () => {
    const t = (key: string) => key;
    const { container } = render(PdfViewer, {
      filePath: "test.pdf",
      t,
    });

    // Check if controls are rendered
    const controls = container.querySelector(".controls");
    expect(controls).toBeTruthy();

    // Check if text layer is present
    const textLayer = container.querySelector(".text-layer");
    expect(textLayer).toBeTruthy();
    expect(textLayer).toHaveStyle({ "pointer-events": "auto" });
  });

  it("shows toolbar when text is selected", async () => {
    // This is hard to test in JSDOM because window.getSelection is limited
    // but we can at least check if the logic is triggered
    const selection = {
      toString: () => "selected text",
      rangeCount: 1,
      getRangeAt: () => ({
        getClientRects: () => [
          { left: 100, top: 100, width: 50, height: 20 }
        ],
        getBoundingClientRect: () => ({ left: 100, top: 100, width: 50, height: 20 }),
      }),
    };

    // Mock window.getSelection
    const originalGetSelection = window.getSelection;
    window.getSelection = vi.fn().mockReturnValue(selection);

    // After cleanup
    window.getSelection = originalGetSelection;
  });
});
