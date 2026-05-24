// NOTE: canonical components are in components/layout/
// These re-exports keep the barrel intact for existing consumers
export { default as AppSidebar } from "../../components/layout/AppSidebar.svelte";
export { default as HomeDesktopView } from "../../components/layout/HomeDesktopView.svelte";
export { default as LibraryShelfScreen } from "../../components/layout/LibraryShelfScreen.svelte";

// State is managed by AppState ($lib/stores/AppState.svelte).
// The homeState singleton is kept for backward compat with legacy consumers.
export { homeState } from "./state";
export type { AppRoute } from "./state";

// Re-export from canonical homeState
export {
  createShelfQueryState,
  getShelfQueryWarnings,
  partitionHomeBooks,
  selectShelfBooks,
  updateShelfQueryState,
  promoteBookForReading,
  reconcileHomeState,
  getSafeProgressPercentage,
  type ShelfQueryState,
  type ShelfTabCode,
  type ShelfSortKey,
  type ShelfViewMode,
  type SmartQueryField,
  type ShelfQueryToken,
  type ShelfQueryInvalidTokenReason,
} from "$lib/shared/stores/homeState";