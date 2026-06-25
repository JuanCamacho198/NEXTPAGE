import { beforeEach, describe, expect, it, vi } from "vitest";

const invokeMock = vi.fn();

vi.mock("@tauri-apps/api/core", () => ({
  invoke: (...args: unknown[]) => invokeMock(...args) as Promise<unknown>,
}));

import {
  addDictionaryWord,
  createTag,
  getDefaultReaderSettings,
  getReaderSettings,
  listDictionaryWords,
  listTags,
  listTagsForHighlight,
  removeDictionaryWord,
  resetReaderSettingsToDefaults,
  sanitizeReaderSettings,
  saveHighlightTags,
  updateHighlight,
  upsertReaderSettings,
} from "$lib/shared/api/tauriClient";

describe("tauriClient reader settings", () => {
  beforeEach(() => {
    invokeMock.mockReset();
  });

  it("sanitizes invalid reader settings with defaults and clamps", () => {
    const settings = sanitizeReaderSettings({
      themeMode: "invalid" as never,
      brightness: 999,
      contrast: 5,
      epub: {
        fontSize: 79,
        fontFamily: "   ",
      },
    });

    expect(settings).toEqual({
      themeMode: "paper",
      brightness: 150,
      contrast: 50,
      selectionColor: "#3388ff",
      epub: {
        fontSize: 80,
        fontFamily: "serif",
      },
      lineHeight: 1.8,
      letterSpacing: 0,
      paragraphSpacing: 1,
      textAlign: "left",
      direction: "ltr",
      hyphenation: false,
      verticalScrolling: false,
      margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 },
      showHeader: true,
      showFooter: true,
      showPageNumbers: true,
      progressIndicator: "percentage",
    });
  });

  it("reads and sanitizes persisted reader settings", async () => {
    invokeMock.mockResolvedValueOnce([
      { key: "reader.themeMode", valueJson: JSON.stringify("night"), updatedAt: "2026-01-01T00:00:00.000Z" },
      { key: "reader.brightness", valueJson: JSON.stringify(49), updatedAt: "2026-01-01T00:00:00.000Z" },
      { key: "reader.contrast", valueJson: JSON.stringify(151), updatedAt: "2026-01-01T00:00:00.000Z" },
      { key: "reader.selectionColor", valueJson: JSON.stringify("#33bbff"), updatedAt: "2026-01-01T00:00:00.000Z" },
      { key: "reader.epub.fontSize", valueJson: JSON.stringify(150), updatedAt: "2026-01-01T00:00:00.000Z" },
      { key: "reader.epub.fontFamily", valueJson: JSON.stringify("Literata"), updatedAt: "2026-01-01T00:00:00.000Z" },
      // New layout fields — not provided, so defaults apply
    ]);

    const settings = await getReaderSettings();

    expect(invokeMock).toHaveBeenCalledWith("getSettings", undefined);
    expect(settings).toEqual({
      themeMode: "night",
      brightness: 50,
      contrast: 150,
      selectionColor: "#33bbff",
      epub: {
        fontSize: 150,
        fontFamily: "Literata",
      },
      lineHeight: 1.8,
      letterSpacing: 0,
      paragraphSpacing: 1,
      textAlign: "left",
      direction: "ltr",
      hyphenation: false,
      verticalScrolling: false,
      margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 },
      showHeader: true,
      showFooter: true,
      showPageNumbers: true,
      progressIndicator: "percentage",
    });
  });

  it("persists sanitized reader settings values", async () => {
    invokeMock.mockResolvedValue(undefined);

    const settings = await upsertReaderSettings({
      themeMode: "sepia",
      brightness: 88.6,
      contrast: 112.4,
      epub: {
        fontSize: 205,
        fontFamily: "   Merriweather   ",
      },
    });

    expect(settings).toEqual({
      themeMode: "sepia",
      brightness: 89,
      contrast: 112,
      selectionColor: "#3388ff",
      epub: {
        fontSize: 200,
        fontFamily: "Merriweather",
      },
      lineHeight: 1.8,
      letterSpacing: 0,
      paragraphSpacing: 1,
      textAlign: "left",
      direction: "ltr",
      hyphenation: false,
      verticalScrolling: false,
      margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 },
      showHeader: true,
      showFooter: true,
      showPageNumbers: true,
      progressIndicator: "percentage",
    });

    expect(invokeMock).toHaveBeenCalledTimes(1);
    const [command, args] = invokeMock.mock.calls[0] as [string, { settings: Array<{ key: string; valueJson: string }> }];
    expect(command).toBe("upsertSettings");
    expect(args.settings).toHaveLength(21);
    expect(args.settings.find((entry) => entry.key === "reader.themeMode")?.valueJson).toBe(
      JSON.stringify("sepia"),
    );
    expect(args.settings.find((entry) => entry.key === "reader.brightness")?.valueJson).toBe(
      JSON.stringify(89),
    );
    expect(args.settings.find((entry) => entry.key === "reader.contrast")?.valueJson).toBe(
      JSON.stringify(112),
    );
    expect(args.settings.find((entry) => entry.key === "reader.epub.fontSize")?.valueJson).toBe(
      JSON.stringify(200),
    );
    expect(args.settings.find((entry) => entry.key === "reader.epub.fontFamily")?.valueJson).toBe(
      JSON.stringify("Merriweather"),
    );
  });

  it("resets reader settings to defaults", async () => {
    invokeMock.mockResolvedValue(undefined);

    const settings = await resetReaderSettingsToDefaults();

    expect(settings).toEqual(getDefaultReaderSettings());
    const [command, args] = invokeMock.mock.calls[0] as [string, { settings: Array<{ key: string; valueJson: string }> }];
    expect(command).toBe("upsertSettings");
    expect(args.settings.find((entry) => entry.key === "reader.themeMode")?.valueJson).toBe(
      JSON.stringify("paper"),
    );
    expect(args.settings.find((entry) => entry.key === "reader.brightness")?.valueJson).toBe(
      JSON.stringify(100),
    );
    expect(args.settings.find((entry) => entry.key === "reader.contrast")?.valueJson).toBe(
      JSON.stringify(100),
    );
    expect(args.settings.find((entry) => entry.key === "reader.selectionColor")?.valueJson).toBe(
      JSON.stringify("#3388ff"),
    );
    expect(args.settings.find((entry) => entry.key === "reader.epub.fontSize")?.valueJson).toBe(
      JSON.stringify(100),
    );
    expect(args.settings.find((entry) => entry.key === "reader.epub.fontFamily")?.valueJson).toBe(
      JSON.stringify("serif"),
    );
    // Verify new layout fields are persisted
    expect(args.settings.find((entry) => entry.key === "reader.lineHeight")?.valueJson).toBe(
      JSON.stringify(1.8),
    );
    expect(args.settings.find((entry) => entry.key === "reader.textAlign")?.valueJson).toBe(
      JSON.stringify("left"),
    );
  });
});

describe("tauriClient highlight menu commands", () => {
  beforeEach(() => {
    invokeMock.mockReset();
  });

  it("updates highlight color and note", async () => {
    invokeMock.mockResolvedValueOnce({
      id: "hl-1",
      bookId: "book-1",
      text: "sample",
      color: "#4ADE80",
      pageNumber: 1,
      note: "updated note",
      createdAt: "2024-01-01T00:00:00Z",
    });

    const result = await updateHighlight({ id: "hl-1", color: "#4ADE80", note: "updated note" });

    expect(invokeMock).toHaveBeenCalledWith("updateHighlight", {
      payload: { id: "hl-1", color: "#4ADE80", note: "updated note" },
    });
    expect(result.color).toBe("#4ADE80");
    expect(result.note).toBe("updated note");
  });

  it("saves highlight tags", async () => {
    invokeMock.mockResolvedValueOnce([
      { id: "tag-1", name: "Review", createdAt: "2024-01-01T00:00:00Z" },
    ]);

    const result = await saveHighlightTags({ highlightId: "hl-1", tagIds: ["tag-1"] });

    expect(invokeMock).toHaveBeenCalledWith("saveHighlightTags", {
      payload: { highlightId: "hl-1", tagIds: ["tag-1"] },
    });
    expect(result).toHaveLength(1);
    expect(result[0].name).toBe("Review");
  });

  it("lists tags", async () => {
    invokeMock.mockResolvedValueOnce([
      { id: "tag-1", name: "Review", createdAt: "2024-01-01T00:00:00Z" },
    ]);

    const result = await listTags();

    expect(invokeMock).toHaveBeenCalledWith("listTags", undefined);
    expect(result).toHaveLength(1);
  });

  it("lists tags for a highlight", async () => {
    invokeMock.mockResolvedValueOnce([
      { id: "tag-1", name: "Review", createdAt: "2024-01-01T00:00:00Z" },
    ]);

    const result = await listTagsForHighlight("hl-1");

    expect(invokeMock).toHaveBeenCalledWith("listTagsForHighlight", { highlightId: "hl-1" });
    expect(result).toHaveLength(1);
  });

  it("creates a tag", async () => {
    invokeMock.mockResolvedValueOnce({
      id: "tag-1",
      name: "Review",
      color: "#ff0000",
      createdAt: "2024-01-01T00:00:00Z",
    });

    const result = await createTag({ name: "Review", color: "#ff0000" });

    expect(invokeMock).toHaveBeenCalledWith("createTag", {
      payload: { name: "Review", color: "#ff0000" },
    });
    expect(result.color).toBe("#ff0000");
  });

  it("adds and lists dictionary words", async () => {
    invokeMock.mockResolvedValueOnce({
      id: "word-1",
      word: "Serendipity",
      createdAt: "2024-01-01T00:00:00Z",
    });

    const added = await addDictionaryWord({ word: "Serendipity" });

    expect(invokeMock).toHaveBeenCalledWith("addDictionaryWord", {
      payload: { word: "Serendipity" },
    });
    expect(added.word).toBe("Serendipity");

    invokeMock.mockResolvedValueOnce([{ id: "word-1", word: "Serendipity", createdAt: "2024-01-01T00:00:00Z" }]);

    const words = await listDictionaryWords();
    expect(invokeMock).toHaveBeenCalledWith("listDictionaryWords", undefined);
    expect(words).toHaveLength(1);
  });

  it("removes a dictionary word", async () => {
    invokeMock.mockResolvedValueOnce(undefined);

    await removeDictionaryWord("word-1");

    expect(invokeMock).toHaveBeenCalledWith("removeDictionaryWord", { id: "word-1" });
  });
});
