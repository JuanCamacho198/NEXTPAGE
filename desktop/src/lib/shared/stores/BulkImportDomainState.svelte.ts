import { importBook, type ImportProgress } from "$lib/shared/services/BookImportService";
import {
  BulkImportService,
  type BulkImportProgress,
} from "$lib/shared/services/BulkImportService";
import { pickFile, pickFolder } from "$lib/shared/services/FilePicker";
import { extractPdfMetadata } from "$lib/shared/services/pdfThumbnail";
import type {
  BulkImportSummary,
  ScanFolderResult,
} from "$lib/shared/types";

class BulkImportDomainState {
  // ─── State ───
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
  importProgress = $state<ImportProgress | null>(null);

  // Internal
  bulkImportService = new BulkImportService();

  // ─── Callback for post-import refresh ───
  onLibraryRefreshNeeded: (() => Promise<void>) | null = null;

  // ─── Single file import ───

  async handleImportFile(): Promise<void> {
    const file = await pickFile();
    if (!file) {
      return;
    }

    this.isImporting = true;

    try {
      const format = file.name.toLowerCase().endsWith(".epub") ? "epub" : "pdf";
      const title = file.name.replace(/\.(pdf|epub)$/i, "");

      let author: string | undefined;
      if (format === "pdf") {
        try {
          const meta = await extractPdfMetadata(file.path);
          if (meta.author) {
            author = meta.author;
          }
        } catch {
          // best-effort
        }
      }

      await importBook(
        {
          sourcePath: file.path,
          title,
          author,
          format,
        },
        (progress) => {
          this.importProgress = progress;
        },
      );

      await this.onLibraryRefreshNeeded?.();
    } catch (error) {
      // Error is propagated via readerError in coordinator
      throw error;
    } finally {
      this.isImporting = false;
      this.importProgress = null;
    }
  }

  // ─── Bulk import ───

  openBulkImportModal(): void {
    this.isBulkImportOpen = true;
  }

  closeBulkImportModal(): void {
    if (this.isBulkImporting) {
      this.bulkImportService.cancel();
    }

    this.isBulkImportOpen = false;
    this.isBulkScanning = false;
    this.bulkScanError = null;
    this.bulkImportProgress = null;
    this.bulkImportSummary = null;
  }

  async handlePickBulkImportFolder(folderTitle: string): Promise<void> {
    const selected = await pickFolder(folderTitle);
    if (!selected) {
      return;
    }

    this.bulkImportFolderPath = selected.path;
    this.bulkImportFolderName = selected.name;
    this.bulkScanResult = null;
    this.bulkScanError = null;
    this.bulkImportProgress = null;
    this.bulkImportSummary = null;
  }

  async handleScanBulkImportFolder(): Promise<void> {
    if (!this.bulkImportFolderPath) {
      return;
    }

    this.isBulkScanning = true;
    this.bulkScanError = null;

    try {
      const { scanFolder } = await import("$lib/shared/api/tauriClient");
      this.bulkScanResult = await scanFolder(this.bulkImportFolderPath);
    } catch (error) {
      this.bulkScanError = error instanceof Error ? error.message : "Import failed";
    } finally {
      this.isBulkScanning = false;
    }
  }

  handleCancelBulkImport(): void {
    this.bulkImportService.cancel();
  }

  async handleStartBulkImport(): Promise<void> {
    if (!this.bulkImportFolderPath || !this.bulkScanResult || this.bulkScanResult.files.length === 0) {
      return;
    }

    this.isBulkImporting = true;
    this.bulkScanError = null;
    this.bulkImportProgress = null;
    this.bulkImportSummary = null;

    try {
      const summary = await this.bulkImportService.importFolder(
        this.bulkImportFolderPath,
        (progress) => {
          this.bulkImportProgress = progress;
        },
      );

      this.bulkImportSummary = summary;

      if (summary.success > 0 || summary.skipped > 0 || summary.failed > 0 || summary.cancelled > 0) {
        await this.onLibraryRefreshNeeded?.();
      }
    } catch (error) {
      this.bulkScanError = error instanceof Error ? error.message : "Import failed";
    } finally {
      this.isBulkImporting = false;
    }
  }
}

export const bulkImportState = new BulkImportDomainState();
export { BulkImportDomainState };
