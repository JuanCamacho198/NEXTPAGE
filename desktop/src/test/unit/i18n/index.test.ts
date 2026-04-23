import { beforeEach, describe, expect, it, vi } from "vitest";

// Mock the tauriClient module before importing
vi.mock("$lib/api/tauriClient", () => ({
  getLocaleSetting: vi.fn().mockResolvedValue(null),
  upsertLocaleSetting: vi.fn(),
}));

import { getLocaleSetting, upsertLocaleSetting } from "$lib/api/tauriClient";

describe("i18n", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    globalThis.localStorage?.clear();
  });

  it("uses es default locale on first run and persists it", async () => {
    const mockGetLocale = getLocaleSetting as ReturnType<typeof vi.fn>;
    const mockUpsert = upsertLocaleSetting as ReturnType<typeof vi.fn>;
    mockGetLocale.mockResolvedValueOnce(null);

    const { i18n } = await import("$lib/i18n");
    const locale = await i18n.initializeLocale();

    expect(locale).toBe("es");
    expect(mockUpsert).toHaveBeenCalledWith("es");
    expect(globalThis.localStorage?.getItem("nextpage.ui.locale")).toBe("es");
  });

  it("falls back to en for unsupported persisted locale", async () => {
    const mockGetLocale = getLocaleSetting as ReturnType<typeof vi.fn>;
    mockGetLocale.mockResolvedValueOnce("pt");

    const { i18n }: any = await import("$lib/i18n");
    const locale = await i18n.initializeLocale();

    expect(locale).toBe("en");
    expect(upsertLocaleSetting).toHaveBeenCalledWith("en");
    expect(globalThis.localStorage?.getItem("nextpage.ui.locale")).toBe("en");
  });

  it("returns en translation when active locale misses a key", async () => {
    const mockGetLocale = getLocaleSetting as ReturnType<typeof vi.fn>;
    mockGetLocale.mockResolvedValueOnce("es");

    const { i18n }: any = await import("$lib/i18n");
    const locale = await i18n.initializeLocale();
    const translated = i18n.t(locale as string, "errors.commandFailure");

    expect(translated).toBe("Unknown command failure");
    expect(i18n.t("en", "errors.commandFailure")).toBe("Unknown command failure");
  });
});
