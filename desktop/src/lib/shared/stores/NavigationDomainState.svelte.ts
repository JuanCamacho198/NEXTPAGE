import type { AppRoute } from "$lib/shared/stores/homeState";

const DOMAIN = {
  LIBRARY: "library",
  STATS: "stats",
  SEARCH: "search",
} as const;

type Domain = (typeof DOMAIN)[keyof typeof DOMAIN];

class NavigationDomainState {
  // ─── State ───
  route = $state<AppRoute>("home");
  previewBookId = $state<string | null>(null);
  shelfDetailsBookId = $state<string | null>(null);
  libraryUnavailableReason = $state<string | null>(null);
  statsUnavailableReason = $state<string | null>(null);
  searchUnavailableReason = $state<string | null>(null);

  readonly DOMAIN = DOMAIN;

  // ─── Navigation ───

  navigateToHome(): void {
    this.route = "home";
    this.shelfDetailsBookId = null;
  }

  navigateToLibrary(): void {
    this.route = "library";
    this.shelfDetailsBookId = null;
  }

  navigateToStats(): void {
    this.route = "stats";
    this.shelfDetailsBookId = null;
  }

  navigateToHighlights(): void {
    this.route = "highlights";
    this.shelfDetailsBookId = null;
  }

  navigateToSettings(): void {
    this.route = "settings";
    this.shelfDetailsBookId = null;
  }

  backToHome(): void {
    this.route = "home";
  }

  // ─── Domain unavailability ───

  setDomainUnavailable(domain: Domain, reason: string | null): void {
    if (domain === DOMAIN.LIBRARY) {
      this.libraryUnavailableReason = reason;
      return;
    }

    if (domain === DOMAIN.STATS) {
      this.statsUnavailableReason = reason;
      return;
    }

    this.searchUnavailableReason = reason;
  }

  // ─── Details ───

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

export const navigationState = new NavigationDomainState();
export { NavigationDomainState };
