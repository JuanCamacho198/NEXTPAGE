export const DEFAULT_PDF_SCALE = 1.0;

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
