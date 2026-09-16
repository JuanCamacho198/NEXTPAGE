import {
  BUILTIN_GUTENDEX,
  isCatalogError,
  liveCatalogProvider,
  type CatalogBook,
  type CatalogErrorCode,
  type CatalogFeaturedSort,
  type CatalogProvider,
} from '$lib/shared/services/catalog';
import type { MessageKey } from '$lib/shared/i18n/messages.en';
import {
  buildRailSpecs,
  DISCOVER_RAIL_LIMIT,
  loadRail,
  RAIL_SCOPE_LIMIT,
  type DiscoverRailSpec,
} from './railPlan';

/**
 * Per-rail machine: `Hidden` renders nothing (empty or unserved), `Loaded`
 * carries the page, and `Error` keeps a stable code plus a retry affordance.
 * A failure in one rail never mutates another rail.
 */
export type DiscoverRailState =
  | { kind: 'Hidden' }
  | { kind: 'Loading' }
  | { kind: 'Loaded'; books: CatalogBook[]; totalCount: number }
  | { kind: 'Error'; code: CatalogErrorCode; offline: boolean };

/** Rail-scoped browse target opened from a rail header ("Ver todo"). */
export type DiscoverBrowseScope =
  | { kind: 'term'; term: string; titleKey: MessageKey }
  | { kind: 'featured'; sort: CatalogFeaturedSort; titleKey: MessageKey };

export interface DiscoverRailsDeps {
  /** Defaults to the live composite catalog; tests inject a recording fake. */
  provider?: CatalogProvider;
  /** Injected clock, so rotation determinism is pinned instead of mocked. */
  now?: () => Date;
}

/** Connectivity failures that must flip the hero pill to offline. */
export function isOfflineCatalogCode(code: CatalogErrorCode): boolean {
  return code === 'NETWORK_ERROR' || code === 'RATE_LIMITED';
}

/** Unknown throwables redact to UPSTREAM_ERROR; typed catalog errors keep their code. */
export function catalogCodeOf(err: unknown): CatalogErrorCode {
  return isCatalogError(err) ? err.code : 'UPSTREAM_ERROR';
}

/**
 * Owns the 3-rail set and everything a rail needs: the index-stable plan, the
 * per-rail machine, isolated retry, remount dedup, and the rail-scoped browse
 * state. There is deliberately no screen-level rail status.
 */
export class DiscoverRailsDomainState {
  /** Index-stable rail plan; rebuilt per refresh so the day rolls forward. */
  specs = $state<DiscoverRailSpec[]>([]);
  /** Parallel to `specs`: index `i` always belongs to `specs[i]`. */
  rails = $state<DiscoverRailState[]>([]);
  /** False once any rail settles with a connectivity failure. */
  isOnline = $state(true);
  /** Non-null while a rail-scoped browse view is open. */
  scope = $state<DiscoverBrowseScope | null>(null);
  /** Books of the open scope, deduplicated across pages. */
  scopeBooks = $state<CatalogBook[]>([]);
  /** True once a refresh settled; remounts reuse it instead of refetching. */
  settled = $state(false);

  private readonly provider: CatalogProvider;
  private readonly now: () => Date;
  private scopePage = 0;
  private scopeExhausted = false;

  constructor(deps: DiscoverRailsDeps = {}) {
    this.provider = deps.provider ?? liveCatalogProvider;
    this.now = deps.now ?? (() => new Date());
    this.specs = [...buildRailSpecs(this.now())];
    this.rails = this.specs.map(() => ({ kind: 'Hidden' }) as DiscoverRailState);
  }

  /** Mount hook: a settled rail set is reused, so remounts do not refetch. */
  async ensureLoaded(): Promise<void> {
    if (this.settled) return;
    await this.refreshRails();
  }

  /**
   * Rebuild the rail plan and settle every rail. Each rail is isolated: a throw
   * becomes that rail's `Error` and empty results collapse to `Hidden`.
   */
  async refreshRails(): Promise<void> {
    this.specs = [...buildRailSpecs(this.now())];
    this.rails = this.specs.map(() => ({ kind: 'Loading' }) as DiscoverRailState);
    this.isOnline = true;
    const settled = await Promise.all(this.specs.map((spec) => this.loadOne(spec)));
    this.rails = settled;
    if (settled.some((rail) => rail.kind === 'Error' && rail.offline)) this.isOnline = false;
    this.settled = true;
  }

  /** Re-resolve ONLY the failing rail; every other rail keeps its books and state. */
  async retryRail(index: number): Promise<void> {
    const spec = this.specs[index];
    const current = this.rails[index];
    if (!spec || !current || current.kind !== 'Error') return;
    this.rails[index] = { kind: 'Loading' };
    const settled = await this.loadOne(spec);
    this.rails[index] = settled;
    if (settled.kind === 'Error' && settled.offline) this.isOnline = false;
  }

  /**
   * Open a rail-scoped browse view: term scopes page through a source search,
   * featured scopes resolve a single first page (the port is first-page-only).
   */
  async openScope(scope: DiscoverBrowseScope): Promise<void> {
    this.scope = scope;
    this.scopeBooks = [];
    this.scopePage = 0;
    this.scopeExhausted = false;
    await this.loadScopeNextPage();
  }

  /** Append the scope's next page; term scopes stop once a page reports no next. */
  async loadScopeNextPage(): Promise<void> {
    const scope = this.scope;
    if (!scope) return;
    if (scope.kind === 'featured') {
      if (this.scopeBooks.length > 0) return;
      const page = await this.provider.featured(scope.sort, RAIL_SCOPE_LIMIT);
      this.scopeBooks = page.results.slice(0, RAIL_SCOPE_LIMIT);
      return;
    }
    if (this.scopeExhausted) return;
    const page = await this.provider.searchSource(BUILTIN_GUTENDEX, scope.term, this.scopePage + 1);
    const seen = new Set(this.scopeBooks.map((book) => book.id));
    for (const book of page.results) {
      if (!seen.has(book.id)) {
        seen.add(book.id);
        this.scopeBooks.push(book);
      }
    }
    this.scopePage += 1;
    this.scopeExhausted = page.nextPage === null;
  }

  /** Leave the scoped view and drop its books. */
  closeScope(): void {
    this.scope = null;
    this.scopeBooks = [];
    this.scopePage = 0;
    this.scopeExhausted = false;
  }

  private async loadOne(spec: DiscoverRailSpec): Promise<DiscoverRailState> {
    try {
      const page = await loadRail(this.provider, spec, DISCOVER_RAIL_LIMIT);
      const books = page.results.slice(0, DISCOVER_RAIL_LIMIT);
      if (books.length === 0) return { kind: 'Hidden' };
      return { kind: 'Loaded', books, totalCount: page.totalCount };
    } catch (err) {
      const code = catalogCodeOf(err);
      return { kind: 'Error', code, offline: isOfflineCatalogCode(code) };
    }
  }
}
