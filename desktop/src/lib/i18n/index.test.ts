import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../tauriClient", () => ({
  getLocaleSetting: vi.fn(),
  upsertLocaleSetting: vi.fn(),
}));

import { getLocaleSetting, upsertLocaleSetting } from "../tauriClient";

describe("i18n", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    globalThis.localStorage?.clear();
  });

  it("uses es default locale on first run and persists it", async () => {
    vi.mocked(getLocaleSetting).mockResolvedValueOnce(null);

    const { i18n } = await import("./index");
    const locale = await i18n.initializeLocale();

    expect(locale).toBe("es");
    expect(vi.mocked(upsertLocaleSetting)).toHaveBeenCalledWith("es");
    expect(globalThis.localStorage?.getItem("nextpage.ui.locale")).toBe("es");
  });

  it("falls back to en for unsupported persisted locale", async () => {
    vi.mocked(getLocaleSetting).mockResolvedValueOnce("pt");

    const { i18n } = await import("./index");
    const locale = await i18n.initializeLocale();

    expect(locale).toBe("en");
    expect(vi.mocked(upsertLocaleSetting)).toHaveBeenCalledWith("en");
    expect(globalThis.localStorage?.getItem("nextpage.ui.locale")).toBe("en");
  });

  it("returns en translation when active locale misses a key", async () => {
    vi.mocked(getLocaleSetting).mockResolvedValueOnce("es");

    const { i18n } = await import("./index");
    const locale = await i18n.initializeLocale();
    const translated = i18n.t(locale, "errors.commandFailure");

    expect(translated).toBe("Unknown command failure");
    expect(i18n.t("en", "errors.commandFailure")).toBe("Unknown command failure");
  });
});
