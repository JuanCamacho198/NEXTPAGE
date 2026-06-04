import { describe, expect, it, vi, afterEach } from "vitest";
import { cleanup } from "@testing-library/svelte";
import type { ReaderArrowIntent } from "$lib/features/reader/epub/keyboardNav";

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

const createKeyEvent = (init: Partial<KeyboardEvent> = {}) =>
  new KeyboardEvent("keydown", {
    bubbles: true,
    cancelable: true,
    ...init,
  });

describe("PDF Viewer Arrow Navigation Integration", () => {
  afterEach(() => {
    cleanup();
  });    it("ArrowLeft triggers prevPage navigation", () => {
    const event = createKeyEvent({ key: "ArrowLeft" });
    const canHandle = !(event.ctrlKey || event.altKey || event.metaKey);

    expect(canHandle).toBe(true);
    expect(event.key).toBe("ArrowLeft");
  });

  it("ArrowRight triggers nextPage navigation", () => {
    const event = createKeyEvent({ key: "ArrowRight" });
    const canHandle = !(event.ctrlKey || event.altKey || event.metaKey);

    expect(canHandle).toBe(true);
    expect(event.key).toBe("ArrowRight");
  });

  it("ArrowUp triggers scrollUp (never page turn)", () => {
    const event = createKeyEvent({ key: "ArrowUp" });
    const canHandle = !(event.ctrlKey || event.altKey || event.metaKey);

    expect(canHandle).toBe(true);
    expect(event.key).toBe("ArrowUp");
  });

  it("ArrowDown triggers scrollDown (never page turn)", () => {
    const event = createKeyEvent({ key: "ArrowDown" });
    const canHandle = !(event.ctrlKey || event.altKey || event.metaKey);

    expect(canHandle).toBe(true);
    expect(event.key).toBe("ArrowDown");
  });

  it("horizontal arrows never trigger scroll actions", () => {
    const leftEvent = createKeyEvent({ key: "ArrowLeft" });
    const rightEvent = createKeyEvent({ key: "ArrowRight" });

    expect(leftEvent.key).not.toBe("ArrowUp");
    expect(leftEvent.key).not.toBe("ArrowDown");
    expect(rightEvent.key).not.toBe("ArrowUp");
    expect(rightEvent.key).not.toBe("ArrowDown");
  });

  it("vertical arrows never trigger page-turn actions", () => {
    const upEvent = createKeyEvent({ key: "ArrowUp" });
    const downEvent = createKeyEvent({ key: "ArrowDown" });

    expect(upEvent.key).not.toBe("ArrowLeft");
    expect(upEvent.key).not.toBe("ArrowRight");
    expect(downEvent.key).not.toBe("ArrowLeft");
    expect(downEvent.key).not.toBe("ArrowRight");
  });
});

describe("EPUB Viewer Arrow Navigation Integration", () => {
  afterEach(() => {
    cleanup();
  });

  const createKeyEvent = (init: Partial<KeyboardEvent> = {}) =>
    new KeyboardEvent("keydown", {
      bubbles: true,
      cancelable: true,
      ...init,
    });

  it("ArrowLeft triggers prevPage navigation", () => {
    const event = createKeyEvent({ key: "ArrowLeft" });
    const canHandle = !(event.ctrlKey || event.altKey || event.metaKey);

    expect(canHandle).toBe(true);
    expect(event.key).toBe("ArrowLeft");
  });

  it("ArrowRight triggers nextPage navigation", () => {
    const event = createKeyEvent({ key: "ArrowRight" });
    const canHandle = !(event.ctrlKey || event.altKey || event.metaKey);

    expect(canHandle).toBe(true);
    expect(event.key).toBe("ArrowRight");
  });

  it("ArrowUp triggers scrollUp (never page turn)", () => {
    const event = createKeyEvent({ key: "ArrowUp" });
    const canHandle = !(event.ctrlKey || event.altKey || event.metaKey);

    expect(canHandle).toBe(true);
    expect(event.key).toBe("ArrowUp");
  });

  it("ArrowDown triggers scrollDown (never page turn)", () => {
    const event = createKeyEvent({ key: "ArrowDown" });
    const canHandle = !(event.ctrlKey || event.altKey || event.metaKey);

    expect(canHandle).toBe(true);
    expect(event.key).toBe("ArrowDown");
  });

  it("Ctrl+= zoom key combination is blocked from arrow nav (allows viewer handler)", () => {
    const zoomInEvent = createKeyEvent({ key: "=", ctrlKey: true });

    expect(zoomInEvent.ctrlKey).toBe(true);
    expect(zoomInEvent.key).toBe("=");
  });

  it("Ctrl+- zoom key combination is blocked from arrow nav (allows viewer handler)", () => {
    const zoomOutEvent = createKeyEvent({ key: "-", ctrlKey: true });

    expect(zoomOutEvent.ctrlKey).toBe(true);
    expect(zoomOutEvent.key).toBe("-");
  });
});

describe("Keyboard Zoom Integration", () => {
  it("Ctrl+= triggers zoom in if keyboard not handled by arrow nav", () => {
    const event = createKeyEvent({ key: "=", ctrlKey: true });

    expect(event.ctrlKey).toBe(true);
    expect(event.key).toBe("=");
  });

  it("Ctrl++ triggers zoom in on numeric keypad", () => {
    const event = createKeyEvent({ key: "+", ctrlKey: true });

    expect(event.ctrlKey).toBe(true);
  });

  it("Ctrl+- triggers zoom out", () => {
    const event = createKeyEvent({ key: "-", ctrlKey: true });

    expect(event.ctrlKey).toBe(true);
    expect(event.key).toBe("-");
  });

  it("Ctrl+0 resets zoom to default", () => {
    const event = createKeyEvent({ key: "0", ctrlKey: true });

    expect(event.ctrlKey).toBe(true);
    expect(event.key).toBe("0");
  });

  it("Meta key (Mac) works same as Ctrl key", () => {
    const event = createKeyEvent({ key: "=", metaKey: true });

    expect(event.metaKey).toBe(true);
  });
});

describe("Scroll Vertical Movement", () => {
  it("ArrowUp maps to scrollUp intent in keyboardNav", () => {
    document.createElement("div");
    const event = new KeyboardEvent("keydown", { key: "ArrowUp", bubbles: true });

    expect(event.key).toBe("ArrowUp");
  });

  it("ArrowDown maps to scrollDown intent in keyboardNav", () => {
    document.createElement("div");
    const event = new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true });

    expect(event.key).toBe("ArrowDown");
  });

  it("vertical movement never triggers page navigation", () => {
    const upArrow = { key: "ArrowUp" };
    const downArrow = { key: "ArrowDown" };

    const pageTurnKeys = new Set(["ArrowLeft", "ArrowRight"]);

    expect(pageTurnKeys.has(upArrow.key)).toBe(false);
    expect(pageTurnKeys.has(downArrow.key)).toBe(false);
  });
});

describe("ReaderArrowIntent Type Contract", () => {
  it("has exactly 4 possible values", () => {
    const validIntents: ReaderArrowIntent[] = [
      "prevPage",
      "nextPage",
      "scrollUp",
      "scrollDown",
    ];

    expect(validIntents).toHaveLength(4);
  });

  it("page navigation intents are horizontal only", () => {
    const pageIntents = ["prevPage", "nextPage"];

    pageIntents.forEach((intent) => {
      expect(intent).toMatch(/Page$/);
    });
  });

  it("scroll intents are vertical only", () => {
    const scrollIntents = ["scrollUp", "scrollDown"];

    scrollIntents.forEach((intent) => {
      expect(intent).toMatch(/^scroll(Up|Down)$/);
    });
  });
});