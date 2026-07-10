/**
 * SupabaseProgressSync — syncs reading progress to Supabase via PostgREST.
 *
 * Handles:
 * - upsertProgress: writes a progress row (ON CONFLICT user_id, book_id DO UPDATE)
 * - fetchProgress: reads all progress rows for the current user
 * - subscribeToProgress: Realtime subscription for cross-device sync
 * - importFromDrive: one-time import of existing Drive progress to Supabase
 */
import { getSessionClient } from '$lib/services/supabase';
import type { SupabaseClient, RealtimePostgresChangesPayload } from '@supabase/supabase-js';

export interface SupabaseProgressRow {
  id?: string;
  userId: string;
  bookId: string;
  cfiLocation: string;
  percentage: number;
  currentPage?: number | null;
  locatorJson?: string | null;
  version?: number;
  updatedAt: string;
}

export type ProgressChangeCallback = (row: SupabaseProgressRow) => void;

export class SupabaseProgressSync {
  private supabase: SupabaseClient;
  private userId: string;
  private unsubscribeRealtime: (() => void) | null = null;

  constructor(userId: string) {
    this.supabase = getSessionClient();
    this.userId = userId;
  }

  /**
   * Upsert a single progress row.
   * ON CONFLICT(user_id, book_id) DO UPDATE with all fields.
   */
  async upsertProgress(progress: SupabaseProgressRow): Promise<void> {
    const { error } = await this.supabase.from('reading_progress').upsert(
      {
        user_id: progress.userId,
        book_id: progress.bookId,
        cfi_location: progress.cfiLocation,
        percentage: progress.percentage,
        current_page: progress.currentPage ?? null,
        locator_json: progress.locatorJson ?? null,
        updated_at: progress.updatedAt,
      },
      {
        onConflict: 'user_id, book_id',
        ignoreDuplicates: false,
      },
    );

    if (error) throw error;
  }

  /**
   * Fetch all reading_progress rows for the current user.
   */
  async fetchProgress(): Promise<SupabaseProgressRow[]> {
    const { data, error } = await this.supabase
      .from('reading_progress')
      .select('*')
      .eq('user_id', this.userId);

    if (error) throw error;

    return (data ?? []).map(this.mapRow);
  }

  /**
   * Subscribe to realtime changes on reading_progress for this user.
   * Returns an unsubscribe function.
   */
  subscribeToProgress(callback: ProgressChangeCallback): () => void {
    const channel = this.supabase.channel(`progress:${this.userId}`);

    channel.on(
      'postgres_changes',
      {
        event: '*',
        schema: 'public',
        table: 'reading_progress',
        filter: `user_id=eq.${this.userId}`,
      },
      (payload: RealtimePostgresChangesPayload<Record<string, unknown>>) => {
        if (payload.new && typeof payload.new === 'object') {
          callback(this.mapRow(payload.new as Record<string, unknown>));
        }
      },
    );

    channel.subscribe();
    this.unsubscribeRealtime = () => channel.unsubscribe();

    return this.unsubscribeRealtime;
  }

  /**
   * Import existing Drive progress data to Supabase.
   * Accepts an array of progress rows (typically parsed from Drive state.json files).
   * Batches upserts in chunks of 100.
   */
  async importFromDrive(driveProgressData: SupabaseProgressRow[]): Promise<void> {
    if (driveProgressData.length === 0) return;

    const chunkSize = 100;
    for (let i = 0; i < driveProgressData.length; i += chunkSize) {
      const chunk = driveProgressData.slice(i, i + chunkSize);
      const { error } = await this.supabase.from('reading_progress').upsert(
        chunk.map((p) => ({
          user_id: p.userId,
          book_id: p.bookId,
          cfi_location: p.cfiLocation,
          percentage: p.percentage,
          current_page: p.currentPage ?? null,
          locator_json: p.locatorJson ?? null,
          updated_at: p.updatedAt,
        })),
        {
          onConflict: 'user_id, book_id',
          ignoreDuplicates: false,
        },
      );

      if (error) throw error;
    }
  }

  /**
   * Clean up the realtime subscription.
   */
  destroy(): void {
    this.unsubscribeRealtime?.();
    this.unsubscribeRealtime = null;
  }

  private mapRow(row: Record<string, unknown>): SupabaseProgressRow {
    return {
      id: String(row.id ?? ''),
      userId: String(row.user_id ?? ''),
      bookId: String(row.book_id ?? ''),
      cfiLocation: String(row.cfi_location ?? ''),
      percentage: Number(row.percentage ?? 0),
      currentPage: row.current_page != null ? Number(row.current_page) : null,
      locatorJson: row.locator_json != null ? String(row.locator_json) : null,
      version: Number(row.version ?? 1),
      updatedAt: String(row.updated_at ?? new Date().toISOString()),
    };
  }
}
