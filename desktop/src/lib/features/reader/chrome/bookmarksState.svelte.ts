import { listBookmarks, saveBookmark, deleteBookmark } from '$lib/shared/api/tauriClient';
import { authState } from '$lib/stores/authState.svelte';
import { SyncOutboxDao } from '$lib/shared/outbox/SyncOutboxDao';

const outboxDao = new SyncOutboxDao();

export type BookmarkItem = {
  id: string;
  bookId: string;
  pageNumber: number;
  title?: string;
  createdAt: string;
};

export function createBookmarksState(): {
  readonly bookmarksList: BookmarkItem[];
  readonly bookmarksLoading: boolean;
  loadBookmarks(bookId: string): Promise<void>;
  addBookmark(bookId: string, pageNumber: number, location?: { cfiLocation?: string | null; locatorJson?: string | null }): Promise<void>;
  removeBookmark(id: string, bookId: string): Promise<void>;
} {
  let bookmarksList = $state<BookmarkItem[]>([]);
  let bookmarksLoading = $state(false);

  async function loadBookmarks(bookId: string): Promise<void> {
    bookmarksLoading = true;
    try {
      bookmarksList = await listBookmarks(bookId);
    } catch (err) {
      console.error('Failed to load bookmarks:', err);
      bookmarksList = [];
    } finally {
      bookmarksLoading = false;
    }
  }

  async function addBookmark(
    bookId: string,
    pageNumber: number,
    location?: { cfiLocation?: string | null; locatorJson?: string | null },
  ): Promise<void> {
    const id = crypto.randomUUID();
    const createdAt = new Date().toISOString();
    try {
      await saveBookmark({
        id,
        bookId,
        pageNumber,
        title: `Page ${pageNumber}`,
        createdAt,
      });
      if (authState.userId) {
        void outboxDao.add('BOOKMARK', id, 'UPSERT', JSON.stringify({
          userId: authState.userId,
          bookId,
          cfiLocation: location?.cfiLocation ?? `page:${pageNumber}`,
          locatorJson: location?.locatorJson ?? null,
          titleSnippet: `Page ${pageNumber}`,
          updatedAt: createdAt,
        }));
      }
      await loadBookmarks(bookId);
    } catch (err) {
      console.error('Failed to save bookmark:', err);
    }
  }

  async function removeBookmark(id: string, bookId: string): Promise<void> {
    const bookmark = bookmarksList.find((item) => item.id === id);
    try {
      await deleteBookmark(id);
      if (authState.userId && bookmark) {
        const updatedAt = new Date().toISOString();
        void outboxDao.add('BOOKMARK', id, 'DELETE', JSON.stringify({
          userId: authState.userId,
          bookId,
          cfiLocation: `page:${bookmark.pageNumber}`,
          titleSnippet: bookmark.title ?? null,
          deletedAt: updatedAt,
          updatedAt,
        }));
      }
      await loadBookmarks(bookId);
    } catch (err) {
      console.error('Failed to delete bookmark:', err);
    }
  }

  return {
    get bookmarksList() {
      return bookmarksList;
    },
    get bookmarksLoading() {
      return bookmarksLoading;
    },
    loadBookmarks,
    addBookmark,
    removeBookmark,
  };
}
