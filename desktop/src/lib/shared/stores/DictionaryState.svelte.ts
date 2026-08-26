import { invoke } from '$lib/shared/api/invokeWrapper';
import type { DictionaryWordDto } from '$lib/shared/types';
import { authState } from '$lib/shared/stores/AuthState.svelte';
import { hasLiveSession } from '$lib/services/supabase';
import { SupabaseDictionarySync } from '$lib/shared/sync/SupabaseDictionarySync';

function normalize(word: string): string {
  return word.trim().toLowerCase();
}

function stripAccents(s: string): string {
  return s.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
}

function normalizedForSearch(word: string): string {
  return stripAccents(normalize(word));
}

function levenshtein(a: string, b: string): number {
  if (a.length === 0) return b.length;
  if (b.length === 0) return a.length;
  const prev: number[] = Array.from({ length: b.length + 1 }, (_, i) => i);
  const cur: number[] = new Array(b.length + 1);
  for (let i = 0; i < a.length; i++) {
    cur[0] = i + 1;
    for (let j = 0; j < b.length; j++) {
      const cost = a[i] === b[j] ? 0 : 1;
      cur[j + 1] = Math.min(prev[j + 1] + 1, cur[j] + 1, prev[j] + cost);
    }
    for (let k = 0; k < prev.length; k++) prev[k] = cur[k];
  }
  return prev[b.length];
}

export function createDictionaryState() {
  let words = $state<DictionaryWordDto[]>([]);
  let isLoading = $state(false);
  let error = $state<string | null>(null);
  let realtimeUnsub: (() => void) | null = null;
  let dictSync: SupabaseDictionarySync | null = null;
  let syncEnabled = $state(true);

  async function load(): Promise<void> {
    isLoading = true;
    error = null;
    try {
      words = await invoke<DictionaryWordDto[]>('listDictionaryWords');
    } catch (e) {
      error = e instanceof Error ? e.message : 'Failed to load dictionary';
      words = [];
    } finally {
      isLoading = false;
    }
  }

  function queueOutbox(entityId: string, operation: 'UPSERT' | 'DELETE', payload: Record<string, unknown>): void {
    if (!syncEnabled || !hasLiveSession() || !authState.userId) return;
    const now = new Date().toISOString();
    const enriched = { ...payload, userId: authState.userId, updatedAt: now };
    // fire-and-forget coalesced outbox
    void invoke('addCoalescedSyncOutboxItem', {
      entityType: 'DICTIONARY_WORD',
      entityId,
      operation,
      payloadJson: JSON.stringify(enriched),
    }).catch(() => {});
  }

  async function add(word: string, opts?: { tags?: string[]; isFavorite?: boolean; srsStage?: number }): Promise<DictionaryWordDto> {
    const now = new Date().toISOString();
    const created: DictionaryWordDto = await invoke<DictionaryWordDto>('addDictionaryWord', {
      payload: { word, tags: opts?.tags, isFavorite: opts?.isFavorite, srsStage: opts?.srsStage, userId: authState.userId ?? undefined },
    });
    words = [...words, created].sort((a, b) => (a.word ?? '').localeCompare(b.word ?? ''));
    queueOutbox(created.id, 'UPSERT', {
      word: created.word,
      normalizedWord: normalizedForSearch(created.word),
      tags: opts?.tags ?? [],
      isFavorite: opts?.isFavorite ?? false,
      srsStage: opts?.srsStage ?? 0,
      updatedAt: now,
      createdAt: now,
    });
    return created;
  }

  async function update(id: string, patch: { word?: string; tags?: string[]; isFavorite?: boolean; srsStage?: number }): Promise<DictionaryWordDto> {
    const now = new Date().toISOString();
    const updated: DictionaryWordDto = await invoke<DictionaryWordDto>('updateDictionaryWord', {
      payload: { id, ...patch },
    });
    words = words.map((w) => (w.id === id ? updated : w));
    queueOutbox(id, 'UPSERT', {
      word: updated.word,
      normalizedWord: normalizedForSearch(updated.word),
      tags: patch.tags ?? updated.tags ?? [],
      isFavorite: patch.isFavorite ?? updated.isFavorite ?? false,
      srsStage: patch.srsStage ?? updated.srsStage ?? 0,
      updatedAt: now,
      createdAt: updated.createdAt,
    });
    return updated;
  }

  async function remove(id: string): Promise<void> {
    await invoke('removeDictionaryWord', { id });
    words = words.filter((w) => w.id !== id);
    const now = new Date().toISOString();
    if (syncEnabled && hasLiveSession() && authState.userId) {
      void invoke('addCoalescedSyncOutboxItem', {
        entityType: 'DICTIONARY_WORD',
        entityId: id,
        operation: 'DELETE',
        payloadJson: JSON.stringify({ userId: authState.userId, updatedAt: now, deletedAt: now }),
      }).catch(() => {});
    }
  }

  async function toggleFavorite(id: string): Promise<void> {
    const found = words.find((w) => w.id === id);
    if (!found) return;
    const next = !found.isFavorite;
    await update(id, { isFavorite: next });
  }

  function search(query: string, limit = 20): DictionaryWordDto[] {
    const q = normalizedForSearch(query);
    if (!q) return words.slice(0, limit);
    const scored = words
      .map((w) => {
        const n = normalizedForSearch(w.word ?? '');
        let score = 99;
        if (n === q) score = 0;
        else if (n.startsWith(q)) score = 1;
        else if (n.includes(q)) score = 2;
        else if (levenshtein(n, q) <= 2) score = 3;
        else score = 99;
        return { w, score };
      })
      .filter((x) => x.score < 99)
      .sort((a, b) => a.score - b.score || (b.w.updatedAt ?? '').localeCompare(a.w.updatedAt ?? ''))
      .slice(0, limit)
      .map((x) => x.w);
    return scored;
  }

  async function exportData(format: 'json' | 'csv'): Promise<string> {
    return await invoke<string>('exportDictionary', { format });
  }

  async function importData(payload: string, format: 'json' | 'csv'): Promise<{ imported: number; errors: { row: number; reason: string }[] }> {
    const res = await invoke<{ imported: number; errors: { row: number; reason: string }[] }>('importDictionary', {
      payload,
      format,
      userId: authState.userId ?? null,
    });
    await load();
    return res;
  }

  function subscribeToRemoteChanges(): void {
    if (!authState.userId || !hasLiveSession()) return;
    if (realtimeUnsub) return;
    dictSync = new SupabaseDictionarySync(authState.userId);
    realtimeUnsub = dictSync.subscribeToDictionary((row) => {
      // LWW: keep newer updatedAt, tie breaker createdAt
      const local = words.find((w) => w.id === row.id || normalizedForSearch(w.word ?? '') === row.normalizedWord);
      if (row.deletedAt) {
        words = words.filter((w) => w.id !== row.id);
        return;
      }
      if (local) {
        const localUpdated = local.updatedAt ?? local.createdAt;
        if (row.updatedAt > localUpdated) {
          words = words.map((w) => (w.id === local.id ? { ...w, word: row.word, tags: row.tags, isFavorite: row.isFavorite, srsStage: row.srsStage, updatedAt: row.updatedAt } : w));
        } else if (row.updatedAt === localUpdated && row.createdAt > (local.createdAt ?? '')) {
          words = words.map((w) => (w.id === local.id ? { ...w, word: row.word } : w));
        }
      } else {
        const dto: DictionaryWordDto = {
          id: row.id,
          word: row.word,
          createdAt: row.createdAt,
          normalizedWord: row.normalizedWord,
          userId: row.userId,
          tags: row.tags,
          isFavorite: row.isFavorite,
          srsStage: row.srsStage,
          updatedAt: row.updatedAt,
          deletedAt: row.deletedAt ?? null,
          syncedAt: null,
        } as unknown as DictionaryWordDto;
        words = [...words, dto].sort((a, b) => (a.word ?? '').localeCompare(b.word ?? ''));
      }
    });
  }

  function unsubscribe(): void {
    if (realtimeUnsub) {
      try {
        realtimeUnsub();
      } catch {}
      realtimeUnsub = null;
    }
    if (dictSync) {
      try {
        dictSync.destroy();
      } catch {}
      dictSync = null;
    }
  }

  return {
    get words() {
      return words;
    },
    get isLoading() {
      return isLoading;
    },
    get error() {
      return error;
    },
    get syncEnabled() {
      return syncEnabled;
    },
    set syncEnabled(v: boolean) {
      syncEnabled = v;
    },
    load,
    add,
    update,
    remove,
    toggleFavorite,
    search,
    exportData,
    importData,
    subscribeToRemoteChanges,
    unsubscribe,
  };
}

export const dictionaryState = createDictionaryState();
