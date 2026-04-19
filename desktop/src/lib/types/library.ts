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