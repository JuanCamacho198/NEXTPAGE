// ─── DEPRECATED: Superseded by AppState ($lib/stores/AppState.svelte) ───
// This file re-exports from state.svelte.ts for backward compat.
// New code should import from AppState directly.
// TODO: Remove this file once all components migrate to AppState.

export {
  LIBRARY_VIEW_MODE,
  BULK_IMPORT_STATUS,
  COLLECTION_COLOR_OPTIONS,
  libraryState,
  ReaderBook,
  type LibraryViewMode,
} from "./state.svelte";

// Backward compat alias — LibraryState = libraryState
export { libraryState as LibraryState } from "./state.svelte";
