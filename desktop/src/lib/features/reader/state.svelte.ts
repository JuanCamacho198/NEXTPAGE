// ─── Feature state: reader — migrated to $state runes ───
// Canonical state is AppState ($lib/stores/AppState.svelte).
// This file retains the public API surface for the features/reader barrel.

import type { searchBookText } from "$lib/shared/api/tauriClient";
import type { SearchBookTextResponse, SearchNavigationTarget } from "$lib/shared/types";

class ReaderStateManager {
  activeReadingBookId = $state<string | null>(null);
  cfiLocation = $state("");
  percentage = $state(0);
  searchResponse = $state<SearchBookTextResponse | null>(null);
  searchTargetLocator = $state<string | null>(null);
  isSearching = $state(false);
  unavailableReason = $state<string | null>(null);
}

export const readerState = new ReaderStateManager();
