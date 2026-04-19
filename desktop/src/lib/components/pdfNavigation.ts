export const DEFAULT_PDF_SCALE = 1.0;
export const PDF_SCALE_MIN = 0.5;
export const PDF_SCALE_MAX = 3.0;
export const PDF_SCALE_STEP = 0.1;

export type NavigationResolution = {
  committedPage: number;
  didCommit: boolean;
  shouldShowError: boolean;
};

type ResolveNavigationOptions = {
  previousPage: number;
  targetPage: number;
  rendered: boolean;
  stale: boolean;
};

export const isPageWithinBounds = (page: number, totalPages: number): boolean => {
  return Number.isInteger(page) && page >= 1 && page <= totalPages;
};

export const clampPdfScale = (scale: number): number => {
  const clamped = Math.min(PDF_SCALE_MAX, Math.max(PDF_SCALE_MIN, scale));
  return Math.round(clamped * 10) / 10;
};

export const adjustPdfScaleForWheel = (currentScale: number, deltaY: number): number => {
  const step = deltaY < 0 ? PDF_SCALE_STEP : -PDF_SCALE_STEP;
  return clampPdfScale(currentScale + step);
};

export const resolveNavigationTransaction = ({
  previousPage,
  targetPage,
  rendered,
  stale,
}: ResolveNavigationOptions): NavigationResolution => {
  if (stale) {
    return {
      committedPage: previousPage,
      didCommit: false,
      shouldShowError: false,
    };
  }

  if (!rendered) {
    return {
      committedPage: previousPage,
      didCommit: false,
      shouldShowError: true,
    };
  }

  return {
    committedPage: targetPage,
    didCommit: true,
    shouldShowError: false,
  };
};
