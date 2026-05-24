import { describe, expect, it } from "vitest";
import {
  clampPdfScale,
  isPageWithinBounds,
  adjustPdfScaleForWheel,
  resolveNavigationTransaction,
  PDF_SCALE_MIN,
  PDF_SCALE_MAX,
  PDF_SCALE_STEP,
  DEFAULT_PDF_SCALE,
} from "$lib/features/reader/pdf/pdfNavigation";

describe("isPageWithinBounds", () => {
  it("returns true for valid page within bounds", () => {
    expect(isPageWithinBounds(1, 10)).toBe(true);
    expect(isPageWithinBounds(5, 10)).toBe(true);
    expect(isPageWithinBounds(10, 10)).toBe(true);
  });

  it("returns false for page below 1", () => {
    expect(isPageWithinBounds(0, 10)).toBe(false);
    expect(isPageWithinBounds(-1, 10)).toBe(false);
  });

  it("returns false for page above totalPages", () => {
    expect(isPageWithinBounds(11, 10)).toBe(false);
  });

  it("returns false for non-integer page", () => {
    expect(isPageWithinBounds(1.5, 10)).toBe(false);
    expect(isPageWithinBounds(NaN, 10)).toBe(false);
  });
});

describe("clampPdfScale", () => {
  it("returns the scale when within bounds", () => {
    expect(clampPdfScale(1.0)).toBe(1.0);
    expect(clampPdfScale(1.5)).toBe(1.5);
    expect(clampPdfScale(2.0)).toBe(2.0);
  });

  it("clamps to minimum when below", () => {
    expect(clampPdfScale(0.3)).toBe(PDF_SCALE_MIN);
    expect(clampPdfScale(0.0)).toBe(PDF_SCALE_MIN);
    expect(clampPdfScale(-1.0)).toBe(PDF_SCALE_MIN);
  });

  it("clamps to maximum when above", () => {
    expect(clampPdfScale(3.5)).toBe(PDF_SCALE_MAX);
    expect(clampPdfScale(10.0)).toBe(PDF_SCALE_MAX);
  });

  it("rounds to one decimal place", () => {
    expect(clampPdfScale(1.23)).toBe(1.2);
    expect(clampPdfScale(1.27)).toBe(1.3);
  });
});

describe("adjustPdfScaleForWheel", () => {
  it("increases scale on negative deltaY (scroll up)", () => {
    const result = adjustPdfScaleForWheel(DEFAULT_PDF_SCALE, -100);
    expect(result).toBe(DEFAULT_PDF_SCALE + PDF_SCALE_STEP);
  });

  it("decreases scale on positive deltaY (scroll down)", () => {
    const result = adjustPdfScaleForWheel(DEFAULT_PDF_SCALE, 100);
    expect(result).toBe(DEFAULT_PDF_SCALE - PDF_SCALE_STEP);
  });

  it("does not go below minimum", () => {
    const result = adjustPdfScaleForWheel(PDF_SCALE_MIN, 200);
    expect(result).toBe(PDF_SCALE_MIN);
  });

  it("does not go above maximum", () => {
    const result = adjustPdfScaleForWheel(PDF_SCALE_MAX, -200);
    expect(result).toBe(PDF_SCALE_MAX);
  });
});

describe("resolveNavigationTransaction", () => {
  it("returns success when rendered and not stale", () => {
    const result = resolveNavigationTransaction({
      previousPage: 1,
      targetPage: 5,
      rendered: true,
      stale: false,
    });
    expect(result.didCommit).toBe(true);
    expect(result.committedPage).toBe(5);
    expect(result.shouldShowError).toBe(false);
  });

  it("returns stale result without error when stale", () => {
    const result = resolveNavigationTransaction({
      previousPage: 1,
      targetPage: 5,
      rendered: true,
      stale: true,
    });
    expect(result.didCommit).toBe(false);
    expect(result.committedPage).toBe(1);
    expect(result.shouldShowError).toBe(false);
  });

  it("returns error when render fails", () => {
    const result = resolveNavigationTransaction({
      previousPage: 1,
      targetPage: 5,
      rendered: false,
      stale: false,
    });
    expect(result.didCommit).toBe(false);
    expect(result.committedPage).toBe(1);
    expect(result.shouldShowError).toBe(true);
  });
});
