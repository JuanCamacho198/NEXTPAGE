export type BookDto = {
  id: string;
  title: string;
  author: string;
  filePath: string;
  format: string;
  syncStatus: string;
  currentPage: number;
  totalPages: number;
  createdAt: string;
  updatedAt: string;
};

export type ReadingProgressDto = {
  id: string;
  bookId: string;
  cfiLocation: string;
  percentage: number;
  updatedAt: string;
};

export type SaveProgressInput = {
  bookId: string;
  cfiLocation: string;
  percentage: number;
};

export type HighlightDto = {
  id: string;
  bookId: string;
  text: string;
  color: string;
  pageNumber: number;
  note?: string | null;
  createdAt: string;
  /**
   * EPUB only. The Canonical Fragment Identifier that pins the
   * highlight to a specific range inside a chapter. Null for PDF
   * highlights and for legacy EPUB highlights saved before the
   * CFI-persistence change.
   */
  cfi?: string | null;
};

export type BookmarkDto = {
  id: string;
  bookId: string;
  pageNumber: number;
  title?: string;
  createdAt: string;
};

export type SaveHighlightInput = {
  id: string;
  bookId: string;
  text: string;
  color: string;
  pageNumber: number;
  page?: number;
  rectLeft: number;
  rectRight: number;
  rectTop: number;
  rectBottom: number;
  cfi: string | null;
  note?: string | null;
};

export type SaveBookmarkInput = {
  id: string;
  bookId: string;
  pageNumber: number;
  title?: string;
  createdAt: string;
};

export type BookCoverDto = {
  bookId: string;
  storagePath: string;
  mimeType: string;
  width?: number;
  height?: number;
  byteSize: number;
};

export type UpsertBookCoverInput = {
  bookId: string;
  data: number[];
  mimeType?: string;
};