import { importBook as importSingleBook } from "./BookImportService";
import { scanFolder } from "$lib/api/tauriClient";
import {
  BULK_IMPORT_ITEM_STATUS,
  type BulkImportItemResult,
  type BulkImportSummary,
  type ScannedFile,
} from "$lib/types";

export type BulkImportProgress = {
  summary: BulkImportSummary;
  currentFile: ScannedFile | null;
};

type ProgressCallback = (progress: BulkImportProgress) => void;

const stripKnownExtension = (fileName: string): string => {
  return fileName.replace(/\.(pdf|epub)$/i, "");
};

const readErrorMessage = (error: unknown): string => {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }

  if (typeof error === "string" && error.trim().length > 0) {
    return error;
  }

  return "Import failed";
};

const buildSummary = (results: BulkImportItemResult[]): BulkImportSummary => {
  let queued = 0;
  let importing = 0;
  let success = 0;
  let skipped = 0;
  let failed = 0;
  let cancelled = 0;

  for (const result of results) {
    if (result.status === BULK_IMPORT_ITEM_STATUS.QUEUED) {
      queued += 1;
      continue;
    }

    if (result.status === BULK_IMPORT_ITEM_STATUS.IMPORTING) {
      importing += 1;
      continue;
    }

    if (result.status === BULK_IMPORT_ITEM_STATUS.SUCCESS) {
      success += 1;
      continue;
    }

    if (result.status === BULK_IMPORT_ITEM_STATUS.SKIPPED) {
      skipped += 1;
      continue;
    }

    if (result.status === BULK_IMPORT_ITEM_STATUS.FAILED) {
      failed += 1;
      continue;
    }

    if (result.status === BULK_IMPORT_ITEM_STATUS.CANCELLED) {
      cancelled += 1;
    }
  }

  return {
    total: results.length,
    queued,
    importing,
    success,
    skipped,
    failed,
    cancelled,
    results,
  };
};

const markRemainingAsCancelled = (results: BulkImportItemResult[]) => {
  for (const result of results) {
    if (result.status === BULK_IMPORT_ITEM_STATUS.QUEUED) {
      result.status = BULK_IMPORT_ITEM_STATUS.CANCELLED;
      result.message = "Cancelled";
    }
  }
};

export class BulkImportService {
  private cancelled = false;

  cancel() {
    this.cancelled = true;
  }

  async importFolder(path: string, onProgress?: ProgressCallback): Promise<BulkImportSummary> {
    this.cancelled = false;
    const scanned = await scanFolder(path);

    const results: BulkImportItemResult[] = scanned.files.map((file) => {
      if (file.isDuplicate) {
        return {
          file,
          status: BULK_IMPORT_ITEM_STATUS.SKIPPED,
          bookId: null,
          message: "Duplicate filename in library",
        };
      }

      return {
        file,
        status: BULK_IMPORT_ITEM_STATUS.QUEUED,
        bookId: null,
        message: null,
      };
    });

    const emit = (currentFile: ScannedFile | null) => {
      onProgress?.({
        summary: buildSummary(results),
        currentFile,
      });
    };

    emit(null);

    for (const result of results) {
      if (result.status !== BULK_IMPORT_ITEM_STATUS.QUEUED) {
        continue;
      }

      if (this.cancelled) {
        break;
      }

      result.status = BULK_IMPORT_ITEM_STATUS.IMPORTING;
      result.message = null;
      emit(result.file);

      try {
        const importedBook = await importSingleBook({
          sourcePath: result.file.fullPath,
          title: stripKnownExtension(result.file.fileName),
          format: result.file.format,
        });

        result.status = BULK_IMPORT_ITEM_STATUS.SUCCESS;
        result.bookId = importedBook.id;
        result.message = null;
      } catch (error) {
        result.status = BULK_IMPORT_ITEM_STATUS.FAILED;
        result.message = readErrorMessage(error);
      }

      emit(result.file);

      if (this.cancelled) {
        break;
      }
    }

    if (this.cancelled) {
      markRemainingAsCancelled(results);
      emit(null);
    }

    return buildSummary(results);
  }
}
