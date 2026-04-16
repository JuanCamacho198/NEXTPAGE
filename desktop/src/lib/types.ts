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

export type AppSettingDto = {
  key: string;
  valueJson: string;
  updatedAt: string;
};

export type BookCoverDto = {
  bookId: string;
  storagePath: string;
  mimeType: string;
  width?: number;
  height?: number;
  byteSize: number;
};

export type LibraryBookDto = {
  id: string;
  title: string;
  author: string;
  format: string;
  currentPage: number;
  totalPages: number;
  progressPercentage: number;
  coverPath: string | null;
  minutesRead: number;
  updatedAt: string;
};

export type ReadingStatsSummaryDto = {
  totalMinutesRead: number;
  totalSessions: number;
  booksStarted: number;
  booksCompleted: number;
  avgProgressPercentage: number;
};

export type SearchBookTextInput = {
  bookId: string;
  query: string;
  page: number;
  pageSize: number;
};

export type SearchResult = {
  chunkId: string;
  bookId: string;
  locator: string;
  snippet: string;
  rank: number;
};

export type SearchBookTextResponse = {
  items: SearchResult[];
  total: number;
  page: number;
  pageSize: number;
};

export type ReadingSessionInput = {
  bookId: string;
  startedAt: string;
  endedAt?: string;
  durationSeconds: number;
  startPercentage?: number;
  endPercentage?: number;
};

export type SearchNavigationTarget = {
  resultId: string;
  locator: string;
  snippet: string;
};

export type CommandErrorDto = {
  code: string;
  message: string;
  recoverable: boolean;
};
