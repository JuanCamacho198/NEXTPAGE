import type { BulkImportSummary, ScanFolderResult } from "$lib/types";
import type { BulkImportProgress } from "../../services/BulkImportService";
import type { MessageKey } from "$lib/i18n";

export const STATUS = {
  QUEUED: "queued",
  IMPORTING: "importing",
  SUCCESS: "success",
  SKIPPED: "skipped",
  FAILED: "failed",
  CANCELLED: "cancelled",
} as const;

export type Props = {
  open: boolean;
  folderName: string | null;
  folderPath: string | null;
  scanResult: ScanFolderResult | null;
  isScanning: boolean;
  scanError: string | null;
  isImporting: boolean;
  importProgress: BulkImportProgress | null;
  importSummary: BulkImportSummary | null;
  onClose: () => void;
  onPickFolder: () => void;
  onScan: () => void;
  onStartImport: () => void;
  onCancelImport: () => void;
  t: (key: MessageKey, params?: Record<string, string | number>) => string;
};

export function getStatusKey(status: string): MessageKey {
  if (status === STATUS.IMPORTING) {
    return "library.bulkImport.status.importing";
  }
  if (status === STATUS.SUCCESS) {
    return "library.bulkImport.status.success";
  }
  if (status === STATUS.SKIPPED) {
    return "library.bulkImport.status.skipped";
  }
  if (status === STATUS.FAILED) {
    return "library.bulkImport.status.failed";
  }
  if (status === STATUS.CANCELLED) {
    return "library.bulkImport.status.cancelled";
  }
  return "library.bulkImport.status.queued";
}

export function getStatusClass(status: string): string {
  if (status === STATUS.SUCCESS) {
    return "text-emerald-700";
  }
  if (status === STATUS.FAILED) {
    return "text-red-700";
  }
  if (status === STATUS.IMPORTING) {
    return "text-blue-700";
  }
  if (status === STATUS.CANCELLED) {
    return "text-amber-700";
  }
  return "text-[var(--color-text-muted)]";
}