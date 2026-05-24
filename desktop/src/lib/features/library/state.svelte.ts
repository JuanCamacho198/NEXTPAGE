// ─── Feature state: library — migrated to $state runes ───
// The canonical app state is AppState ($lib/stores/AppState.svelte).
// This file retains the public API surface for the features/library barrel.

import { BulkImportService, type BulkImportProgress } from "$lib/shared/services/BulkImportService";
import type {
  BulkImportSummary,
  CollectionDto,
  LibraryBookDto,
  ScanFolderResult,
} from "$lib/shared/types";
import type { MessageKey } from "$lib/shared/i18n";

// Local type (not in shared/types, preserved for backward compat)
export type ReaderBook = LibraryBookDto & {
  filePath: string;
  isFavorite?: boolean;
  toRead?: boolean;
  completed?: boolean;
  shelfStatus?: "all" | "favorites" | "to_read" | "completed";
};

export const LIBRARY_VIEW_MODE = {
  LIST: "list",
  GRID: "grid",
} as const;
export type LibraryViewMode = (typeof LIBRARY_VIEW_MODE)[keyof typeof LIBRARY_VIEW_MODE];

export const BULK_IMPORT_STATUS = {
  QUEUED: "queued",
  IMPORTING: "importing",
  SUCCESS: "success",
  SKIPPED: "skipped",
  FAILED: "failed",
  CANCELLED: "cancelled",
} as const;

export const COLLECTION_COLOR_OPTIONS = [
  "#6366f1", "#8b5cf6", "#ec4899", "#ef4444",
  "#f97316", "#eab308", "#22c55e", "#14b8a6", "#0ea5e9",
] as const;

class LibraryStateManager {
  books = $state<ReaderBook[]>([]);
  collections = $state<CollectionDto[]>([]);
  isLoading = $state(false);
  unavailableReason = $state<string | null>(null);
  error = $state<string | null>(null);
  editingBook = $state<ReaderBook | null>(null);

  isBulkImportOpen = $state(false);
  isBulkScanning = $state(false);
  isBulkImporting = $state(false);
  bulkImportFolderPath = $state<string | null>(null);
  bulkImportFolderName = $state<string | null>(null);
  bulkScanResult = $state<ScanFolderResult | null>(null);
  bulkScanError = $state<string | null>(null);
  bulkImportProgress = $state<BulkImportProgress | null>(null);
  bulkImportSummary = $state<BulkImportSummary | null>(null);

  isImporting = $state(false);
  importProgress = $state<any | null>(null);

  bulkImportService = new BulkImportService();

  // ─── Utility functions ───

  formatUpdatedAt(iso: string, t: (key: string, params?: Record<string, string | number>) => string): string {
    const parsed = new Date(iso);
    if (Number.isNaN(parsed.getTime())) return t("settings.unknownBook");
    return parsed.toLocaleDateString();
  }

  formatProgress(progress: number): string {
    return `${Math.round(progress)}%`;
  }

  getBulkImportStatusKey(status: string): string {
    const map: Record<string, string> = {
      importing: "library.bulkImport.status.ingesting",
      success: "library.bulkImport.status.success",
      skipped: "library.bulkImport.status.skipped",
      failed: "library.bulkImport.status.failed",
      cancelled: "library.bulkImport.status.cancelled",
    };
    return map[status] || "library.bulkImport.status.queued";
  }

  getBulkImportStatusClass(status: string): string {
    const map: Record<string, string> = {
      success: "text-emerald-700",
      failed: "text-red-700",
      importing: "text-blue-700",
      cancelled: "text-amber-700",
    };
    return map[status] || "text-[var(--color-text-muted)]";
  }

  generateCollectionId(): string {
    return Math.random().toString(36).substring(2, 9);
  }

  getShelfMenuId(bookId: string): string {
    return `shelf-actions-menu-${bookId}`;
  }
}

export const libraryState = new LibraryStateManager();
