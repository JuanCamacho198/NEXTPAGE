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

export interface SupabaseBookmarkRow {
  id?: string;
  userId: string;
  bookId: string;
  cfiLocation: string;
  titleSnippet?: string | null;
  locatorJson?: string | null;
  deletedAt?: string | null;
  updatedAt: string;
}

export interface SupabaseHighlightRow {
  id?: string;
  userId: string;
  bookId: string;
  cfiRange: string;
  textContent: string;
  note?: string | null;
  color: string;
  page?: number | null;
  type?: string | null;
  rectJson?: Record<string, number> | null;
  locatorJson?: string | null;
  deletedAt?: string | null;
  updatedAt: string;
}

export interface SupabaseTagRow {
  id?: string;
  userId: string;
  name: string;
  color?: string | null;
}

export type ProgressChangeCallback = (row: SupabaseProgressRow) => void;
export type BookmarkChangeCallback = (row: SupabaseBookmarkRow) => void;
export type HighlightChangeCallback = (row: SupabaseHighlightRow) => void;

export class SupabaseProgressSync {
  private supabase: SupabaseClient;
  private userId: string;
  private unsubscribeRealtime: (() => void) | null = null;
  private unsubscribeBookmarksRealtime: (() => void) | null = null;
  private unsubscribeHighlightsRealtime: (() => void) | null = null;

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

  // ─── Bookmarks ────────────────────────────────────────────────

  /**
   * Upsert a single bookmark row.
   * ON CONFLICT(user_id, book_id, COALESCE(cfi_location,'')) DO UPDATE
   * but only for rows WHERE deleted_at IS NULL.
   */
  async upsertBookmark(bookmark: SupabaseBookmarkRow): Promise<void> {
    const payload: Record<string, unknown> = {
      id: bookmark.id,
      user_id: bookmark.userId,
      book_id: bookmark.bookId,
      cfi_location: bookmark.cfiLocation,
      title_snippet: bookmark.titleSnippet ?? null,
      locator_json: bookmark.locatorJson ?? null,
      deleted_at: bookmark.deletedAt ?? null,
      updated_at: bookmark.updatedAt,
    };

    const { error } = await this.supabase.from('bookmarks').upsert(payload, {
      onConflict: 'user_id, book_id, cfi_location',
      ignoreDuplicates: false,
    });

    if (error) throw error;
  }

  /**
   * Fetch all bookmarks for the current user, optionally filtered by book.
   */
  async fetchBookmarks(bookId?: string): Promise<SupabaseBookmarkRow[]> {
    let query = this.supabase
      .from('bookmarks')
      .select('*')
      .eq('user_id', this.userId);

    if (bookId) {
      query = query.eq('book_id', bookId);
    }

    const { data, error } = await query;
    if (error) throw error;

    return (data ?? []).map(this.mapBookmarkRow);
  }

  /**
   * Subscribe to realtime changes on bookmarks for this user.
   * Returns an unsubscribe function.
   */
  subscribeToBookmarks(callback: BookmarkChangeCallback): () => void {
    const channel = this.supabase.channel(`bookmarks:${this.userId}`);

    channel.on(
      'postgres_changes',
      {
        event: '*',
        schema: 'public',
        table: 'bookmarks',
        filter: `user_id=eq.${this.userId}`,
      },
      (payload: RealtimePostgresChangesPayload<Record<string, unknown>>) => {
        if (payload.new && typeof payload.new === 'object') {
          callback(this.mapBookmarkRow(payload.new as Record<string, unknown>));
        }
      },
    );

    channel.subscribe();
    this.unsubscribeBookmarksRealtime = () => channel.unsubscribe();

    return this.unsubscribeBookmarksRealtime;
  }

  // ─── Highlights ───────────────────────────────────────────────

  /**
   * Upsert a single highlight row.
   * ON CONFLICT(id) DO UPDATE with all fields.
   */
  async upsertHighlight(highlight: SupabaseHighlightRow): Promise<void> {
    const payload: Record<string, unknown> = {
      id: highlight.id,
      user_id: highlight.userId,
      book_id: highlight.bookId,
      cfi_range: highlight.cfiRange,
      text_content: highlight.textContent,
      note: highlight.note ?? null,
      color: highlight.color,
      page: highlight.page ?? null,
      type: highlight.type ?? null,
      rect_json: highlight.rectJson ?? null,
      locator_json: highlight.locatorJson ?? null,
      deleted_at: highlight.deletedAt ?? null,
      updated_at: highlight.updatedAt,
    };

    const { error } = await this.supabase.from('highlights').upsert(payload, {
      onConflict: 'id',
      ignoreDuplicates: false,
    });

    if (error) throw error;
  }

  /**
   * Fetch all highlights for the current user, optionally filtered by book.
   */
  async fetchHighlights(bookId?: string): Promise<SupabaseHighlightRow[]> {
    let query = this.supabase
      .from('highlights')
      .select('*')
      .eq('user_id', this.userId);

    if (bookId) {
      query = query.eq('book_id', bookId);
    }

    const { data, error } = await query;
    if (error) throw error;

    return (data ?? []).map(this.mapHighlightRow);
  }

  /**
   * Subscribe to realtime changes on highlights for this user.
   * Returns an unsubscribe function.
   */
  subscribeToHighlights(callback: HighlightChangeCallback): () => void {
    const channel = this.supabase.channel(`highlights:${this.userId}`);

    channel.on(
      'postgres_changes',
      {
        event: '*',
        schema: 'public',
        table: 'highlights',
        filter: `user_id=eq.${this.userId}`,
      },
      (payload: RealtimePostgresChangesPayload<Record<string, unknown>>) => {
        if (payload.new && typeof payload.new === 'object') {
          callback(this.mapHighlightRow(payload.new as Record<string, unknown>));
        }
      },
    );

    channel.subscribe();
    this.unsubscribeHighlightsRealtime = () => channel.unsubscribe();

    return this.unsubscribeHighlightsRealtime;
  }

  // ─── Tags ─────────────────────────────────────────────────────

  /**
   * Find-or-create a tag by name for the current user.
   * SELECT existing by (user_id, name), INSERT if not found.
   * Returns the tag row (with id).
   */
  async upsertTag(tag: SupabaseTagRow): Promise<SupabaseTagRow> {
    // Try to find existing tag
    const { data: existing, error: findError } = await this.supabase
      .from('tags')
      .select('*')
      .eq('user_id', tag.userId)
      .eq('name', tag.name)
      .maybeSingle();

    if (findError) throw findError;

    if (existing) {
      // Update color if provided
      if (tag.color != null) {
        const { error: updError } = await this.supabase
          .from('tags')
          .update({ color: tag.color })
          .eq('id', existing.id);

        if (updError) throw updError;
      }
      return this.mapTagRow(existing);
    }

    // Insert new tag
    const payload: Record<string, unknown> = {
      id: tag.id,
      user_id: tag.userId,
      name: tag.name,
      color: tag.color ?? null,
    };

    const { data: inserted, error: insError } = await this.supabase
      .from('tags')
      .upsert(payload, { onConflict: 'user_id, name', ignoreDuplicates: false })
      .select()
      .maybeSingle();

    if (insError) throw insError;

    return this.mapTagRow(inserted ?? payload);
  }

  /**
   * Link a tag to a highlight via highlight_tags junction.
   * ON CONFLICT DO NOTHING (idempotent).
   */
  async linkTagToHighlight(highlightId: string, tagId: string): Promise<void> {
    const { error } = await this.supabase.from('highlight_tags').upsert(
      {
        highlight_id: highlightId,
        tag_id: tagId,
      },
      { onConflict: 'highlight_id, tag_id', ignoreDuplicates: true },
    );

    if (error) throw error;
  }

  // ─── Import helpers ───────────────────────────────────────────

  /**
   * Import bookmark data from Drive (batched upsert).
   */
  async importBookmarksFromDrive(bookmarks: SupabaseBookmarkRow[]): Promise<void> {
    if (bookmarks.length === 0) return;

    const chunkSize = 100;
    for (let i = 0; i < bookmarks.length; i += chunkSize) {
      const chunk = bookmarks.slice(i, i + chunkSize);
      const { error } = await this.supabase.from('bookmarks').upsert(
        chunk.map((b) => ({
          id: b.id,
          user_id: b.userId,
          book_id: b.bookId,
          cfi_location: b.cfiLocation,
          title_snippet: b.titleSnippet ?? null,
          locator_json: b.locatorJson ?? null,
          deleted_at: b.deletedAt ?? null,
          updated_at: b.updatedAt,
        })),
        {
          onConflict: 'user_id, book_id, cfi_location',
          ignoreDuplicates: false,
        },
      );
      if (error) throw error;
    }
  }

  /**
   * Import highlight data from Drive (batched upsert).
   */
  async importHighlightsFromDrive(highlights: SupabaseHighlightRow[]): Promise<void> {
    if (highlights.length === 0) return;

    const chunkSize = 100;
    for (let i = 0; i < highlights.length; i += chunkSize) {
      const chunk = highlights.slice(i, i + chunkSize);
      const { error } = await this.supabase.from('highlights').upsert(
        chunk.map((h) => ({
          id: h.id,
          user_id: h.userId,
          book_id: h.bookId,
          cfi_range: h.cfiRange,
          text_content: h.textContent,
          note: h.note ?? null,
          color: h.color,
          page: h.page ?? null,
          type: h.type ?? null,
          rect_json: h.rectJson ?? null,
          locator_json: h.locatorJson ?? null,
          deleted_at: h.deletedAt ?? null,
          updated_at: h.updatedAt,
        })),
        {
          onConflict: 'id',
          ignoreDuplicates: false,
        },
      );
      if (error) throw error;
    }
  }

  /**
   * Clean up all realtime subscriptions.
   */
  destroy(): void {
    this.unsubscribeRealtime?.();
    this.unsubscribeRealtime = null;
    this.unsubscribeBookmarksRealtime?.();
    this.unsubscribeBookmarksRealtime = null;
    this.unsubscribeHighlightsRealtime?.();
    this.unsubscribeHighlightsRealtime = null;
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

  private mapBookmarkRow(row: Record<string, unknown>): SupabaseBookmarkRow {
    return {
      id: String(row.id ?? crypto.randomUUID()),
      userId: String(row.user_id ?? ''),
      bookId: String(row.book_id ?? ''),
      cfiLocation: String(row.cfi_location ?? ''),
      titleSnippet: row.title_snippet != null ? String(row.title_snippet) : null,
      locatorJson: row.locator_json != null ? String(row.locator_json) : null,
      deletedAt: row.deleted_at != null ? String(row.deleted_at) : null,
      updatedAt: String(row.updated_at ?? new Date().toISOString()),
    };
  }

  private mapHighlightRow(row: Record<string, unknown>): SupabaseHighlightRow {
    return {
      id: String(row.id ?? crypto.randomUUID()),
      userId: String(row.user_id ?? ''),
      bookId: String(row.book_id ?? ''),
      cfiRange: String(row.cfi_range ?? ''),
      textContent: String(row.text_content ?? ''),
      note: row.note != null ? String(row.note) : null,
      color: String(row.color ?? 'yellow'),
      page: row.page != null ? Number(row.page) : null,
      type: row.type != null ? String(row.type) : null,
      rectJson: row.rect_json != null ? (row.rect_json as Record<string, number>) : null,
      locatorJson: row.locator_json != null ? String(row.locator_json) : null,
      deletedAt: row.deleted_at != null ? String(row.deleted_at) : null,
      updatedAt: String(row.updated_at ?? new Date().toISOString()),
    };
  }

  private mapTagRow(row: Record<string, unknown>): SupabaseTagRow {
    return {
      id: String(row.id ?? crypto.randomUUID()),
      userId: String(row.user_id ?? ''),
      name: String(row.name ?? ''),
      color: row.color != null ? String(row.color) : null,
    };
  }
}
