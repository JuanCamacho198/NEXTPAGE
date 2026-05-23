import { describe, it, expect } from "vitest";
import { debugState } from "$lib/debug/debugState.svelte";

describe("DebugState", () => {
  it("defaults all properties to false/null/empty", () => {
    expect(debugState.enabled).toBe(false);
    expect(debugState.currentRoute).toBe("");
    expect(debugState.readerInfo).toBeNull();
    expect(debugState.selection).toBeNull();
  });

  it("toggles enabled from false to true", () => {
    debugState.enabled = true;
    expect(debugState.enabled).toBe(true);
    debugState.enabled = false;
    expect(debugState.enabled).toBe(false);
  });

  it("assigns currentRoute correctly", () => {
    debugState.currentRoute = "reader";
    expect(debugState.currentRoute).toBe("reader");
    debugState.currentRoute = "";
  });

  it("assigns selection correctly", () => {
    debugState.selection = { text: "hello world", source: "pdf", rectCount: 3, firstRect: { top: 0, left: 0, width: 100, height: 20 } };
    expect(debugState.selection).toEqual({ text: "hello world", source: "pdf", rectCount: 3, firstRect: { top: 0, left: 0, width: 100, height: 20 } });
    debugState.selection = null;
    expect(debugState.selection).toBeNull();
  });

  it("assigns readerInfo correctly", () => {
    debugState.readerInfo = {
      format: "pdf",
      isTocOpen: true,
      isSearchOpen: false,
      isFullscreen: false,
      pageInfo: "5 / 20",
      scale: 1.5,
    };
    expect(debugState.readerInfo).toEqual({
      format: "pdf",
      isTocOpen: true,
      isSearchOpen: false,
      isFullscreen: false,
      pageInfo: "5 / 20",
      scale: 1.5,
    });
    debugState.readerInfo = null;
    expect(debugState.readerInfo).toBeNull();
  });
});
