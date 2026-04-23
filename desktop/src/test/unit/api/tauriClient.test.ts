import { beforeEach, describe, expect, it, vi } from "vitest";

const invokeMock = vi.fn();

vi.mock("@tauri-apps/api/core", () => ({
  invoke: (...args: unknown[]) => invokeMock(...args) as Promise<unknown>,
}));

import {
  getDefaultReaderSettings,
  getReaderSettings,
  resetReaderSettingsToDefaults,
  sanitizeReaderSettings,
  upsertReaderSettings,
} from "$lib/api/tauriClient";

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
      epub: {
        fontSize: 80,
        fontFamily: "serif",
      },
    });
  });

  it("reads and sanitizes persisted reader settings", async () => {
    invokeMock.mockResolvedValueOnce([
      { key: "reader.themeMode", valueJson: JSON.stringify("night"), updatedAt: "2026-01-01T00:00:00.000Z" },
      { key: "reader.brightness", valueJson: JSON.stringify(49), updatedAt: "2026-01-01T00:00:00.000Z" },
      { key: "reader.contrast", valueJson: JSON.stringify(151), updatedAt: "2026-01-01T00:00:00.000Z" },
      { key: "reader.epub.fontSize", valueJson: JSON.stringify(150), updatedAt: "2026-01-01T00:00:00.000Z" },
      { key: "reader.epub.fontFamily", valueJson: JSON.stringify("Literata"), updatedAt: "2026-01-01T00:00:00.000Z" },
    ]);

    const settings = await getReaderSettings();

    expect(invokeMock).toHaveBeenCalledWith("getSettings");
    expect(settings).toEqual({
      themeMode: "night",
      brightness: 50,
      contrast: 150,
      epub: {
        fontSize: 150,
        fontFamily: "Literata",
      },
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
      epub: {
        fontSize: 200,
        fontFamily: "Merriweather",
      },
    });

    expect(invokeMock).toHaveBeenCalledTimes(1);
    const [command, args] = invokeMock.mock.calls[0] as [string, { settings: Array<{ key: string; valueJson: string }> }];
    expect(command).toBe("upsertSettings");
    expect(args.settings).toHaveLength(5);
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
    expect(args.settings.find((entry) => entry.key === "reader.epub.fontSize")?.valueJson).toBe(
      JSON.stringify(100),
    );
    expect(args.settings.find((entry) => entry.key === "reader.epub.fontFamily")?.valueJson).toBe(
      JSON.stringify("serif"),
    );
  });
});
