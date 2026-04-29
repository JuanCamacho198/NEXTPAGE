import { describe, expect, it } from "vitest";
import {
  adjustPdfScaleForWheel,
  clampPdfScale,
  DEFAULT_PDF_SCALE,
  PDF_SCALE_MAX,
  PDF_SCALE_MIN,
  PDF_SCALE_STEP,
  isPageWithinBounds,
  resolveNavigationTransaction,
} from "$lib/domain/reader/pdf/pdfNavigation.js";

describe("pdfNavigation", () => {
  it("keeps the default PDF scale at 100%", () => {
    expect(DEFAULT_PDF_SCALE).toBe(1.0);
  });

  it("clamps scale between 50% and 300%", () => {
    expect(clampPdfScale(PDF_SCALE_MIN - 0.1)).toBe(PDF_SCALE_MIN);
    expect(clampPdfScale(PDF_SCALE_MAX + 0.1)).toBe(PDF_SCALE_MAX);
    expect(clampPdfScale(1.23)).toBe(1.2);
  });

  it("adjusts wheel scale in 10% steps", () => {
    expect(adjustPdfScaleForWheel(1, -100)).toBe(1 + PDF_SCALE_STEP);
    expect(adjustPdfScaleForWheel(1, 100)).toBe(1 - PDF_SCALE_STEP);
    expect(adjustPdfScaleForWheel(PDF_SCALE_MAX, -100)).toBe(PDF_SCALE_MAX);
    expect(adjustPdfScaleForWheel(PDF_SCALE_MIN, 100)).toBe(PDF_SCALE_MIN);
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
