import { describe, expect, it } from "vitest";
import {
  DEFAULT_PDF_SCALE,
  isPageWithinBounds,
  resolveNavigationTransaction,
} from "./pdfNavigation";

describe("pdfNavigation", () => {
  it("keeps the default PDF scale at 100%", () => {
    expect(DEFAULT_PDF_SCALE).toBe(1.0);
  });

  it("validates page bounds consistently", () => {
    expect(isPageWithinBounds(1, 10)).toBe(true);
    expect(isPageWithinBounds(10, 10)).toBe(true);
    expect(isPageWithinBounds(0, 10)).toBe(false);
    expect(isPageWithinBounds(11, 10)).toBe(false);
    expect(isPageWithinBounds(3.5, 10)).toBe(false);
  });

  it("commits target page only on successful non-stale render", () => {
    expect(
      resolveNavigationTransaction({
        previousPage: 3,
        targetPage: 4,
        rendered: true,
        stale: false,
      }),
    ).toEqual({
      committedPage: 4,
      didCommit: true,
      shouldShowError: false,
    });

    expect(
      resolveNavigationTransaction({
        previousPage: 3,
        targetPage: 4,
        rendered: false,
        stale: false,
      }),
    ).toEqual({
      committedPage: 3,
      didCommit: false,
      shouldShowError: true,
    });

    expect(
      resolveNavigationTransaction({
        previousPage: 3,
        targetPage: 4,
        rendered: true,
        stale: true,
      }),
    ).toEqual({
      committedPage: 3,
      didCommit: false,
      shouldShowError: false,
    });
  });
});
