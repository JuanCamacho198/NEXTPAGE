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

export type ScannedFile = {
  fullPath: string;
  fileName: string;
  format: string;
  isDuplicate: boolean;
};

export type ScanFolderResult = {
  files: ScannedFile[];
  skippedUnsupportedCount: number;
  skippedUnreadableCount: number;
};

export const BULK_IMPORT_ITEM_STATUS = {
  QUEUED: "queued",
  IMPORTING: "importing",
  SUCCESS: "success",
  SKIPPED: "skipped",
  FAILED: "failed",
  CANCELLED: "cancelled",
} as const;

export type BulkImportItemStatus =
  (typeof BULK_IMPORT_ITEM_STATUS)[keyof typeof BULK_IMPORT_ITEM_STATUS];

export type BulkImportItemResult = {
  file: ScannedFile;
  status: BulkImportItemStatus;
  bookId: string | null;
  message: string | null;
};

export type BulkImportSummary = {
  total: number;
  queued: number;
  importing: number;
  success: number;
  skipped: number;
  failed: number;
  cancelled: number;
  results: BulkImportItemResult[];
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
  page: number;
  rectLeft: number;
  rectRight: number;
  rectTop: number;
  rectBottom: number;
  cfi: string | null;
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

export type UpsertBookCoverInput = {
  bookId: string;
  data: number[];
  mimeType?: string;
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
  collectionIds?: number[];
};

export type CollectionDto = {
  id: number;
  name: string;
  color: string | null;
  isSystem: boolean;
  createdAt: string;
};

export type CreateCollectionInput = {
  name: string;
  color?: string;
};

export type BookCollectionInput = {
  bookId: string;
  collectionId: number;
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

export type PdfOutlineItem = {
  id: string;
  title: string;
  dest: string | unknown[] | null;
  items: PdfOutlineItem[];
};

export type CommandErrorDto = {
  code: string;
  message: string;
  recoverable: boolean;
};

export const SUPPORTED_UI_LOCALES = ["es", "en"] as const;

export type UiLocale = (typeof SUPPORTED_UI_LOCALES)[number];

export const UI_LOCALE_SETTING_KEY = "ui.locale" as const;

export type TranslationKey = string;
