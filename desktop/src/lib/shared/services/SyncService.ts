/**
 * SyncService — syncs local SQLite state with Google Drive.
 * Replaces Supabase table sync with Drive JSON state files (GoogleDriveStateSync)
 * and Drive file sync (GDriveProvider). LWW conflict resolution via ConflictResolver.
 *
 * Dual-write mode: after Drive sync, also upserts progress to Supabase.
 * Cutover: when `readingProgressSync` is 'supabase', Drive writes are skipped.
 *
 * Extended in PR 2 (cross-device-book-sync):
 * - syncBookCatalog(): reconciles local books with Supabase user_books catalog
 * - setupOutboxProcessor(): wires outbox handler for BOOK entity dispatch
 */
import { authState } from '$lib/stores/authState.svelte';
import { hasLiveSession, recheckLiveSession } from '$lib/services/supabase';
import { reportAuthError } from '$lib/shared/stores/syncAlert.svelte';
import { GDriveProvider } from './storage/GDriveProvider';
import { GoogleDriveStateSync } from './GoogleDriveStateSync';
import { SupabaseProgressSync } from '../sync/SupabaseProgressSync';
import { SupabaseBookCatalogSync, buildRemoteRefs } from '../sync/SupabaseBookCatalogSync';
import { canonicalBookName } from '$lib/shared/protocol/DriveCatalogContract';
import { SyncOutboxService } from '../outbox/SyncOutboxService';
import type {
  ProgressStateJson,
  HighlightStateJson,
  BookmarkStateJson,
} from './GoogleDriveStateSync';
import type { TagDto } from '$lib/shared/types';
import * as tauri from '$lib/shared/api/tauriClient';

/** Sync mode for reading progress: 'drive' (legacy), 'dual' (both), 'supabase' (cutover). */
type ReadingProgressSyncMode = 'drive' | 'dual' | 'supabase';
type BookmarkSyncMode = 'drive' | 'dual' | 'supabase';
type HighlightSyncMode = 'drive' | 'dual' | 'supabase';

export class SyncService {
  private static gdrive = new GDriveProvider();

  /** Which mode to use for reading progress sync. Defaults to 'dual' during transition. */
  private static readingProgressSync: ReadingProgressSyncMode = 'dual';

  /** Which mode to use for bookmark sync. Independent of readingProgressSync. */
  private static bookmarkSync: BookmarkSyncMode = 'dual';

  /** Which mode to use for highlight sync (includes tags). Independent of readingProgressSync. */
  private static highlightSync: HighlightSyncMode = 'dual';

  /** Flag to track whether Drive → Supabase import was done this session. */
  private static supabaseImportDone = false;

  /** Singleton outbox service for processing queued sync items. */
  private static outboxService: SyncOutboxService | null = null;

  /** Shared in-flight startup sync so repeated auth wiring cannot duplicate work. */
  private static metadataSyncPromise: Promise<void> | null = null;

  /**
   * Override the sync mode at runtime.
   * - 'drive': legacy Drive-only (no Supabase writes)
   * - 'dual': write to both Drive and Supabase (transition)
   * - 'supabase': Supabase-only (cutover complete)
   */
  static setReadingProgressSyncMode(mode: ReadingProgressSyncMode): void {
    this.readingProgressSync = mode;
  }

  static setBookmarkSyncMode(mode: BookmarkSyncMode): void {
    this.bookmarkSync = mode;
  }

  static setHighlightSyncMode(mode: HighlightSyncMode): void {
    this.highlightSync = mode;
  }

  /**
   * Whether any import is still pending (import not done and at least one domain in dual/supabase mode).
   */
  static get hasPendingImport(): boolean {
    if (this.supabaseImportDone) return false;
    return (
      this.readingProgressSync !== 'drive' ||
      this.bookmarkSync !== 'drive' ||
      this.highlightSync !== 'drive'
    );
  }

  /**
   * Set up and start the outbox timer-based processor.
   * Registers a handler that dispatches by entity type:
   * - BOOK → SupabaseBookCatalogSync.upsertBook()
   * - READING_PROGRESS → (future, no-op for now)
   *
   * Idempotent — safe to call multiple times.
   */
  static setupOutboxProcessor(): void {
    if (this.outboxService) return;

    this.outboxService = new SyncOutboxService();
    this.outboxService.setHandler(async (entityType, entityId, operation, payloadJson) => {
      // Gate (SR-1): never process a row without a live session. Silent skip —
      // no throw (a throw would markFailed + backoff, feeding the retry loop)
      // and no request with a dead JWT. The flush-level gate in
      // SyncOutboxService makes this reachable only in a mid-flush race.
      if (!hasLiveSession()) return;
      if (!entityId) throw new Error(`Outbox entity ${entityType} is missing entityId`);

      // The live-session gate guarantees the cached user matches authState (D3),
      // so a null userId here is impossible; the guard keeps the handler
      // type-safe with the same silent-skip contract.
      const userId = authState.userId;
      if (!userId) return;

      const progressSync = new SupabaseProgressSync(userId);
      const payload = JSON.parse(payloadJson) as Record<string, unknown>;

      if (entityType === 'READING_PROGRESS' && operation === 'UPSERT') {
        await progressSync.upsertProgress({
          userId: userId,
          bookId: entityId,
          cfiLocation: String(payload.cfiLocation ?? ''),
          percentage: Number(payload.percentage ?? 0),
          currentPage: payload.currentPage != null ? Number(payload.currentPage) : null,
          locatorJson: payload.locatorJson != null ? String(payload.locatorJson) : null,
          updatedAt: String(payload.updatedAt ?? new Date().toISOString()),
        });
        return;
      }

      // D10 (SCEN-push-2/3/4): READING_SESSION upsert — one outbox row per
      // session (never coalesced). Remote updated_at MUST come from the payload
      // clock, NOT now(): pull-back of an own push is then a tie → no-op
      // (SCEN-pull-3). Success → outbox delete; throw → existing backoff.
      if (entityType === 'READING_SESSION' && operation === 'UPSERT') {
        await progressSync.upsertReadingSession({
          id: String(payload.id ?? ''),
          userId: userId,
          bookId: entityId,
          startedAt: String(payload.startedAt ?? ''),
          durationMinutes: Number(payload.durationMinutes ?? 0),
          date: String(payload.date ?? ''),
          device: 'desktop',
          updatedAt: new Date(Number(payload.updatedAtEpochMillis)).toISOString(),
          startPercentage: payload.startPercentage != null ? Number(payload.startPercentage) : null,
          endPercentage: payload.endPercentage != null ? Number(payload.endPercentage) : null,
        });
        return;
      }

      if (entityType === 'HIGHLIGHT') {
        await progressSync.upsertHighlight({
          id: entityId,
          userId: userId,
          bookId: String(payload.bookId ?? entityId),
          cfiRange: String(payload.cfiRange ?? payload.cfi ?? ''),
          textContent: String(payload.textContent ?? payload.text ?? ''),
          note: payload.note != null ? String(payload.note) : null,
          color: String(payload.color ?? 'yellow'),
          page: payload.page != null ? Number(payload.page) : null,
          rectJson: (payload.rectJson as Record<string, number> | null | undefined) ?? null,
          locatorJson: payload.locatorJson != null ? String(payload.locatorJson) : null,
          deletedAt:
            operation === 'DELETE' ? String(payload.deletedAt ?? new Date().toISOString()) : null,
          updatedAt: String(payload.updatedAt ?? new Date().toISOString()),
        });
        return;
      }

      if (entityType === 'BOOKMARK') {
        await progressSync.upsertBookmark({
          id: entityId,
          userId: userId,
          bookId: String(payload.bookId ?? entityId),
          cfiLocation: String(payload.cfiLocation ?? ''),
          titleSnippet: payload.titleSnippet != null ? String(payload.titleSnippet) : null,
          locatorJson: payload.locatorJson != null ? String(payload.locatorJson) : null,
          deletedAt:
            operation === 'DELETE' ? String(payload.deletedAt ?? new Date().toISOString()) : null,
          updatedAt: String(payload.updatedAt ?? new Date().toISOString()),
        });
        return;
      }

      if (entityType === 'BOOK' && operation === 'UPSERT') {
        const metadata = payload;
        const bookSync = new SupabaseBookCatalogSync(userId);

        const contentHash = metadata.content_hash != null ? String(metadata.content_hash) : null;

        // Content-hash dedup: skip if this SHA-256 hash already exists
        // in the catalog for this user (book imported from other device).
        if (contentHash) {
          const existing = await bookSync.findByHash(contentHash);
          if (existing) {
            // Already in catalog — skip upsert to avoid duplicates
            return;
          }
        }

        // Cover upload: try to read the cover file and upload to Storage
        let coverUrl: string | null = null;
        try {
          const allBooks = await tauri.listLibraryBooks();
          const localBook = allBooks.find((b) => b.id === entityId);
          if (localBook?.coverPath) {
            const coverBytes = await tauri.getFileBytes(localBook.coverPath);
            coverUrl = await bookSync.uploadCover(
              userId,
              entityId,
              new Uint8Array(coverBytes).buffer as ArrayBuffer,
            );
          }
        } catch (e) {
          console.warn('Cover upload failed for book', entityId, e);
          // Non-blocking — continue with null coverUrl
        }

        // Binary upload to Drive + remote-ref persistence (DRP-1). The upload
        // must succeed before refs are written; on failure the error propagates
        // so the outbox marks the row failed with backoff and no partial refs
        // are persisted (DRP-4). On retry, DRP-3 find-by-name updates the same
        // Drive file — no duplicates.
        let remoteRefs: ReturnType<typeof buildRemoteRefs> | null = null;
        try {
          const sourceBooks = await tauri.listBooks();
          const localSource = sourceBooks.find((b) => b.id === entityId);
          const format = String(metadata.format ?? localSource?.format ?? 'epub');
          const expectedName = canonicalBookName(entityId, format);
          if (localSource?.filePath) {
            const fileBytes = await tauri.getFileBytes(localSource.filePath);
            const fileId = await this.gdrive.upload(
              entityId,
              new Uint8Array(fileBytes),
              expectedName,
            );
            remoteRefs = buildRemoteRefs(entityId, format, fileId);
          }
        } catch (e) {
          throw e;
        }

        await bookSync.upsertBook({
          id: entityId,
          userId: userId,
          title: String(metadata.title ?? ''),
          author: metadata.author != null ? String(metadata.author) : null,
          format: String(metadata.format ?? ''),
          contentHash: contentHash,
          filePath: null,
          coverUrl: coverUrl,
          description: null,
          totalPages: metadata.totalPages != null ? Number(metadata.totalPages) : null,
          sourceDevice: 'desktop',
          importedAt:
            metadata.importedAt != null ? String(metadata.importedAt) : new Date().toISOString(),
          updatedAt:
            metadata.updatedAt != null ? String(metadata.updatedAt) : new Date().toISOString(),
          ...(remoteRefs ?? {}),
        });
      } else if (entityType === 'BOOK' && operation === 'DELETE') {
        // Explicit deletion is versioned: tombstone the remote row, never hard-delete.
        const bookSync = new SupabaseBookCatalogSync(userId);
        await bookSync.tombstoneBook(entityId);
      } else {
        throw new Error(`Unsupported outbox entity: ${entityType}/${operation}`);
      }
    });

    this.outboxService.start();
  }

  /**
   * Reset the outbox auth circuit breaker. Wired to SIGNED_IN / TOKEN_REFRESHED
   * (D4): fresh tokens mean an auth-class pause no longer applies, so the flush
   * may resume immediately instead of waiting out the backoff window.
   */
  static resetOutboxBreaker(): void {
    this.outboxService?.resetAuthBreaker();
  }

  /**
   * Sync book metadata catalog with Supabase.
   *
   * Flow:
   * 1. Fetch remote catalog from user_books table
   * 2. Get local books from SQLite
   * 3. Reconcile: push local books that are missing from the remote catalog
   *
   * The shelf "Available from other devices" section is no longer fed from
   * user_books — it lists Drive directly via
   * `downloadableCatalog.loadAvailableFromDrive()` (T-03).
   */
  static async syncBookCatalog(): Promise<void> {
    if (!authState.userId) return;
    // D1: re-verify the live session once for silent drops (DA-1 "stale without event").
    if (!(await recheckLiveSession())) return;

    try {
      const catalogSync = new SupabaseBookCatalogSync(authState.userId);

      // 1. Fetch remote catalog
      const remoteBooks = await catalogSync.fetchCatalog();

      // 2. Get local books — library listing for coverPath, book listing for filePath
      const [booksWithCover, sourceBooks] = await Promise.all([
        tauri.listLibraryBooks(),
        tauri.listBooks(),
      ]);
      const filePathByBookId = new Map(sourceBooks.map((b) => [b.id, b.filePath]));
      const localBooks = booksWithCover.map((lb) => ({
        ...lb,
        filePath: filePathByBookId.get(lb.id) ?? '',
      }));

      const remoteIds = new Set(remoteBooks.map((b) => b.id));

      // 3. Reconcile: push any local book missing from remote catalog, or
      // present in the catalog but without a Drive remote ref (the outbox
      // BOOK/UPSERT may have been lost silently, leaving metadata-only rows).
      for (const book of localBooks) {
        const remoteBook = remoteBooks.find((rb) => rb.id === book.id);
        const missingFromCatalog = !remoteIds.has(book.id);
        const missingRemoteRef = Boolean(remoteBook && !remoteBook.remoteProvider);
        if (!missingFromCatalog && !missingRemoteRef) continue;
        try {
          // Cover upload: try to read the cover file and upload to Storage
          let coverUrl: string | null = null;
          try {
            if (book.coverPath) {
              const coverBytes = await tauri.getFileBytes(book.coverPath);
              coverUrl = await catalogSync.uploadCover(
                authState.userId,
                book.id,
                new Uint8Array(coverBytes).buffer as ArrayBuffer,
              );
            }
          } catch (e) {
            console.warn('Cover upload failed for book', book.id, e);
          }

          // Binary upload to Drive + remote-ref persistence (DRP-1) when the
          // local file exists and the catalog row has no remote ref yet. The
          // upload must succeed before refs are written; on failure the error
          // is non-blocking for metadata but no partial refs are persisted.
          let remoteRefs: ReturnType<typeof buildRemoteRefs> | null = null;
          if (book.filePath && missingRemoteRef) {
            try {
              const fileBytes = await tauri.getFileBytes(book.filePath);
              const expectedName = canonicalBookName(book.id, book.format);
              const fileId = await this.gdrive.upload(
                book.id,
                new Uint8Array(fileBytes),
                expectedName,
              );
              remoteRefs = buildRemoteRefs(book.id, book.format, fileId);
            } catch (e) {
              console.error(`Failed to upload book file for ${book.id}:`, e);
            }
          }

          await catalogSync.upsertBook({
            id: book.id,
            userId: authState.userId,
            title: book.title,
            author: book.author || null,
            format: book.format,
            filePath: book.filePath || null,
            coverUrl: coverUrl,
            description: null,
            totalPages: book.totalPages > 0 ? book.totalPages : null,
            sourceDevice: 'desktop',
            importedAt: book.createdAt,
            updatedAt: book.updatedAt,
            ...(remoteRefs ?? {}),
          });
        } catch (e) {
          console.error(`Failed to push local book ${book.id} to catalog:`, e);
        }
      }
    } catch (e) {
      // SR-3: typed AUTH_REQUIRED/AUTH_EXPIRED must surface, never console.error-only.
      reportAuthError(e);
      console.error('Failed to sync book catalog:', e);
    }
  }

  static async syncMetadata(): Promise<void> {
    if (!authState.isSignedIn) return;
    // D1: async sync path re-verifies the live session once (DA-1.2).
    if (!(await recheckLiveSession())) return;

    if (this.metadataSyncPromise) return this.metadataSyncPromise;

    const syncPromise = Promise.all([this.syncBooks(), this.syncState(), this.syncBookCatalog()])
      .then(() => undefined)
      .catch((error: unknown) => {
        reportAuthError(error);
        console.error('Failed to sync startup metadata:', error);
      })
      .finally(() => {
        if (this.metadataSyncPromise === syncPromise) {
          this.metadataSyncPromise = null;
        }
      });

    this.metadataSyncPromise = syncPromise;
    return syncPromise;
  }

  /**
   * On first sync after login, run the one-time import of Drive progress,
   * bookmarks, highlights, and tags to Supabase.
   */
  private static async ensureSupabaseImport(): Promise<void> {
    if (this.supabaseImportDone) return;
    if (
      this.readingProgressSync === 'drive' &&
      this.bookmarkSync === 'drive' &&
      this.highlightSync === 'drive'
    )
      return;
    if (!authState.userId) return;
    // D1: re-verify the live session once for silent drops (DA-1.2).
    if (!(await recheckLiveSession())) return;

    try {
      const sync = new SupabaseProgressSync(authState.userId);
      const localBooks = await tauri.listBooks();

      // ── Import progress ──
      if (this.readingProgressSync !== 'drive') {
        const progressRows: Array<{
          userId: string;
          bookId: string;
          cfiLocation: string;
          percentage: number;
          updatedAt: string;
        }> = [];

        for (const book of localBooks) {
          const progress = await tauri.getProgress(book.id);
          if (progress) {
            progressRows.push({
              userId: authState.userId,
              bookId: progress.bookId,
              cfiLocation: progress.cfiLocation,
              percentage: progress.percentage,
              updatedAt: progress.updatedAt,
            });
          }
        }

        if (progressRows.length > 0) {
          await sync.importFromDrive(progressRows);
        }
      }

      // ── Import bookmarks ──
      if (this.bookmarkSync !== 'drive') {
        const bookmarkRows: Array<{
          userId: string;
          bookId: string;
          cfiLocation: string;
          titleSnippet: string | null;
          updatedAt: string;
        }> = [];

        for (const book of localBooks) {
          const bookmarks = await tauri.listBookmarks(book.id);
          for (const b of bookmarks) {
            bookmarkRows.push({
              userId: authState.userId,
              bookId: b.bookId,
              cfiLocation: '',
              titleSnippet: b.title ?? null,
              updatedAt: b.createdAt,
            });
          }
        }

        if (bookmarkRows.length > 0) {
          await sync.importBookmarksFromDrive(
            bookmarkRows.map((b) => ({
              ...b,
              id: crypto.randomUUID(),
              locatorJson: null,
              deletedAt: null,
            })),
          );
        }
      }

      // ── Import highlights + tags ──
      if (this.highlightSync !== 'drive') {
        const highlightRows: Array<{
          id: string;
          userId: string;
          bookId: string;
          cfiRange: string;
          textContent: string;
          note: string | null;
          color: string;
          rectJson: Record<string, number> | null;
          updatedAt: string;
        }> = [];

        for (const book of localBooks) {
          const highlights = await tauri.listHighlights(book.id);
          for (const h of highlights) {
            highlightRows.push({
              id: h.id,
              userId: authState.userId,
              bookId: h.bookId,
              cfiRange: h.cfi ?? '',
              textContent: h.text,
              note: h.note ?? null,
              color: h.color,
              rectJson: h.pageNumber ? { left: 0, right: 0, top: 0, bottom: 0 } : null,
              updatedAt: h.createdAt,
            });
          }

          // Import tags for each highlight
          for (const h of highlights) {
            const tags: TagDto[] = await tauri.listTagsForHighlight(h.id).catch(() => []);
            for (const tag of tags) {
              const tagRow = await sync.upsertTag({
                userId: authState.userId,
                name: tag.name,
                color: tag.color ?? null,
              });
              await sync.linkTagToHighlight(h.id, tagRow.id!);
            }
          }
        }

        if (highlightRows.length > 0) {
          await sync.importHighlightsFromDrive(
            highlightRows.map((h) => ({
              ...h,
              type: null,
              locatorJson: null,
              deletedAt: null,
            })),
          );
        }
      }

      this.supabaseImportDone = true;
    } catch (e) {
      console.error('Failed to import data to Supabase:', e);
      // Non-blocking — retry on next sync
    }
  }

  /**
   * Sync book files with Drive — download missing files, upload local-only files.
   * Book metadata (title, author) stays local-only (no table sync).
   */
  private static async syncBooks(): Promise<void> {
    const userId = authState.userId;
    if (!userId) return;
    // D1: re-verify the live session once for silent drops (DA-1.2).
    if (!(await recheckLiveSession())) return;

    // 1. List remote book files from Drive
    const remoteFiles = await this.gdrive.list('');
    const remoteBookFiles = remoteFiles.filter((f) => !f.endsWith('_state.json'));

    // 2. Get local books from SQLite
    const localBooks = await tauri.listBooks();

    // 3. Download book files that are on Drive but missing locally
    for (const remoteFile of remoteBookFiles) {
      const localBook = localBooks.find((b) => {
        const ext = b.format || 'epub';
        return remoteFile === `${b.id}.${ext}` || remoteFile.startsWith(b.id);
      });

      if (localBook) {
        const existsLocally = await tauri.fileExists(localBook.filePath);
        if (!existsLocally) {
          try {
            console.log(`Syncing missing file for book: ${localBook.id}`);
            const fileData = await this.gdrive.download(remoteFile);
            await tauri.saveBookFile(localBook.id, Array.from(fileData));
          } catch (e) {
            reportAuthError(e);
            console.error(`Failed to sync book file for ${localBook.id}:`, e);
          }
        }
      }
    }

    // 4. Upload local-only books to Drive (file only, not metadata)
    const remoteFileIds = new Set(remoteBookFiles);
    for (const localBook of localBooks) {
      const ext = localBook.format || 'epub';
      const expectedName = `${localBook.id}.${ext}`;
      if (!remoteFileIds.has(expectedName)) {
        try {
          const existsLocally = await tauri.fileExists(localBook.filePath);
          if (existsLocally) {
            const fileBytes = await tauri.getFileBytes(localBook.filePath);
            const fileId = await this.gdrive.upload(
              expectedName,
              new Uint8Array(fileBytes),
              expectedName,
            );
            // Persist the remote ref (DRP-1): the upload fileId was previously
            // discarded. The merge upsert preserves existing fields and never
            // lowers catalog_version (DRP-2).
            try {
              const bookSync = new SupabaseBookCatalogSync(userId);
              await bookSync.upsertBook({
                id: localBook.id,
                userId,
                title: localBook.title,
                author: localBook.author || null,
                format: localBook.format,
                filePath: null,
                coverUrl: null,
                description: null,
                totalPages: null,
                sourceDevice: 'desktop',
                ...buildRemoteRefs(localBook.id, localBook.format, fileId),
                importedAt: localBook.createdAt,
                updatedAt: new Date().toISOString(),
              });
            } catch (persistError) {
              console.error(`Failed to persist Drive refs for book ${localBook.id}:`, persistError);
            }
          }
        } catch (e) {
          reportAuthError(e);
          console.error(`Failed to upload book file for ${localBook.id}:`, e);
        }
      }
    }
  }

  /**
   * Sync reading state (progress, highlights, bookmarks) via state.json in Drive.
   * Uses GoogleDriveStateSync for push/pull with LWW conflict resolution.
   */
  private static async syncState(): Promise<void> {
    // D1: async sync path re-verifies the live session once (DA-1.2).
    if (!(await recheckLiveSession())) return;
    // Import existing progress to Supabase on first sync (if in dual/supabase mode)
    await this.ensureSupabaseImport();

    const localBooks = await tauri.listBooks();

    for (const book of localBooks) {
      try {
        // ---- Gather local state ----
        const localProgressDto = await tauri.getProgress(book.id);
        const localHighlightsDto = await tauri.listHighlights(book.id);
        const localBookmarksDto = await tauri.listBookmarks(book.id);

        // Map to state JSON types
        const localProgress: ProgressStateJson | null = localProgressDto
          ? {
              id: localProgressDto.id,
              book_id: localProgressDto.bookId,
              cfi_location: localProgressDto.cfiLocation,
              percentage: localProgressDto.percentage,
              updated_at: new Date(localProgressDto.updatedAt).getTime(),
            }
          : null;

        const localHighlights: HighlightStateJson[] = localHighlightsDto.map((h) => ({
          id: h.id,
          book_id: h.bookId,
          cfi_range: '', // HighlightDto doesn't have CFI — filled from state sync
          text_content: h.text,
          note: h.note ?? null,
          color: h.color,
          updated_at: new Date(h.createdAt).getTime(),
          deleted_at: null,
          recordId: h.id,
          updatedAtEpochMillis: new Date(h.createdAt).getTime(),
          deletedAtEpochMillis: null,
        }));

        const localBookmarks: BookmarkStateJson[] = localBookmarksDto.map((b) => ({
          id: b.id,
          book_id: b.bookId,
          cfi_location: '', // BookmarkDto doesn't have CFI
          title_or_snippet: b.title ?? '',
          updated_at: new Date(b.createdAt).getTime(),
          deleted_at: null,
          recordId: b.id,
          updatedAtEpochMillis: new Date(b.createdAt).getTime(),
          deletedAtEpochMillis: null,
        }));

        // ---- Push local state to Drive (skip if cutover) ----
        if (this.readingProgressSync !== 'supabase') {
          await GoogleDriveStateSync.pushState(
            book.id,
            localProgress,
            localHighlights,
            localBookmarks,
          );
        }

        // ---- Pull remote state and merge ----
        const remote = await GoogleDriveStateSync.pullState(
          book.id,
          localProgress,
          localHighlights,
          localBookmarks,
        );

        // ---- Apply resolved state to local SQLite ----
        if (remote.progress) {
          await tauri.upsertProgress({
            id: remote.progress.id,
            bookId: remote.progress.book_id,
            cfiLocation: remote.progress.cfi_location,
            percentage: remote.progress.percentage,
            updatedAt: new Date(remote.progress.updated_at).toISOString(),
          });
        }

        // ---- Dual-write: also upsert resolved progress to Supabase ----
        if (this.readingProgressSync !== 'drive' && authState.userId && remote.progress) {
          try {
            const sync = new SupabaseProgressSync(authState.userId);
            await sync.upsertProgress({
              userId: authState.userId,
              bookId: remote.progress.book_id,
              cfiLocation: remote.progress.cfi_location,
              percentage: remote.progress.percentage,
              updatedAt: new Date(remote.progress.updated_at).toISOString(),
            });
          } catch (e) {
            reportAuthError(e);
            console.error(`Failed to sync progress to Supabase for book ${book.id}:`, e);
          }
        }
        // Also upsert local-only progress that wasn't in Drive
        else if (
          this.readingProgressSync !== 'drive' &&
          authState.userId &&
          localProgress &&
          !remote.progress
        ) {
          try {
            const sync = new SupabaseProgressSync(authState.userId);
            await sync.upsertProgress({
              userId: authState.userId,
              bookId: localProgress.book_id,
              cfiLocation: localProgress.cfi_location,
              percentage: localProgress.percentage,
              updatedAt: new Date(localProgress.updated_at).toISOString(),
            });
          } catch (e) {
            reportAuthError(e);
            console.error(`Failed to sync local progress to Supabase for book ${book.id}:`, e);
          }
        }

        // Apply highlights: only those that differ from local
        for (const h of remote.highlights) {
          const localMatch = localHighlights.find((lh) => lh.id === h.id);
          if (!localMatch || h.updatedAtEpochMillis > localMatch.updatedAtEpochMillis) {
            // Check if soft-deleted
            if (h.deletedAtEpochMillis !== null) {
              try {
                await tauri.deleteHighlight(h.id);
              } catch {
                // Highlight may not exist locally — ignore
              }
            } else {
              await tauri.saveHighlight({
                id: h.id,
                bookId: h.book_id,
                text: h.text_content,
                color: h.color,
                // Page anchoring is format-aware: PDF highlights carry a real
                // positive page; EPUB highlights are anchored by CFI (no page),
                // so use 1 as the minimum-valid placeholder (matching the local
                // EPUB reader fallback `pageNumber ?? 1`). Passing 0 made
                // normalizePageNumber reject the write (must be > 0).
                pageNumber: 1,
                rectLeft: 0,
                rectRight: 0,
                rectTop: 0,
                rectBottom: 0,
                cfi: h.cfi_range || null,
                note: h.note,
              });
            }
          }
        }

        // Apply bookmarks: only those that differ from local
        for (const b of remote.bookmarks) {
          const localMatch = localBookmarks.find((lb) => lb.id === b.id);
          if (!localMatch || b.updatedAtEpochMillis > localMatch.updatedAtEpochMillis) {
            if (b.deletedAtEpochMillis !== null) {
              try {
                await tauri.deleteBookmark(b.id);
              } catch {
                // Bookmark may not exist locally — ignore
              }
            } else {
              await tauri.saveBookmark({
                id: b.id,
                bookId: b.book_id,
                // EPUB bookmarks are anchored by CFI (no real page); use 1 as the
                // minimum-valid placeholder instead of 0 (page 0 is not a real page).
                pageNumber: 1,
                title: b.title_or_snippet || undefined,
                createdAt: new Date(b.updated_at).toISOString(),
              });
            }
          }
        }
      } catch (e) {
        console.error(`Failed to sync state for book ${book.id}:`, e);
      }
    }
  }
}
