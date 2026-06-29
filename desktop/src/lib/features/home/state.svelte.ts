// ─── Feature state: home — migrated to $state runes ───
// Canonical state is AppState ($lib/stores/AppState.svelte).

import type { LibraryBookDto } from '$lib/shared/types';
import {
  partitionHomeBooks,
  selectShelfBooks,
  type ShelfQueryState,
  type ShelfSortKey,
  type ShelfTabCode,
  type ShelfViewMode,
} from '$lib/shared/stores/homeState';

export type AppRoute = 'home' | 'reader' | 'library' | 'stats' | 'highlights' | 'settings';

class HomeStateManager {
  route = $state<AppRoute>('home');
  previewBookId = $state<string | null>(null);
  shelfDetailsBookId = $state<string | null>(null);

  shelfTab = $state<ShelfTabCode>('all');
  shelfSortKey = $state<ShelfSortKey>('progress');
  shelfViewMode = $state<ShelfViewMode>('grid');
  shelfRawQuery = $state<string>('');

  getShelfBooks(books: LibraryBookDto[]): LibraryBookDto[] {
    const shelfStateWithDeps: ShelfQueryState = {
      tab: this.shelfTab,
      sortKey: this.shelfSortKey,
      viewMode: this.shelfViewMode,
      rawQuery: this.shelfRawQuery,
      searchText: '',
      smartTokens: [],
      invalidTokens: [],
    };

    const { myShelfBooks } = partitionHomeBooks(books);
    return selectShelfBooks(myShelfBooks, shelfStateWithDeps);
  }

  setRoute(r: AppRoute): void {
    this.route = r;
    this.shelfDetailsBookId = null;
  }

  openDetails(bookId: string): void {
    this.previewBookId = bookId;
  }

  openShelfDetails(bookId: string): void {
    this.previewBookId = bookId;
    this.shelfDetailsBookId = bookId;
  }

  closeShelfDetails(): void {
    this.shelfDetailsBookId = null;
  }
}

export const homeState = new HomeStateManager();
