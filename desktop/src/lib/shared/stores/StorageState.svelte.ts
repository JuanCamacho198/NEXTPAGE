import { invoke } from '$lib/shared/api/invokeWrapper';

export type StorageStats = {
  totalBytes: number;
  dbBytes: number;
  coversBytes: number;
  tempBytes: number;
  cacheBytes: number;
  coverBytes: number;
  driveBytesEstimate: number | null;
};

export type PerBookSize = {
  id: string;
  title: string;
  bytes: number;
};

export type AutoBackupConfig = {
  enabled: boolean;
  frequency: '1h' | '6h' | '24h';
  retention: 3 | 7 | 30;
  onAuth: boolean;
};

const AUTO_BACKUP_KEY = 'storage.autoBackup';

function loadAutoBackup(): AutoBackupConfig {
  try {
    const raw = localStorage.getItem(AUTO_BACKUP_KEY);
    if (raw) return JSON.parse(raw) as AutoBackupConfig;
  } catch {}
  return { enabled: false, frequency: '24h', retention: 7, onAuth: false };
}

function persistAutoBackup(cfg: AutoBackupConfig): void {
  try {
    localStorage.setItem(AUTO_BACKUP_KEY, JSON.stringify(cfg));
  } catch {}
}

export function createStorageState() {
  let stats = $state<StorageStats | null>(null);
  let perBookSizes = $state<PerBookSize[]>([]);
  let isLoading = $state(false);
  let error = $state<string | null>(null);
  let isClearing = $state(false);
  let clearProgress = $state<number | null>(null);
  let autoBackup = $state<AutoBackupConfig>(loadAutoBackup());

  async function loadStats(): Promise<void> {
    isLoading = true;
    error = null;
    try {
      const res = await invoke<StorageStats>('getStorageStats');
      stats = res;
      if (res.driveBytesEstimate === null) {
        console.warn('[storageState] driveBytesEstimate null (Drive unavailable)');
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      error = msg;
      stats = null;
    } finally {
      isLoading = false;
    }
  }

  async function clearCache(kind: 'covers' | 'temp' | 'all', deep = false): Promise<{ freedBytes: number }> {
    isClearing = true;
    clearProgress = 0;
    error = null;
    try {
      const res = await invoke<{ freedBytes: number }>('clearCache', { kind, deep });
      clearProgress = 100;
      await loadStats();
      return res;
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      error = msg;
      throw e;
    } finally {
      isClearing = false;
      setTimeout(() => (clearProgress = null), 800);
    }
  }

  async function getPerBookSizes(): Promise<PerBookSize[]> {
    try {
      const res = await invoke<PerBookSize[]>('getPerBookSizes');
      perBookSizes = res;
      return res;
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
      throw e;
    }
  }

  async function deleteBookData(bookId: string): Promise<void> {
    await invoke('deleteBookData', { bookId });
    await Promise.all([loadStats(), getPerBookSizes()]);
  }

  async function cleanupOrphans(): Promise<{ removed: number }> {
    const res = await invoke<{ removed: number }>('cleanupOrphans');
    await loadStats();
    return res;
  }

  function setAutoBackup(patch: Partial<AutoBackupConfig>): void {
    autoBackup = { ...autoBackup, ...patch };
    persistAutoBackup(autoBackup);
  }

  return {
    get stats() {
      return stats;
    },
    get perBookSizes() {
      return perBookSizes;
    },
    get isLoading() {
      return isLoading;
    },
    get error() {
      return error;
    },
    get isClearing() {
      return isClearing;
    },
    get clearProgress() {
      return clearProgress;
    },
    get autoBackup() {
      return autoBackup;
    },
    loadStats,
    clearCache,
    getPerBookSizes,
    deleteBookData,
    cleanupOrphans,
    setAutoBackup,
  };
}

export const storageState = createStorageState();
