import { getSessionClient, hasLiveSession } from '$lib/services/supabase';
import { authState } from '$lib/shared/stores/AuthState.svelte';
import type { SupabaseClient, RealtimeChannel, RealtimePostgresChangesPayload } from '@supabase/supabase-js';

export interface SupabaseDictionaryRow {
  id: string;
  userId: string;
  word: string;
  normalizedWord: string;
  tags: string[];
  isFavorite: boolean;
  srsStage: number;
  updatedAt: string;
  deletedAt?: string | null;
  createdAt: string;
}

export type DictionaryChangeCallback = (row: SupabaseDictionaryRow) => void;

export class SupabaseDictionarySync {
  private supabase: SupabaseClient;
  private userId: string;
  private channel: RealtimeChannel | null = null;
  private unsubscribeFn: (() => void) | null = null;
  private currentUserId: string | null = null;

  constructor(userId: string) {
    this.supabase = getSessionClient();
    this.userId = userId;
  }

  private isGated(): boolean {
    return !hasLiveSession() || this.userId !== authState.userId;
  }

  async upsert(row: SupabaseDictionaryRow): Promise<void> {
    if (this.isGated()) return;
    const { error } = await this.supabase.from('user_dictionary_words').upsert(
      {
        id: row.id,
        user_id: row.userId,
        word: row.word,
        normalized_word: row.normalizedWord,
        tags: row.tags,
        is_favorite: row.isFavorite,
        srs_stage: row.srsStage,
        updated_at: row.updatedAt,
        deleted_at: row.deletedAt ?? null,
      },
      { onConflict: 'user_id, normalized_word', ignoreDuplicates: false },
    );
    if (error) throw error;
  }

  async delete(id: string): Promise<void> {
    if (this.isGated()) return;
    const now = new Date().toISOString();
    const { error } = await this.supabase
      .from('user_dictionary_words')
      .update({ deleted_at: now, updated_at: now })
      .eq('id', id)
      .eq('user_id', this.userId);
    if (error) throw error;
  }

  async fetchAll(): Promise<SupabaseDictionaryRow[]> {
    if (this.isGated()) return [];
    const { data, error } = await this.supabase
      .from('user_dictionary_words')
      .select('*')
      .eq('user_id', this.userId)
      .is('deleted_at', null);
    if (error) throw error;
    return (data ?? []).map(this.mapRow);
  }

  subscribeToDictionary(callback: DictionaryChangeCallback): () => void {
    const channelName = `dictionary:${this.userId}`;

    if (
      this.currentUserId === this.userId &&
      this.channel != null &&
      this.unsubscribeFn != null &&
      (this.channel as unknown as { state?: string }).state === 'subscribed'
    ) {
      return this.unsubscribeFn;
    }

    if (this.currentUserId !== null && this.currentUserId !== this.userId) {
      this.teardown();
    }

    const client = this.supabase as unknown as {
      getChannels?: () => RealtimeChannel[];
      getChannel?: (name: string) => RealtimeChannel | undefined;
    };
    let existing: RealtimeChannel | null = null;
    if (typeof client.getChannel === 'function') {
      try {
        existing = client.getChannel(channelName) ?? null;
      } catch {
        existing = null;
      }
    }
    if (!existing && typeof client.getChannels === 'function') {
      try {
        const all = client.getChannels();
        existing =
          all.find((c) => {
            const topic = (c as unknown as { topic?: string; channelName?: string }).topic ??
              (c as unknown as { channelName?: string }).channelName ??
              '';
            return topic.includes(channelName);
          }) ?? null;
      } catch {
        existing = null;
      }
    }
    if (existing) {
      const state = (existing as unknown as { state?: string }).state;
      if (state === 'subscribed' || state === 'joined') {
        this.channel = existing;
        this.currentUserId = this.userId;
        this.unsubscribeFn = () => {
          try {
            existing!.unsubscribe();
          } catch {}
          try {
            this.supabase.removeChannel(existing!);
          } catch {}
          if (this.channel === existing) {
            this.channel = null;
            this.currentUserId = null;
          }
          this.unsubscribeFn = null;
        };
        return this.unsubscribeFn;
      }
      try {
        this.supabase.removeChannel(existing);
      } catch {}
    }

    if (this.channel) this.teardown();

    const channel = this.supabase.channel(channelName);
    this.channel = channel;
    this.currentUserId = this.userId;

    channel.on(
      'postgres_changes',
      {
        event: '*',
        schema: 'public',
        table: 'user_dictionary_words',
        filter: `user_id=eq.${this.userId}`,
      },
      (payload: RealtimePostgresChangesPayload<Record<string, unknown>>) => {
        const raw = (payload.new ?? payload.old) as Record<string, unknown> | null;
        if (!raw || typeof raw !== 'object') return;
        // LWW: compare updated_at lexicographically; caller handles tie with created_at
        const row = this.mapRow(raw as Record<string, unknown>);
        callback(row);
      },
    );

    channel.subscribe();
    this.unsubscribeFn = () => {
      try {
        channel.unsubscribe();
      } catch {}
      try {
        this.supabase.removeChannel(channel);
      } catch {}
      if (this.channel === channel) {
        this.channel = null;
        this.currentUserId = null;
      }
      this.unsubscribeFn = null;
    };
    return this.unsubscribeFn;
  }

  getRealtimeStatus(): 'connected' | 'connecting' | 'closed' | 'error' {
    if (!this.channel) return 'closed';
    const state = (this.channel as unknown as { state?: string }).state ?? '';
    if (state === 'subscribed' || state === 'joined') return 'connected';
    if (state === 'joining' || state === 'connecting') return 'connecting';
    if (state === 'closed' || state === 'leaving' || state === 'unsubscribed') return 'closed';
    if (state === 'errored' || state === 'error') return 'error';
    return 'closed';
  }

  destroy(): void {
    this.teardown();
  }

  private teardown(): void {
    if (this.channel) {
      try {
        this.channel.unsubscribe();
      } catch {}
      try {
        this.supabase.removeChannel(this.channel);
      } catch {}
      this.channel = null;
    }
    this.currentUserId = null;
    this.unsubscribeFn = null;
  }

  private mapRow(raw: Record<string, unknown>): SupabaseDictionaryRow {
    return {
      id: String(raw.id ?? ''),
      userId: String(raw.user_id ?? ''),
      word: String(raw.word ?? ''),
      normalizedWord: String(raw.normalized_word ?? ''),
      tags: Array.isArray(raw.tags) ? (raw.tags as string[]) : [],
      isFavorite: Boolean(raw.is_favorite ?? false),
      srsStage: Number(raw.srs_stage ?? 0),
      updatedAt: String(raw.updated_at ?? new Date().toISOString()),
      deletedAt: raw.deleted_at != null ? String(raw.deleted_at) : null,
      createdAt: String(raw.created_at ?? new Date().toISOString()),
    };
  }
}
