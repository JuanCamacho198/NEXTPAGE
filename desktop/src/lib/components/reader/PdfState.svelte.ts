import * as pdfjsLib from "pdfjs-dist";
import { ReaderError, handleError } from "$lib/utils/errors";
import { DEFAULT_PDF_SCALE } from "$lib/domain/reader/pdf/pdfNavigation";
import { readable, writable } from "svelte/store";

export interface PdfStateOptions {
  initialPage?: number;
  initialScale?: number;
}

export interface PdfStateSnapshot {
  currentPage: number;
  totalPages: number;
  scale: number;
  committedScale: number;
  isLoading: boolean;
  error: string | null;
  loadError: ReaderError | null;
  pdfDocument: pdfjsLib.PDFDocumentProxy | null;
  currentPageObj: pdfjsLib.PDFPageProxy | null;
  navigationError: string | null;
  flashSearchResult: boolean;
}

const defaultSnapshot: PdfStateSnapshot = {
  currentPage: 1,
  totalPages: 0,
  scale: DEFAULT_PDF_SCALE,
  committedScale: DEFAULT_PDF_SCALE,
  isLoading: true,
  error: null,
  loadError: null,
  pdfDocument: null,
  currentPageObj: null,
  navigationError: null,
  flashSearchResult: false,
};

function createPdfStateStore() {
  const { subscribe, set, update } = writable<PdfStateSnapshot>(defaultSnapshot);

  return {
    subscribe,

    reset(options?: PdfStateOptions) {
      set({
        ...defaultSnapshot,
        currentPage: options?.initialPage ?? 1,
        scale: options?.initialScale ?? DEFAULT_PDF_SCALE,
        committedScale: DEFAULT_PDF_SCALE,
      });
    },

    setDocument(doc: pdfjsLib.PDFDocumentProxy) {
      update((s) => ({
        ...s,
        pdfDocument: doc,
        totalPages: doc.numPages,
        isLoading: false,
        error: null,
      }));
    },

    setCurrentPage(page: number) {
      update((s) => {
        if (page >= 1 && page <= s.totalPages) {
          return { ...s, currentPage: page };
        }
        return s;
      });
    },

    setError(err: unknown) {
      const readerError = err instanceof ReaderError
        ? err
        : new ReaderError(
            err instanceof Error ? err.message : "Failed to load PDF",
            "PDF_LOAD_ERROR",
            { originalError: err }
          );
      handleError(readerError, "reader");
      update((s) => ({
        ...s,
        loadError: readerError,
        error: readerError.message,
        isLoading: false,
      }));
    },

    setNavigationError(message: string) {
      update((s) => ({ ...s, navigationError: message }));
    },

    clearNavigationError() {
      update((s) => ({ ...s, navigationError: null }));
    },

    setScale(newScale: number) {
      update((s) => ({ ...s, scale: newScale }));
    },

    commitScale() {
      update((s) => ({ ...s, committedScale: s.scale }));
    },

    triggerSearchFlash() {
      update((s) => ({ ...s, flashSearchResult: true }));
      setTimeout(() => {
        update((s) => ({ ...s, flashSearchResult: false }));
      }, 900);
    },

    setCurrentPageObj(page: pdfjsLib.PDFPageProxy | null) {
      update((s) => ({ ...s, currentPageObj: page }));
    },

    isLoaded(s: PdfStateSnapshot): boolean {
      return s.pdfDocument !== null && s.totalPages > 0;
    },

    canGoNext(s: PdfStateSnapshot): boolean {
      return s.currentPage < s.totalPages;
    },

    canGoPrev(s: PdfStateSnapshot): boolean {
      return s.currentPage > 1;
    },

    progressPercent(s: PdfStateSnapshot): number {
      return s.totalPages > 0 ? Math.round((s.currentPage / s.totalPages) * 100) : 0;
    },
  };
}

export const pdfState = createPdfStateStore();
export const currentPage = { subscribe: pdfState.subscribe, map: (s: PdfStateSnapshot) => s.currentPage };
export const totalPages = { subscribe: pdfState.subscribe, map: (s: PdfStateSnapshot) => s.totalPages };
export const scale = { subscribe: pdfState.subscribe, map: (s: PdfStateSnapshot) => s.scale };
export const isLoading = { subscribe: pdfState.subscribe, map: (s: PdfStateSnapshot) => s.isLoading };
export const error = { subscribe: pdfState.subscribe, map: (s: PdfStateSnapshot) => s.error };
export const pdfDocument = { subscribe: pdfState.subscribe, map: (s: PdfStateSnapshot) => s.pdfDocument };
export const navigationError = { subscribe: pdfState.subscribe, map: (s: PdfStateSnapshot) => s.navigationError };
export const flashSearchResult = { subscribe: pdfState.subscribe, map: (s: PdfStateSnapshot) => s.flashSearchResult };