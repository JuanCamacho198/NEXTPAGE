import { writable, get, derived } from "svelte/store";
import type {
  LibraryBookDto,
  CollectionDto,
  BulkImportSummary,
  ScanFolderResult,
  BookDto,
} from "$lib/shared/types";
import type { MessageKey } from "$lib/shared/i18n";
import {
  listLibraryBooks,
  listBooks,
  listCollections,
  hideBookFromLibrary,
  upsertBook,
  updateBookProgress,
  scanFolder,
} from "$lib/shared/api/tauriClient";
import { BulkImportService, type BulkImportProgress } from "$lib/shared/services/BulkImportService";
import { importBook } from "$lib/shared/services/BookImportService";
import { pickFile, pickFolder } from "$lib/shared/services/FilePicker";
import { extractPdfMetadata } from "$lib/shared/services/pdfThumbnail";

export const LIBRARY_VIEW_MODE = {
  LIST: "list",
  GRID: "grid",
} as const;

export type LibraryViewMode = typeof LIBRARY_VIEW_MODE[keyof typeof LIBRARY_VIEW_MODE];

export const BULK_IMPORT_STATUS = {
  QUEUED: "queued",
  IMPORTING: "importing",
  SUCCESS: "success",
  SKIPPED: "skipped",
  FAILED: "failed",
  CANCELLED: "cancelled",
} as const;

export type ReaderBook = LibraryBookDto & {
  filePath: string;
  isFavorite?: boolean;
  toRead?: boolean;
  completed?: boolean;
  shelfStatus?: "all" | "favorites" | "to_read" | "completed";
};

export const books = writable<ReaderBook[]>([]);
export const collections = writable<CollectionDto[]>([]);
export const isLoading = writable(false);
export const unavailableReason = writable<string | null>(null);
export const error = writable<string | null>(null);
export const editingBook = writable<ReaderBook | null>(null);

export const isBulkImportOpen = writable(false);
export const isBulkScanning = writable(false);
export const isBulkImporting = writable(false);
export const bulkImportFolderPath = writable<string | null>(null);
export const bulkImportFolderName = writable<string | null>(null);
export const bulkScanResult = writable<ScanFolderResult | null>(null);
export const bulkScanError = writable<string | null>(null);
export const bulkImportProgress = writable<BulkImportProgress | null>(null);
export const bulkImportSummary = writable<BulkImportSummary | null>(null);

export const isImporting = writable(false);
export const importProgress = writable<any | null>(null);

const bulkImportService = new BulkImportService();

export const LibraryState = {
  async loadLibrary(onLoaded?: (books: ReaderBook[]) => void) {
    isLoading.set(true);
    error.set(null);

    try {
      const [libraryRows, sourceRows, loadedCollections] = await Promise.all([
        listLibraryBooks(1),
        listBooks(),
        listCollections(),
      ]);
      collections.set(loadedCollections);

      const filePathById = new Map<string, string>(
        sourceRows.map((book: BookDto) => [book.id, book.filePath]),
      );

      const booksWithCollections = libraryRows.map((entry: LibraryBookDto) => ({
        ...entry,
        filePath: filePathById.get(entry.id) ?? "",
        collectionIds: entry.collectionIds ?? [],
      }));

      books.set(booksWithCollections);
      unavailableReason.set(null);
      
      if (onLoaded) {
        onLoaded(booksWithCollections);
      }
    } catch (err: any) {
      error.set(err.message || "Failed to load library");
    } finally {
      isLoading.set(false);
    }
  },

  async handleImportFile(t: (key: MessageKey) => string) {
    const file = await pickFile();
    if (!file) return;

    isImporting.set(true);
    error.set(null);

    try {
      const format = file.name.toLowerCase().endsWith(".epub") ? "epub" : "pdf";
      const title = file.name.replace(/\.(pdf|epub)$/i, "");

      let author: string | undefined;
      if (format === "pdf") {
        try {
          const meta = await extractPdfMetadata(file.path);
          if (meta.author) author = meta.author;
        } catch {
          // ignore
        }
      }

      await importBook(
        { sourcePath: file.path, title, author, format },
        (progress) => {
          importProgress.set(progress);
        },
      );

      await this.loadLibrary();
    } catch (err: any) {
      error.set(err.message || t("import.failed" as any));
    } finally {
      isImporting.set(false);
      importProgress.set(null);
    }
  },

  openBulkImportModal() {
    isBulkImportOpen.set(true);
  },

  closeBulkImportModal() {
    if (get(isBulkImporting)) {
      bulkImportService.cancel();
    }
    isBulkImportOpen.set(false);
    isBulkScanning.set(false);
    bulkScanError.set(null);
    bulkImportProgress.set(null);
    bulkImportSummary.set(null);
  },

  async handlePickBulkImportFolder(t: (key: MessageKey) => string) {
    const selected = await pickFolder(t("library.bulkImport.selectFolderTitle" as any));
    if (!selected) return;

    bulkImportFolderPath.set(selected.path);
    bulkImportFolderName.set(selected.name);
    bulkScanResult.set(null);
    bulkScanError.set(null);
    bulkImportProgress.set(null);
    bulkImportSummary.set(null);
  },

  async handleScanBulkImportFolder() {
    const path = get(bulkImportFolderPath);
    if (!path) return;
    
    isBulkScanning.set(true);
    bulkScanError.set(null);
    try {
      bulkScanResult.set(await scanFolder(path));
    } catch (err: any) {
      bulkScanError.set(err.message || "Failed to scan folder");
    } finally {
      isBulkScanning.set(false);
    }
  },

  async handleStartBulkImport() {
    const path = get(bulkImportFolderPath);
    const result = get(bulkScanResult);
    
    if (!path || !result || result.files.length === 0) {
      return;
    }

    isBulkImporting.set(true);
    bulkScanError.set(null);
    bulkImportProgress.set(null);
    bulkImportSummary.set(null);

    try {
      const summary = await bulkImportService.importFolder(path, (progress) => {
        bulkImportProgress.set(progress);
      });

      bulkImportSummary.set(summary);
      if (summary.success > 0 || summary.skipped > 0 || summary.failed > 0 || summary.cancelled > 0) {
        await this.loadLibrary();
      }
    } catch (err: any) {
      bulkScanError.set(err.message || "Failed to import folder");
    } finally {
      isBulkImporting.set(false);
    }
  },

  handleCancelBulkImport() {
    bulkImportService.cancel();
  },

  async handleHideBook(book: Pick<ReaderBook, "id">) {
    try {
      await hideBookFromLibrary(book.id);
      await this.loadLibrary();
    } catch (err: any) {
      error.set(err.message);
    }
  },

  async handleToggleFavorite(book: Pick<ReaderBook, "id" | "isFavorite">) {
    const nextFavorite = !Boolean(book.isFavorite);
    books.update(bs => bs.map((b) => (b.id === book.id ? { ...b, isFavorite: nextFavorite } : b)));

    const current = get(books).find((b) => b.id === book.id);
    if (!current) return;

    try {
      await upsertBook({
        id: current.id,
        title: current.title,
        author: current.author || "",
        filePath: current.filePath,
        format: current.format,
        syncStatus: "local",
        currentPage: current.currentPage,
        totalPages: current.totalPages,
      });
    } catch (err: any) {
      books.update(bs => bs.map((b) => (b.id === book.id ? { ...b, isFavorite: Boolean(book.isFavorite) } : b)));
      error.set(err.message);
    }
  },

  async handleMarkCompleted(book: ReaderBook, onCompleted?: () => Promise<void>) {
    try {
      if (book.format.toLowerCase() === "epub") {
        if (onCompleted) await onCompleted();
      } else {
        await updateBookProgress(book.id, Math.max(1, book.totalPages || book.currentPage || 1));
      }

      books.update(bs => bs.map((b) =>
        b.id === book.id
          ? {
              ...b,
              currentPage: Math.max(b.currentPage, b.totalPages || b.currentPage),
              progressPercentage: 100,
              completed: true,
            }
          : b,
      ));

      await this.loadLibrary();
    } catch (err: any) {
      error.set(err.message);
    }
  },

  handleEditBook(book: Pick<ReaderBook, "id">) {
    const match = get(books).find((b) => b.id === book.id);
    editingBook.set(match ?? null);
  },

  async handleSaveEditedBook(updatedBook: LibraryBookDto) {
    try {
      const readerBook = get(books).find((b) => b.id === updatedBook.id);
      if (!readerBook) return;

      await upsertBook({
        id: updatedBook.id,
        title: updatedBook.title,
        author: updatedBook.author || "",
        filePath: readerBook.filePath,
        format: readerBook.format,
        syncStatus: "local",
        currentPage: readerBook.currentPage,
        totalPages: readerBook.totalPages,
      });

      books.update(bs => bs.map((b) =>
        b.id === updatedBook.id ? { ...b, title: updatedBook.title, author: updatedBook.author } : b,
      ));

      editingBook.set(null);
    } catch (err: any) {
      error.set(err.message);
    }
  },

  formatUpdatedAt(iso: string, t: (key: MessageKey, params?: Record<string, string | number>) => string): string {
    const parsed = new Date(iso);
    if (Number.isNaN(parsed.getTime())) {
      return t("settings.unknownBook");
    }
    return parsed.toLocaleDateString();
  },

  formatProgress(progress: number): string {
    return `${Math.round(progress)}%`;
  },

  getBulkImportStatusKey(status: string): MessageKey {
    if (status === BULK_IMPORT_STATUS.IMPORTING) {
      return "library.bulkImport.status.ingesting" as any;
    }
    if (status === BULK_IMPORT_STATUS.SUCCESS) {
      return "library.bulkImport.status.success" as any;
    }
    if (status === BULK_IMPORT_STATUS.SKIPPED) {
      return "library.bulkImport.status.skipped" as any;
    }
    if (status === BULK_IMPORT_STATUS.FAILED) {
      return "library.bulkImport.status.failed" as any;
    }
    if (status === BULK_IMPORT_STATUS.CANCELLED) {
      return "library.bulkImport.status.cancelled" as any;
    }
    return "library.bulkImport.status.queued" as any;
  },

  getBulkImportStatusClass(status: string): string {
    if (status === BULK_IMPORT_STATUS.SUCCESS) {
      return "text-emerald-700";
    }
    if (status === BULK_IMPORT_STATUS.FAILED) {
      return "text-red-700";
    }
    if (status === BULK_IMPORT_STATUS.IMPORTING) {
      return "text-blue-700";
    }
    if (status === BULK_IMPORT_STATUS.CANCELLED) {
      return "text-amber-700";
    }
    return "text-[var(--color-text-muted)]";
  },

  generateCollectionId(): string {
    return Math.random().toString(36).substring(2, 9);
  },

  getShelfMenuId(bookId: string): string {
    return `shelf-actions-menu-${bookId}`;
  }
};

export const COLLECTION_COLOR_OPTIONS = [
  "#6366f1", "#8b5cf6", "#ec4899", "#ef4444",
  "#f97316", "#eab308", "#22c55e", "#14b8a6", "#0ea5e9"
] as const;