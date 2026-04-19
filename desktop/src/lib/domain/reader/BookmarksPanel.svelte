<script lang="ts">
  import { listBookmarks, saveBookmark, deleteBookmark } from "$lib/api/tauriClient";

  type Props = {
    bookId: string;
    onNavigate?: (pageNumber: number) => void;
  };

  let { bookId, onNavigate }: Props = $props();

  let bookmarks: Array<{
    id: string;
    bookId: string;
    pageNumber: number;
    title?: string;
    createdAt: string;
  }> = $state([]);
  let isLoading = $state(true);

  $effect(() => {
    if (bookId) {
      loadBookmarks();
    }
  });

  async function loadBookmarks() {
    isLoading = true;
    try {
      bookmarks = await listBookmarks(bookId);
    } catch (err) {
      console.error("Failed to load bookmarks:", err);
      bookmarks = [];
    } finally {
      isLoading = false;
    }
  }

  async function handleAddBookmark() {
    if (!bookId) return;

    const pageNumber = 1;
    try {
      await saveBookmark({
        id: crypto.randomUUID(),
        bookId,
        pageNumber,
        title: `Page ${pageNumber}`,
        createdAt: new Date().toISOString(),
      });
      await loadBookmarks();
    } catch (err) {
      console.error("Failed to save bookmark:", err);
    }
  }

  async function handleDeleteBookmark(id: string) {
    try {
      await deleteBookmark(id);
      await loadBookmarks();
    } catch (err) {
      console.error("Failed to delete bookmark:", err);
    }
  }

  function handleNavigate(pageNumber: number) {
    onNavigate?.(pageNumber);
  }
</script>

<div class="bookmarks-panel">
  <div class="header">
    <h3 class="title">Bookmarks</h3>
    <button
      type="button"
      class="add-btn"
      onclick={handleAddBookmark}
      disabled={!bookId}
      title="Add bookmark for current page"
    >
      +
    </button>
  </div>

  {#if isLoading}
    <div class="loading">Loading bookmarks...</div>
  {:else if bookmarks.length === 0}
    <div class="empty">No bookmarks yet</div>
  {:else}
    <ul class="bookmark-list">
      {#each bookmarks as bookmark}
        <li class="bookmark-item">
          <button
            type="button"
            class="bookmark-link"
            onclick={() => handleNavigate(bookmark.pageNumber)}
          >
            <span class="page-num">Page {bookmark.pageNumber}</span>
            {#if bookmark.title}
              <span class="bookmark-title">{bookmark.title}</span>
            {/if}
          </button>
          <button
            type="button"
            class="delete-btn"
            onclick={() => handleDeleteBookmark(bookmark.id)}
            title="Delete bookmark"
          >
            ×
          </button>
        </li>
      {/each}
    </ul>
  {/if}
</div>

<style>
  .bookmarks-panel {
    display: flex;
    flex-direction: column;
    height: 100%;
  }

  .header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    border-bottom: 1px solid #e5e7eb;
  }

  .title {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: #374151;
  }

  .add-btn {
    width: 24px;
    height: 24px;
    border: none;
    border-radius: 4px;
    background: #374151;
    color: #fff;
    cursor: pointer;
    font-size: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .add-btn:hover:not(:disabled) {
    background: #1f2937;
  }

  .add-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .loading,
  .empty {
    padding: 24px;
    text-align: center;
    font-size: 13px;
    color: #6b7280;
  }

  .bookmark-list {
    list-style: none;
    margin: 0;
    padding: 8px;
  }

  .bookmark-item {
    display: flex;
    align-items: center;
    padding: 4px;
    border-radius: 4px;
    margin-bottom: 4px;
  }

  .bookmark-item:hover {
    background: #f9fafb;
  }

  .bookmark-link {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    padding: 4px 8px;
    border: none;
    background: transparent;
    cursor: pointer;
    text-align: left;
  }

  .page-num {
    font-size: 13px;
    font-weight: 500;
    color: #374151;
  }

  .bookmark-title {
    font-size: 12px;
    color: #6b7280;
  }

  .delete-btn {
    width: 20px;
    height: 20px;
    border: none;
    border-radius: 4px;
    background: transparent;
    color: #9ca3af;
    cursor: pointer;
    font-size: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .delete-btn:hover {
    background: #fee2e2;
    color: #dc2626;
  }
</style>