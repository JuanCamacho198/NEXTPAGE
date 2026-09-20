<script lang="ts">
  import Panel from '$lib/shared/ui/layout/Panel.svelte';
  import SettingsPrivacySection from './SettingsPrivacySection.svelte';
  import Dropdown from '$lib/shared/ui/navigation/Dropdown.svelte';
  import type { MessageKey } from '$lib/shared/i18n';

  type Props = {
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    books: { id: string; title: string }[];
    isClearingCache: boolean;
    cacheCleared: boolean;
    selectedExportBook: string;
    selectedExportFormat: 'json' | 'markdown';
    isExportingHighlights: boolean;
    isExportingColdBackup?: boolean;
    isImportingColdBackup?: boolean;
    isExportingDictionary?: boolean;
    isImportingDictionary?: boolean;
    dictionaryExportError?: string | null;
    dictionaryImportResult?: string | null;
    dictionaryImportError?: string | null;
    isDriveConnected?: boolean;
    isConnectingDrive?: boolean;
    onClearCache: () => void;
    onExportLibrary: () => void;
    onExportHighlights: () => void;
    onExportColdBackup?: () => void;
    onImportColdBackup?: () => void;
    onExportDictionary?: (format: 'json' | 'csv') => void;
    onImportDictionary?: (file: File) => void;
    onConnectDrive?: () => void;
    onSelectedExportBookChange: (value: string) => void;
    onSelectedExportFormatChange: (value: 'json' | 'markdown') => void;
  };

  let {
    t,
    books,
    isClearingCache,
    cacheCleared,
    selectedExportBook,
    selectedExportFormat,
    isExportingHighlights,
    isExportingColdBackup = false,
    isImportingColdBackup = false,
    isExportingDictionary = false,
    isImportingDictionary = false,
    dictionaryExportError = null,
    dictionaryImportResult = null,
    dictionaryImportError = null,
    isDriveConnected = true,
    isConnectingDrive = false,
    onClearCache,
    onExportLibrary,
    onExportHighlights,
    onExportColdBackup = () => {},
    onImportColdBackup = () => {},
    onExportDictionary = () => {},
    onImportDictionary = () => {},
    onConnectDrive = () => {},
    onSelectedExportBookChange,
    onSelectedExportFormatChange,
  }: Props = $props();

  const exportBookOptions = $derived([
    { value: 'all', label: t('settings.data.allBooks') },
    ...(books ?? []).map((b: { id: string; title: string }) => ({ value: b.id, label: b.title })),
  ]);

  const exportFormatOptions = $derived([
    { value: 'json', label: 'JSON' },
    { value: 'markdown', label: t('settings.data.markdown') },
  ]);

  async function handleDictionaryFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    try {
      await onImportDictionary(file);
    } finally {
      input.value = '';
    }
  }
</script>

<Panel
  title={t('settings.data.exportLibrary')}
  subtitle={t('settings.data.exportLibraryDescription')}
>
  <button
    type="button"
    class="flex items-center gap-2.5 px-3 py-2.5 rounded-lg border border-(--color-border) bg-(--color-background) cursor-pointer transition-all duration-200 text-(--color-primary) text-xs hover:bg-(--color-surface) hover:border-(--color-text-muted)"
    onclick={onExportLibrary}
  >
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      ><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /><path
        d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"
      /></svg
    >
    <span>{t('settings.data.exportLibraryButton')}</span>
  </button>
</Panel>

<Panel
  title={t('settings.data.exportHighlights')}
  subtitle={t('settings.data.exportHighlightsDescription')}
>
  <section
    class="flex flex-col gap-2 p-3 bg-(--color-background) border border-(--color-border) rounded-lg"
  >
    <div class="flex gap-2">
      <Dropdown
        options={exportBookOptions}
        value={selectedExportBook}
        class="flex-1"
        onchange={({ value }) => onSelectedExportBookChange(value)}
      />
      <Dropdown
        options={exportFormatOptions}
        value={selectedExportFormat}
        class="w-[90px] shrink-0"
        onchange={({ value }) => onSelectedExportFormatChange(value as 'json' | 'markdown')}
      />
      <button
        type="button"
        class="flex items-center gap-2 px-4 py-2 rounded-md border border-(--color-primary) bg-(--color-primary) text-(--color-background) cursor-pointer transition-all duration-200 text-xs font-medium hover:opacity-90 disabled:opacity-60 disabled:cursor-not-allowed"
        onclick={onExportHighlights}
        disabled={isExportingHighlights}
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          ><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" /><polyline
            points="15 3 21 3 21 9"
          /><line x1="10" y1="14" x2="21" y2="3" /></svg
        >
        <span
          >{isExportingHighlights
            ? t('settings.data.exporting')
            : t('settings.data.download')}</span
        >
      </button>
    </div>
  </section>
</Panel>

<Panel title={t('settings.data.coldBackup')} subtitle={t('settings.data.coldBackupDescription')}>
  {#if !isDriveConnected}
    <div
      class="mb-2 flex flex-col gap-2 p-3 bg-(--color-background) border border-(--color-border) rounded-lg"
    >
      <p class="text-xs text-(--color-text-muted)">{t('settings.data.driveNotConnected')}</p>
      <button
        type="button"
        class="flex items-center justify-center gap-2 px-4 py-2 rounded-lg border border-(--color-primary) bg-(--color-primary) cursor-pointer transition-all duration-200 text-xs font-medium text-(--color-background) hover:opacity-90 disabled:opacity-60 disabled:cursor-not-allowed"
        onclick={onConnectDrive}
        disabled={isConnectingDrive || isExportingColdBackup || isImportingColdBackup}
      >
        <span
          >{isConnectingDrive
            ? t('settings.sync.drive.connecting')
            : t('settings.data.connectDrive')}</span
        >
      </button>
    </div>
  {/if}
  <div class="flex gap-2">
    <button
      type="button"
      class="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg border border-(--color-primary) bg-(--color-primary) text-(--color-background) cursor-pointer transition-all duration-200 text-xs font-medium hover:opacity-90 disabled:opacity-60 disabled:cursor-not-allowed"
      onclick={onExportColdBackup}
      disabled={isExportingColdBackup || isImportingColdBackup}
    >
      <span
        >{isExportingColdBackup
          ? t('settings.data.exporting')
          : t('settings.data.coldExport')}</span
      >
    </button>
    <button
      type="button"
      class="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg border border-(--color-border) bg-(--color-background) cursor-pointer transition-all duration-200 text-(--color-primary) text-xs hover:bg-(--color-surface) disabled:opacity-60 disabled:cursor-not-allowed"
      onclick={onImportColdBackup}
      disabled={isExportingColdBackup || isImportingColdBackup}
    >
      <span
        >{isImportingColdBackup
          ? t('settings.data.importing')
          : t('settings.data.coldImport')}</span
      >
    </button>
  </div>
</Panel>

<Panel
  title={t('settings.data.dictionary.title')}
  subtitle={t('settings.data.dictionary.description')}
>
  <div class="flex flex-wrap items-center gap-2" data-testid="dictionary-transfer-actions">
    <button
      type="button"
      class="flex items-center gap-2 rounded-lg border border-(--color-primary) bg-(--color-primary) px-4 py-2.5 text-xs font-medium text-(--color-background) cursor-pointer transition-all duration-200 hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
      onclick={() => onExportDictionary('json')}
      disabled={isExportingDictionary || isImportingDictionary}
    >
      <span>{t('settings.data.dictionary.exportJson')}</span>
    </button>
    <button
      type="button"
      class="flex items-center gap-2 rounded-lg border border-(--color-border) bg-(--color-background) px-4 py-2.5 text-xs text-(--color-primary) cursor-pointer transition-all duration-200 hover:bg-(--color-surface) disabled:cursor-not-allowed disabled:opacity-60"
      onclick={() => onExportDictionary('csv')}
      disabled={isExportingDictionary || isImportingDictionary}
    >
      <span>{t('settings.data.dictionary.exportCsv')}</span>
    </button>
    <label
      class="flex cursor-pointer items-center gap-2 rounded-lg border border-(--color-border) bg-(--color-background) px-4 py-2.5 text-xs text-(--color-primary) transition-all duration-200 hover:bg-(--color-surface)"
      class:pointer-events-none={isExportingDictionary || isImportingDictionary}
      class:opacity-60={isExportingDictionary || isImportingDictionary}
    >
      <input
        type="file"
        accept=".json,.csv"
        class="hidden"
        data-testid="dictionary-import-input"
        onchange={handleDictionaryFile}
        disabled={isExportingDictionary || isImportingDictionary}
      />
      <span>{t('settings.data.dictionary.import')}</span>
    </label>
  </div>
  {#if dictionaryExportError}
    <p class="mt-2 text-xs text-red-500" data-testid="dictionary-export-error">
      {dictionaryExportError}
    </p>
  {/if}
  {#if dictionaryImportResult}
    <p class="mt-2 text-xs text-green-600" data-testid="dictionary-import-result">
      {dictionaryImportResult}
    </p>
  {/if}
  {#if dictionaryImportError}
    <p class="mt-2 text-xs text-amber-600" data-testid="dictionary-import-error">
      {dictionaryImportError}
    </p>
  {/if}
</Panel>

<Panel title={t('settings.data.clearCache')} subtitle={t('settings.data.clearCacheDescription')}>
  <button
    type="button"
    class="flex items-center gap-2.5 px-3 py-2.5 rounded-lg border border-red-300 bg-(--color-background) cursor-pointer transition-all duration-200 text-red-500 text-xs hover:bg-red-50 hover:border-red-500 disabled:opacity-60 disabled:cursor-not-allowed"
    onclick={onClearCache}
    disabled={isClearingCache}
  >
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      ><polyline points="3 6 5 6 21 6" /><path
        d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
      /></svg
    >
    <span
      >{isClearingCache
        ? t('settings.data.clearing')
        : cacheCleared
          ? t('settings.data.cleared')
          : t('settings.data.clearCache')}</span
    >
  </button>
</Panel>

<SettingsPrivacySection {t} />
