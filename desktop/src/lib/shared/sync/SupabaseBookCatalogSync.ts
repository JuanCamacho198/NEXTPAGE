/**
 * SupabaseBookCatalogSync — syncs book metadata to Supabase via PostgREST.
 *
 * Handles:
 * - upsertBook: writes a book row (ON CONFLICT user_id, id DO UPDATE)
 * - fetchCatalog: reads all book rows for the current user
 * - deleteBook: removes a book row for the current user
 * - subscribeToCatalog: Realtime subscription for cross-device book sync
 */
import { getSessionClient } from '$lib/services/supabase';
import type { SupabaseClient, RealtimePostgresChangesPayload } from '@supabase/supabase-js';

export interface SupabaseUserBookRow {
  id: string;
  userId: string;
  title: string;
  author: string | null;
  format: string;
  contentHash: string | null;
  filePath: string | null;
  coverUrl: string | null;
  description: string | null;
  totalPages: number | null;
  sourceDevice: string | null;
  importedAt: string;
  updatedAt: string;
}

export type CatalogChangeCallback = (row: SupabaseUserBookRow) => void;

export class SupabaseBookCatalogSync {
  private supabase: SupabaseClient;
  private userId: string;
  private unsubscribeRealtime: (() => void) | null = null;

  constructor(userId: string) {
    this.supabase = getSessionClient();
    this.userId = userId;
  }

  /**
   * Upsert a single book row.
   * ON CONFLICT(user_id, id) DO UPDATE with all fields.
   */
  async upsertBook(book: SupabaseUserBookRow): Promise<void> {
    const { error } = await this.supabase.from('user_books').upsert(
      {
        id: book.id,
        user_id: book.userId,
        title: book.title,
        author: book.author ?? null,
        format: book.format,
        content_hash: book.contentHash ?? null,
        file_path: book.filePath ?? null,
        cover_url: book.coverUrl ?? null,
        description: book.description ?? null,
        total_pages: book.totalPages ?? null,
        source_device: book.sourceDevice ?? null,
        imported_at: book.importedAt,
        updated_at: book.updatedAt,
      },
      {
        onConflict: 'user_id, id',
        ignoreDuplicates: false,
      },
    );

    if (error) throw error;
  }

  /**
   * Fetch all user_books rows for the current user.
   */
  async fetchCatalog(): Promise<SupabaseUserBookRow[]> {
    const { data, error } = await this.supabase
      .from('user_books')
      .select('*')
      .eq('user_id', this.userId)
      .order('updated_at', { ascending: false });

    if (error) throw error;

    return (data ?? []).map(this.mapRow);
  }

  /**
   * Delete a book row for the current user.
   */
  async deleteBook(bookId: string): Promise<void> {
    const { error } = await this.supabase
      .from('user_books')
      .delete()
      .eq('user_id', this.userId)
      .eq('id', bookId);

    if (error) throw error;
  }

  /**
   * Find a book row by content hash for the current user.
   * Returns the first match or null if not found.
   * Used by content-hash dedup (PR 5) to check if a book with the
   * same SHA-256 hash already exists in the catalog.
   */
  async findByHash(contentHash: string): Promise<SupabaseUserBookRow | null> {
    const { data, error } = await this.supabase
      .from('user_books')
      .select('*')
      .eq('user_id', this.userId)
      .eq('content_hash', contentHash)
      .maybeSingle();

    if (error) throw error;
    return data ? this.mapRow(data as Record<string, unknown>) : null;
  }

  /**
   * Subscribe to realtime changes on user_books for this user.
   * Returns an unsubscribe function.
   */
  subscribeToCatalog(callback: CatalogChangeCallback): () => void {
    const channel = this.supabase.channel(`catalog:${this.userId}`);

    channel.on(
      'postgres_changes',
      {
        event: '*',
        schema: 'public',
        table: 'user_books',
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
   * Clean up realtime subscription.
   */
  destroy(): void {
    this.unsubscribeRealtime?.();
    this.unsubscribeRealtime = null;
  }

  private mapRow(row: Record<string, unknown>): SupabaseUserBookRow {
    return {
      id: String(row.id ?? ''),
      userId: String(row.user_id ?? ''),
      title: String(row.title ?? ''),
      author: row.author != null ? String(row.author) : null,
      format: String(row.format ?? ''),
      contentHash: row.content_hash != null ? String(row.content_hash) : null,
      filePath: row.file_path != null ? String(row.file_path) : null,
      coverUrl: row.cover_url != null ? String(row.cover_url) : null,
      description: row.description != null ? String(row.description) : null,
      totalPages: row.total_pages != null ? Number(row.total_pages) : null,
      sourceDevice: row.source_device != null ? String(row.source_device) : null,
      importedAt: String(row.imported_at ?? new Date().toISOString()),
      updatedAt: String(row.updated_at ?? new Date().toISOString()),
    };
  }
}
