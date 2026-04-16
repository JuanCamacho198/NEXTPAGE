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

export type BookImportInput = {
  sourcePath: string;
  title?: string;
  author?: string;
  format: string;
};

export type HighlightDto = {
  id: string;
  bookId: string;
  text: string;
  color: string;
  pageNumber: number;
  createdAt: string;
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
  createdAt: string;
};

export type SaveBookmarkInput = {
  id: string;
  bookId: string;
  pageNumber: number;
  title?: string;
  createdAt: string;
};
