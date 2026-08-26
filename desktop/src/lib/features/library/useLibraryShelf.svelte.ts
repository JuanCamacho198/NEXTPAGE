import {
  FAVORITES_COLLECTION_ID,
  getSafeProgressPercentage,
  getTimestamp,
  type ShelfBook,
  type ShelfFilter,
  type ShelfSort,
  type ShelfView,
} from './utils';

/**
 * Pure filter + sort — no side effects, fully testeable.
 * Applies search (title/author), filter (all/favorites/reading/completed/pending)
 * and sort (title/progress/timestamp) over a copy of the input array.
 */
export function filterAndSortShelfBooks(
  books: ShelfBook[],
  searchQuery: string,
  activeFilter: ShelfFilter,
  activeSort: ShelfSort,
): ShelfBook[] {
  const query = searchQuery.trim().toLowerCase();

  const visible = books.filter((book) => {
    const progress = getSafeProgressPercentage(book);
    const matchesSearch =
      query.length === 0 ||
      book.title.toLowerCase().includes(query) ||
      (book.author ?? '').toLowerCase().includes(query);

    if (!matchesSearch) return false;

    if (activeFilter === 'all') return true;
    if (activeFilter === 'favorites') return Boolean(book.collectionIds?.includes(FAVORITES_COLLECTION_ID));
    if (activeFilter === 'reading') return progress > 0 && progress < 100;
    if (activeFilter === 'completed') return book.readingStatus === 'completed' || progress >= 100;
    return progress === 0;
  });

  return [...visible].sort((left, right) => {
    if (activeSort === 'title') return left.title.localeCompare(right.title, 'es');
    if (activeSort === 'progress') return getSafeProgressPercentage(right) - getSafeProgressPercentage(left);
    // dedup: last_read and date_added share identical timestamp logic (updatedAt)
    return getTimestamp(right) - getTimestamp(left);
  });
}

export type UseLibraryShelfReturn = {
  searchQuery: string;
  activeFilter: ShelfFilter;
  activeSort: ShelfSort;
  activeView: ShelfView;
  filteredBooks: ShelfBook[];
};

export function useLibraryShelf(getBooks: () => ShelfBook[]): UseLibraryShelfReturn {
  let searchQuery = $state('');
  let activeFilter = $state<ShelfFilter>('all');
  let activeSort = $state<ShelfSort>('date_added');
  let activeView = $state<ShelfView>('grid');

  const filteredBooks = $derived.by(() =>
    filterAndSortShelfBooks(getBooks(), searchQuery, activeFilter, activeSort),
  );

  return {
    get searchQuery(): string {
      return searchQuery;
    },
    set searchQuery(v: string) {
      searchQuery = v;
    },
    get activeFilter(): ShelfFilter {
      return activeFilter;
    },
    set activeFilter(v: ShelfFilter) {
      activeFilter = v;
    },
    get activeSort(): ShelfSort {
      return activeSort;
    },
    set activeSort(v: ShelfSort) {
      activeSort = v;
    },
    get activeView(): ShelfView {
      return activeView;
    },
    set activeView(v: ShelfView) {
      activeView = v;
    },
    get filteredBooks(): ShelfBook[] {
      return filteredBooks;
    },
  };
}
