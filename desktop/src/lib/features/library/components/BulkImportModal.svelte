<script lang="ts">
  import { Button, Modal } from "$lib/shared/ui";
  import type { BulkImportSummary, ScanFolderResult } from "$lib/shared/types";
  import type { BulkImportProgress } from "$lib/shared/services/BulkImportService";
  import type { MessageKey } from "$lib/shared/i18n";
  import {
    BULK_IMPORT_STATUS,
    LibraryState,
  } from "../state";

  let {
    open,
    folderName,
    folderPath,
    scanResult,
    isScanning,
    scanError,
    isImporting,
    importProgress,
    importSummary,
    onClose,
    onPickFolder,
    onScan,
    onStartImport,
    onCancelImport,
    t,
  }: {
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
  } = $props();

  const libState = new LibraryState();

  const effectiveSummary = $derived(importProgress?.summary ?? importSummary ?? null);
  const currentFileName = $derived(importProgress?.currentFile?.fileName ?? null);
  const canStartImport = $derived(
    !isScanning && !isImporting && !!folderPath && !!scanResult && scanResult.files.length > 0,
  );

  const fallbackRows = $derived.by(() => {
    if (!scanResult) {
      return [];
    }

    return scanResult.files.map((file) => ({
      file,
      status: file.isDuplicate ? BULK_IMPORT_STATUS.SKIPPED : BULK_IMPORT_STATUS.QUEUED,
      message: file.isDuplicate ? t("library.bulkImport.duplicate") : null,
    }));
  });
</script>

<Modal bind:open={open} title={t("library.bulkImport.title")}>
  {#snippet children()}
    <div class="mb-4 rounded-lg border border-[color:var(--color-border)] bg-[var(--color-background)] p-3">
      <div class="mb-2 flex flex-wrap items-center gap-2">
        <Button variant="secondary" size="sm" onclick={onPickFolder} disabled={isScanning || isImporting}>
          {t("library.bulkImport.selectFolder")}
        </Button>
        <Button variant="secondary" size="sm" onclick={onScan} disabled={!folderPath || isScanning || isImporting}>
          {isScanning ? t("library.bulkImport.scanning") : t("library.bulkImport.scan")}
        </Button>
        <Button onclick={onStartImport} size="sm" disabled={!canStartImport}>
          {isImporting ? t("library.bulkImport.importing") : t("library.bulkImport.confirm")}
        </Button>
        {#if isImporting}
          <Button variant="danger" size="sm" onclick={onCancelImport}>
            {t("library.bulkImport.cancel")}
          </Button>
        {/if}
      </div>

      <p class="text-xs text-[var(--color-text-muted)]">
        {folderName
          ? t("library.bulkImport.selectedFolder", { name: folderName })
          : t("library.bulkImport.noFolder")}
      </p>

      {#if scanResult}
        <div class="mt-2 flex flex-wrap gap-2 text-xs text-[var(--color-text-muted)]">
          <span>{t("library.bulkImport.filesFound", { count: scanResult.files.length })}</span>
          <span>{t("library.bulkImport.skippedUnsupported", { count: scanResult.skippedUnsupportedCount })}</span>
          <span>{t("library.bulkImport.skippedUnreadable", { count: scanResult.skippedUnreadableCount })}</span>
        </div>
      {/if}
    </div>

    {#if scanError}
      <p class="mb-3 rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-900">{scanError}</p>
    {/if}

    {#if effectiveSummary}
      <div class="mb-3 rounded-lg border border-[color:var(--color-border)] bg-[var(--color-background)] p-3 text-xs text-[var(--color-text-muted)]">
        <div class="mb-2 flex flex-wrap gap-2">
          <span>{t("library.bulkImport.summary.total", { count: effectiveSummary.total })}</span>
          <span>{t("library.bulkImport.summary.queued", { count: effectiveSummary.queued })}</span>
          <span>{t("library.bulkImport.summary.importing", { count: effectiveSummary.importing })}</span>
          <span>{t("library.bulkImport.summary.success", { count: effectiveSummary.success })}</span>
          <span>{t("library.bulkImport.summary.skipped", { count: effectiveSummary.skipped })}</span>
          <span>{t("library.bulkImport.summary.failed", { count: effectiveSummary.failed })}</span>
          <span>{t("library.bulkImport.summary.cancelled", { count: effectiveSummary.cancelled })}</span>
        </div>
        {#if currentFileName}
          <p>{t("library.bulkImport.currentFile", { name: currentFileName })}</p>
        {/if}
      </div>
    {/if}

    <div class="min-h-0 flex-1 overflow-y-auto rounded-lg border border-[color:var(--color-border)] bg-[var(--color-background)]">
      {#if (effectiveSummary?.results.length ?? 0) > 0}
        <ul class="divide-y divide-[color:var(--color-border)]">
          {#each effectiveSummary?.results ?? [] as row}
            <li class="p-3">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <p class="truncate text-sm font-medium text-[var(--color-primary)]">{row.file.fileName}</p>
                <span class={`text-xs font-semibold uppercase ${libState.getBulkImportStatusClass(row.status)}`}>
                  {t(libState.getBulkImportStatusKey(row.status))}
                </span>
              </div>
              <p class="text-xs text-[var(--color-text-muted)]">{row.file.format.toUpperCase()}</p>
              {#if row.message}
                <p class={`mt-1 text-xs ${row.status === BULK_IMPORT_STATUS.FAILED ? "text-red-700" : "text-[var(--color-text-muted)]"}`}>
                  {row.message}
                </p>
              {/if}
            </li>
          {/each}
        </ul>
      {:else if fallbackRows.length > 0}
        <ul class="divide-y divide-[color:var(--color-border)]">
          {#each fallbackRows as row}
            <li class="p-3">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <p class="truncate text-sm font-medium text-[var(--color-primary)]">{row.file.fileName}</p>
                <span class={`text-xs font-semibold uppercase ${libState.getBulkImportStatusClass(row.status)}`}>
                  {t(libState.getBulkImportStatusKey(row.status))}
                </span>
              </div>
              <p class="text-xs text-[var(--color-text-muted)]">{row.file.format.toUpperCase()}</p>
              {#if row.message}
                <p class="mt-1 text-xs text-[var(--color-text-muted)]">{row.message}</p>
              {/if}
            </li>
          {/each}
        </ul>
      {:else}
        <p class="p-4 text-sm text-[var(--color-text-muted)]">{t("library.bulkImport.noFiles")}</p>
      {/if}
    </div>
  {/snippet}

  {#snippet footer()}
    <Button variant="secondary" size="sm" onclick={onClose}>
      {t("library.bulkImport.close")}
    </Button>
  {/snippet}
</Modal>