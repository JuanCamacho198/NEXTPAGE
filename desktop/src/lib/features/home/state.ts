import { writable, type Writable } from "svelte/store";
import type { LibraryBookDto } from "$lib/shared/types";
import { partitionHomeBooks, selectShelfBooks } from "$lib/shared/stores/homeState";

export type AppRoute = "home" | "reader" | "library" | "stats" | "settings";

export const route = writable<AppRoute>("home");
export const previewBookId = writable<string | null>(null);
export const shelfDetailsBookId = writable<string | null>(null);

export const shelfTab = writable<string>("all");
export const shelfSortKey = writable<string>("progress");
export const shelfViewMode = writable<"grid" | "list">("grid");
export const shelfRawQuery = writable<string>("");

export function getShelfBooks(books: LibraryBookDto[]) {
  let currentTab = "all";
  let currentSort = "progress";
  let currentView: "grid" | "list" = "grid";
  let currentQuery = "";
  
  shelfTab.subscribe(v => currentTab = v)();
  shelfSortKey.subscribe(v => currentSort = v)();
  shelfViewMode.subscribe(v => currentView = v)();
  shelfRawQuery.subscribe(v => currentQuery = v)();
  
  const shelfStateWithDeps = {
    tab: currentTab,
    sortKey: currentSort,
    viewMode: currentView,
    rawQuery: currentQuery,
    searchText: "",
    smartTokens: [],
    invalidTokens: []
  };
  
  const { myShelfBooks } = partitionHomeBooks(books);
  return selectShelfBooks(myShelfBooks, shelfStateWithDeps);
}

export function setRoute(r: AppRoute) {
  route.set(r);
  shelfDetailsBookId.set(null);
}

export function openDetails(bookId: string) {
  previewBookId.set(bookId);
}

export function openShelfDetails(bookId: string) {
  previewBookId.set(bookId);
  shelfDetailsBookId.set(bookId);
}

export function closeShelfDetails() {
  shelfDetailsBookId.set(null);
}