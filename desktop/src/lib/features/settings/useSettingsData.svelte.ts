import { storageState as defaultStorageState } from '$lib/shared/stores/storageState.svelte';
import { DriveColdBackupService as DefaultDriveColdBackupService } from '$lib/shared/services/DriveColdBackupService';
import { authState as defaultAuthState } from '$lib/stores/authState.svelte';
import { pushToast as defaultPushToast } from '$lib/stores/toastQueue.svelte';

export type DataDeps = {
  storageState?: typeof defaultStorageState;
  DriveColdBackupService?: typeof DefaultDriveColdBackupService;
  authState?: typeof defaultAuthState;
  pushToast?: typeof defaultPushToast;
  t?: (key: string, params?: Record<string, string | number>) => string;
};

export function createSettingsData(deps: DataDeps = {}): {
  isClearingCache: boolean;
  cacheCleared: boolean;
  selectedExportBook: string;
  selectedExportFormat: 'json' | 'markdown';
  isExportingHighlights: boolean;
  isExportingColdBackup: boolean;
  isImportingColdBackup: boolean;
  isSaving: boolean;
  isDirty: boolean;
  handleClearCache: () => Promise<void>;
  handleExportHighlights: () => Promise<void>;
  handleExportColdBackup: () => Promise<void>;
  handleImportColdBackup: () => Promise<void>;
  handleSelectedExportBookChange: (value: string) => void;
  handleSelectedExportFormatChange: (value: 'json' | 'markdown') => void;
} {
  const storage = deps.storageState ?? defaultStorageState;
  const ColdBackup = deps.DriveColdBackupService ?? DefaultDriveColdBackupService;
  const auth = deps.authState ?? defaultAuthState;
  const pushToast = deps.pushToast ?? defaultPushToast;
  const t = deps.t ?? ((k: string) => k);

  let isClearingCache = $state(false);
  let cacheCleared = $state(false);
  let selectedExportBook = $state('all');
  let selectedExportFormat = $state<'json' | 'markdown'>('json');
  let isExportingHighlights = $state(false);
  let isExportingColdBackup = $state(false);
  let isImportingColdBackup = $state(false);

  const isSaving = $derived(isClearingCache || isExportingHighlights || isExportingColdBackup || isImportingColdBackup);
  const isDirty = $derived(selectedExportBook !== 'all' || selectedExportFormat !== 'json');

  async function handleClearCache(): Promise<void> {
    isClearingCache = true;
    try {
      await storage.clearCache('temp', false);
      cacheCleared = true;
      pushToast('success', t('settings.data.cacheClearedToast'));
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      if (msg.includes('storage.permission_denied')) {
        pushToast('error', 'storage.permission_denied');
      } else {
        pushToast('error', msg);
      }
    } finally {
      isClearingCache = false;
    }
  }

  async function handleExportHighlights(): Promise<void> {
    isExportingHighlights = true;
    try {
      await new Promise((resolve) => setTimeout(resolve, 500));
    } finally {
      isExportingHighlights = false;
    }
  }

  async function handleExportColdBackup(): Promise<void> {
    const userId = auth.userId;
    if (!userId) {
      pushToast('error', t('errors.commandFailure'));
      return;
    }
    isExportingColdBackup = true;
    try {
      await ColdBackup.exportColdBackup(userId);
      pushToast('success', t('settings.data.exportSuccess'));
    } catch (e) {
      pushToast('error', e instanceof Error ? e.message : t('errors.commandFailure'));
    } finally {
      isExportingColdBackup = false;
    }
  }

  async function handleImportColdBackup(): Promise<void> {
    const userId = auth.userId;
    if (!userId) {
      pushToast('error', t('errors.commandFailure'));
      return;
    }
    isImportingColdBackup = true;
    try {
      await ColdBackup.importColdBackup(userId);
      pushToast('success', t('settings.data.importSuccess'));
    } catch (e) {
      pushToast('error', e instanceof Error ? e.message : t('errors.importCommandFailed'));
    } finally {
      isImportingColdBackup = false;
    }
  }

  function handleSelectedExportBookChange(value: string): void {
    selectedExportBook = value;
  }

  function handleSelectedExportFormatChange(value: 'json' | 'markdown'): void {
    selectedExportFormat = value;
  }

  return {
    get isClearingCache() { return isClearingCache; },
    set isClearingCache(v: boolean) { isClearingCache = v; },
    get cacheCleared() { return cacheCleared; },
    set cacheCleared(v: boolean) { cacheCleared = v; },
    get selectedExportBook() { return selectedExportBook; },
    set selectedExportBook(v: string) { selectedExportBook = v; },
    get selectedExportFormat() { return selectedExportFormat; },
    set selectedExportFormat(v: 'json' | 'markdown') { selectedExportFormat = v; },
    get isExportingHighlights() { return isExportingHighlights; },
    set isExportingHighlights(v: boolean) { isExportingHighlights = v; },
    get isExportingColdBackup() { return isExportingColdBackup; },
    set isExportingColdBackup(v: boolean) { isExportingColdBackup = v; },
    get isImportingColdBackup() { return isImportingColdBackup; },
    set isImportingColdBackup(v: boolean) { isImportingColdBackup = v; },
    get isSaving() { return isSaving; },
    get isDirty() { return isDirty; },
    handleClearCache,
    handleExportHighlights,
    handleExportColdBackup,
    handleImportColdBackup,
    handleSelectedExportBookChange,
    handleSelectedExportFormatChange,
  };
}
