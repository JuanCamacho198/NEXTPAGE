import {
  BUILTIN_GUTENDEX,
  isCatalogError,
  liveCatalogProvider,
  REQUEST_DEADLINE_MS,
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
  withDeadline,
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
  /**
   * Total bound for one rail attempt. Defaults to the shared
   * `REQUEST_DEADLINE_MS`; tests pin a smaller value with fake timers.
   */
  timeoutMs?: number;
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
  /** Stable code of the last failed scoped-browse page, or null. */
  scopeError = $state<CatalogErrorCode | null>(null);
  /** True once the open scope has no further page to offer. */
  scopeExhausted = $state(false);

  private readonly provider: CatalogProvider;
  private readonly now: () => Date;
  private readonly timeoutMs: number;
  private scopePage = 0;
  /** Guards a stale refresh/retry from overwriting a newer publish. */
  private generation = 0;

  constructor(deps: DiscoverRailsDeps = {}) {
    this.provider = deps.provider ?? liveCatalogProvider;
    this.now = deps.now ?? (() => new Date());
    this.timeoutMs = deps.timeoutMs ?? REQUEST_DEADLINE_MS;
    this.specs = [...buildRailSpecs(this.now())];
    this.rails = this.specs.map(() => ({ kind: 'Hidden' }) as DiscoverRailState);
  }

  /** Mount hook: a settled rail set is reused, so remounts do not refetch. */
  async ensureLoaded(): Promise<void> {
    if (this.settled) return;
    await this.refreshRails();
  }

  /**
   * Rebuild the rail plan and publish each rail the moment it settles: a fast
   * rail renders while a slow one is still `Loading`, and positions stay
   * index-stable. Each rail is isolated — a throw or the deadline becomes that
   * rail's `Error` and empty results collapse to `Hidden`.
   */
  async refreshRails(): Promise<void> {
    this.specs = [...buildRailSpecs(this.now())];
    this.rails = this.specs.map(() => ({ kind: 'Loading' }) as DiscoverRailState);
    this.isOnline = true;
    const generation = (this.generation += 1);
    // Every rail is in flight immediately; awaiting them one by one lets
    // `refreshRails` settle the whole set without gating any publish on the
    // slowest rail (no all-settled publish).
    const pending = this.specs.map((spec, index) => this.loadInto(index, spec, generation));
    for (const rail of pending) await rail;
    if (generation !== this.generation) return;
    this.settled = true;
  }

  /** Re-resolve ONLY the failing rail; every other rail keeps its books and state. */
  async retryRail(index: number): Promise<void> {
    const spec = this.specs[index];
    const current = this.rails[index];
    if (!spec || !current || current.kind !== 'Error') return;
    const generation = this.generation;
    this.rails[index] = { kind: 'Loading' };
    const settled = await this.loadOne(spec);
    if (generation !== this.generation) return;
    this.rails[index] = settled;
    if (settled.kind === 'Error' && settled.offline) this.isOnline = false;
  }

  /**
   * Open a rail-scoped browse view: term scopes page through a source search,
   * featured scopes resolve a single first page (the port is first-page-only).
   * A failure lands in `scopeError` instead of throwing at the caller.
   */
  async openScope(scope: DiscoverBrowseScope): Promise<void> {
    this.scope = scope;
    this.scopeBooks = [];
    this.scopePage = 0;
    this.scopeExhausted = false;
    this.scopeError = null;
    await this.loadScopeNextPage();
  }

  /**
   * Append the scope's next page. A term scope stops once a page reports no next
   * page; a featured scope is single-page by contract (≤ `RAIL_SCOPE_LIMIT`).
   */
  async loadScopeNextPage(): Promise<void> {
    const scope = this.scope;
    if (!scope) return;
    this.scopeError = null;
    try {
      if (scope.kind === 'featured') {
        if (this.scopeBooks.length > 0) return;
        const page = await withDeadline(
          this.provider.featured(scope.sort, RAIL_SCOPE_LIMIT),
          this.timeoutMs,
        );
        if (this.scope !== scope) return;
        this.scopeBooks = page.results.slice(0, RAIL_SCOPE_LIMIT);
        this.scopeExhausted = true;
        return;
      }
      if (this.scopeExhausted) return;
      const page = await withDeadline(
        this.provider.searchSource(BUILTIN_GUTENDEX, scope.term, this.scopePage + 1),
        this.timeoutMs,
      );
      if (this.scope !== scope) return;
      const seen = new Set(this.scopeBooks.map((book) => book.id));
      for (const book of page.results) {
        if (!seen.has(book.id)) {
          seen.add(book.id);
          this.scopeBooks.push(book);
        }
      }
      this.scopePage += 1;
      this.scopeExhausted = page.nextPage === null;
    } catch (err) {
      this.scopeError = catalogCodeOf(err);
    }
  }

  /** Leave the scoped view and drop its books. */
  closeScope(): void {
    this.scope = null;
    this.scopeBooks = [];
    this.scopePage = 0;
    this.scopeExhausted = false;
    this.scopeError = null;
  }

  /** Resolve one rail and publish it in place; a stale generation never writes. */
  private async loadInto(index: number, spec: DiscoverRailSpec, generation: number): Promise<void> {
    const settled = await this.loadOne(spec);
    if (generation !== this.generation) return;
    this.rails[index] = settled;
    if (settled.kind === 'Error' && settled.offline) this.isOnline = false;
  }

  private async loadOne(spec: DiscoverRailSpec): Promise<DiscoverRailState> {
    try {
      // TOTAL rail bound: one attempt — including its single delayed retry — can
      // never leave the rail `Loading`, even for a provider that ignores signals.
      const page = await withDeadline(
        loadRail(this.provider, spec, DISCOVER_RAIL_LIMIT),
        this.timeoutMs,
      );
      const books = page.results.slice(0, DISCOVER_RAIL_LIMIT);
      if (books.length === 0) return { kind: 'Hidden' };
      return { kind: 'Loaded', books, totalCount: page.totalCount };
    } catch (err) {
      const code = catalogCodeOf(err);
      return { kind: 'Error', code, offline: isOfflineCatalogCode(code) };
    }
  }
}
