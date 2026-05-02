export { default as AppSidebar } from "./components/AppSidebar.svelte";
export { default as HomeDesktopView } from "./components/HomeDesktopView.svelte";
export { default as LibraryShelfScreen } from "./components/LibraryShelfScreen.svelte";

// Export stores and functions from state
export {
  route,
  previewBookId,
  shelfDetailsBookId,
  shelfTab,
  shelfSortKey,
  shelfViewMode,
  shelfRawQuery,
  setRoute,
  openDetails,
  openShelfDetails,
  closeShelfDetails,
  getShelfBooks,
} from "./state";

// Re-export from homeState, but avoid AppRoute conflict
export {
  createShelfQueryState,
  getShelfQueryWarnings,
  partitionHomeBooks,
  selectShelfBooks,
  updateShelfQueryState,
  promoteBookForReading,
  reconcileHomeState,
  getSafeProgressPercentage,
  type AppRoute,
  type ShelfQueryState,
  type ShelfTabCode,
  type ShelfSortKey,
  type ShelfViewMode,
  type SmartQueryField,
  type ShelfQueryToken,
  type ShelfQueryInvalidTokenReason,
} from "$lib/shared/stores/homeState";