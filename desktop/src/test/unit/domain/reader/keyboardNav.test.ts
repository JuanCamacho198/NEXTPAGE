import { describe, expect, it } from "vitest";
import { canHandleReaderArrowNav } from "$lib/domain/reader/keyboardNav";

const createKeyboardEvent = (target: EventTarget | null, overrides: Partial<KeyboardEvent> = {}) => {
  return {
    target,
    defaultPrevented: false,
    altKey: false,
    ctrlKey: false,
    metaKey: false,
    ...overrides,
  } as KeyboardEvent;
};

describe("keyboardNav", () => {
  it("allows arrow nav on generic containers", () => {
    const target = document.createElement("div");
    expect(canHandleReaderArrowNav(createKeyboardEvent(target))).toBe(true);
  });

  it("blocks arrow nav inside input-like controls", () => {
    expect(canHandleReaderArrowNav(createKeyboardEvent(document.createElement("input")))).toBe(false);
    expect(canHandleReaderArrowNav(createKeyboardEvent(document.createElement("textarea")))).toBe(false);
    expect(canHandleReaderArrowNav(createKeyboardEvent(document.createElement("select")))).toBe(false);
    expect(canHandleReaderArrowNav(createKeyboardEvent(document.createElement("button")))).toBe(false);
  });

  it("blocks arrow nav inside contentEditable and textbox roles", () => {
    const editable = document.createElement("div");
    editable.contentEditable = "true";

    const roleTextbox = document.createElement("div");
    roleTextbox.setAttribute("role", "textbox");

    expect(canHandleReaderArrowNav(createKeyboardEvent(editable))).toBe(false);
    expect(canHandleReaderArrowNav(createKeyboardEvent(roleTextbox))).toBe(false);
  });

  it("blocks when modifier keys are active or event already handled", () => {
    const target = document.createElement("div");
    expect(canHandleReaderArrowNav(createKeyboardEvent(target, { ctrlKey: true }))).toBe(false);
    expect(canHandleReaderArrowNav(createKeyboardEvent(target, { metaKey: true }))).toBe(false);
    expect(canHandleReaderArrowNav(createKeyboardEvent(target, { defaultPrevented: true }))).toBe(false);
  });
});
