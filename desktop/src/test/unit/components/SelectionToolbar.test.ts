import { render } from "@testing-library/svelte";
import { describe, expect, it } from "vitest";
import SelectionToolbar from "$lib/features/reader/components/SelectionToolbar.svelte";

const t = (key: string) => key;

describe("SelectionToolbar", () => {
  it("positions within container using viewerSpace bounds", () => {
    const { container } = render(SelectionToolbar, {
      selectedText: "Selection text",
      selectionBounds: { left: 40, top: 120, right: 160, bottom: 140 },
      containerRect: { left: 100, top: 200, width: 320, height: 480 },
      onCopy: () => undefined,
      onNote: () => undefined,
      onDismiss: () => undefined,
      onColorSelect: () => undefined,
      t,
    });

    const toolbar = container.querySelector(".selection-toolbar");
    expect(toolbar).toBeTruthy();
    expect(toolbar?.getAttribute("style")).toContain("left: 116px");
    expect(toolbar?.getAttribute("style")).toContain("top: 248px");
  });

  it("clamps toolbar inside container width", () => {
    const { container } = render(SelectionToolbar, {
      selectedText: "Selection text",
      selectionBounds: { left: 5, top: 140, right: 25, bottom: 160 },
      containerRect: { left: 20, top: 100, width: 240, height: 400 },
      onCopy: () => undefined,
      onNote: () => undefined,
      onDismiss: () => undefined,
      onColorSelect: () => undefined,
      t,
    });

    const toolbar = container.querySelector(".selection-toolbar");
    expect(toolbar).toBeTruthy();
    expect(toolbar?.getAttribute("style")).toContain("left: 36px");
  });
});
