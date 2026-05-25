import { listBookmarks, saveBookmark, deleteBookmark } from "$lib/api/tauriClient";

export type BookmarkItem = {
  id: string;
  bookId: string;
  pageNumber: number;
  title?: string;
  createdAt: string;
};

export function createBookmarksState() {
  let bookmarksList = $state<BookmarkItem[]>([]);
  let bookmarksLoading = $state(false);

  async function loadBookmarks(bookId: string) {
    bookmarksLoading = true;
    try {
      bookmarksList = await listBookmarks(bookId);
    } catch (err) {
      console.error("Failed to load bookmarks:", err);
      bookmarksList = [];
    } finally {
      bookmarksLoading = false;
    }
  }

  async function addBookmark(bookId: string, pageNumber: number) {
    try {
      await saveBookmark({
        id: crypto.randomUUID(),
        bookId,
        pageNumber,
        title: `Page ${pageNumber}`,
        createdAt: new Date().toISOString(),
      });
      await loadBookmarks(bookId);
    } catch (err) {
      console.error("Failed to save bookmark:", err);
    }
  }

  async function removeBookmark(id: string, bookId: string) {
    try {
      await deleteBookmark(id);
      await loadBookmarks(bookId);
    } catch (err) {
      console.error("Failed to delete bookmark:", err);
    }
  }

  return {
    get bookmarksList() { return bookmarksList; },
    get bookmarksLoading() { return bookmarksLoading; },
    loadBookmarks,
    addBookmark,
    removeBookmark,
  };
}
