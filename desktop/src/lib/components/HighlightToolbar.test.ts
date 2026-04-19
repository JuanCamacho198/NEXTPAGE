import { fireEvent, render, screen } from "@testing-library/svelte";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import HighlightToolbar from "../domain/reader/HighlightToolbar.svelte";
import { saveHighlight } from "$lib/api/tauriClient";

vi.mock("$lib/api/tauriClient", () => ({
  saveHighlight: vi.fn(),
}));

const mockedSaveHighlight = vi.mocked(saveHighlight);

const t = (key: string, params?: Record<string, string | number>) => {
  const dictionary: Record<string, string> = {
    "highlight.menuAriaLabel": "Selection actions",
    "highlight.selectColor": "Select {{color}} highlight",
    "highlight.save": "Save",
    "highlight.saving": "Saving...",
    "highlight.note": "Note",
    "highlight.cancel": "Cancel",
    "highlight.notePlaceholder": "Write a note for this highlight",
    "highlight.noteInputAriaLabel": "Highlight note",
    "highlight.saveWithNote": "Save note",
    "highlight.selectionUnavailable": "Selection context is no longer available. Please select text again.",
    "highlight.saveFailed": "Could not save highlight.",
    "highlight.noteRequired": "Please write a note before saving.",
    "settings.color.yellow": "Yellow",
    "settings.color.green": "Green",
    "settings.color.blue": "Blue",
    "settings.color.pink": "Pink",
    "settings.color.orange": "Orange",
  };

  const template = dictionary[key] ?? key;
  if (!params) {
    return template;
  }

  return template.replace(/\{\{\s*([\w.-]+)\s*\}\}/g, (_match, token: string) => {
    const value = params[token];
    return value === undefined ? "" : String(value);
  });
};

describe("HighlightToolbar", () => {
  beforeEach(() => {
    mockedSaveHighlight.mockReset();
  });

  it("keeps save highlight flow with selected color", async () => {
    mockedSaveHighlight.mockResolvedValueOnce(undefined);
    const onClose = vi.fn();

    render(HighlightToolbar, {
      selectedText: "Selected quote",
      bookId: "book-1",
      pageNumber: 5,
      cfi: null,
      hasSelectionAnchor: true,
      t,
      onClose,
    });

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Select Blue highlight" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(mockedSaveHighlight).toHaveBeenCalledTimes(1);
    expect(mockedSaveHighlight).toHaveBeenCalledWith(
      expect.objectContaining({
        text: "Selected quote",
        bookId: "book-1",
        pageNumber: 5,
        color: "#bfdbfe",
        note: null,
      }),
    );
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("saves note linked to highlight payload", async () => {
    mockedSaveHighlight.mockResolvedValueOnce(undefined);
    const onClose = vi.fn();

    render(HighlightToolbar, {
      selectedText: "Noted quote",
      bookId: "book-2",
      pageNumber: 9,
      cfi: "epubcfi(/6/2)",
      hasSelectionAnchor: true,
      t,
      onClose,
    });

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Note" }));
    await user.type(screen.getByLabelText("Highlight note"), "Remember this part");
    await user.click(screen.getByRole("button", { name: "Save note" }));

    expect(mockedSaveHighlight).toHaveBeenCalledWith(
      expect.objectContaining({
        text: "Noted quote",
        note: "Remember this part",
        cfi: "epubcfi(/6/2)",
      }),
    );
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("shows graceful message and avoids save when anchor is unavailable", async () => {
    const onClose = vi.fn();

    render(HighlightToolbar, {
      selectedText: "Detached quote",
      bookId: "book-3",
      pageNumber: 2,
      cfi: null,
      hasSelectionAnchor: false,
      t,
      onClose,
    });

    await fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(mockedSaveHighlight).not.toHaveBeenCalled();
    expect(
      screen.getByText("Selection context is no longer available. Please select text again."),
    ).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });
});
