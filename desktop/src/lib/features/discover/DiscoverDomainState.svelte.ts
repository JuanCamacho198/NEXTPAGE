import {
  liveCatalogProvider,
  type CatalogBook,
  type CatalogErrorCode,
  type CatalogProvider,
  type CatalogSourceInfo,
} from '$lib/shared/services/catalog';
import {
  fetchBytesWithProgress,
  importDiscoverBytes,
  type DiscoverDownloadPorts,
} from './discoverDownloadImport';
import { TRENDING_CHIPS } from './discoverChips';
import {
  catalogCodeOf,
  DiscoverRailsDomainState,
  isOfflineCatalogCode,
  type DiscoverBrowseScope,
  type DiscoverRailState,
  type DiscoverRailsDeps,
} from './DiscoverRailsDomainState.svelte';

export type DiscoverStatus =
  'idle' | 'loading' | 'loadingMore' | 'loaded' | 'empty' | 'error' | 'offline';

export type DiscoverDetailStatus = 'closed' | 'loading' | 'loaded' | 'notFound' | 'error';

/** In-app download-to-import lifecycle for the open detail book. */
export type DiscoverDownloadState =
  'idle' | 'downloading' | 'importing' | 'imported' | 'cancelled' | 'error';

/** Chip taxonomy + chip filtering live in `discoverChips.ts`; re-exported for consumers. */
export { CHIP_KEYWORDS, TRENDING_CHIPS, filterBooksByChip, matchesChip } from './discoverChips';
/** The 3-rail plan lives in `railPlan.ts`; re-exported so existing imports keep working. */
export { DISCOVER_RAIL_COUNT, DISCOVER_RAIL_LIMIT, type DiscoverRailSpec } from './railPlan';
export type { DiscoverBrowseScope, DiscoverRailState } from './DiscoverRailsDomainState.svelte';

class DiscoverDomainState {
  query = $state('');
  status = $state<DiscoverStatus>('idle');
  books = $state<CatalogBook[]>([]);
  totalCount = $state(0);
  nextPage = $state<number | null>(null);
  activePage = $state(0);
  errorCode = $state<CatalogErrorCode | null>(null);
  detail = $state<CatalogBook | null>(null);
  detailStatus = $state<DiscoverDetailStatus>('closed');
  /** Download-to-import lifecycle for the open detail book. */
  downloadState = $state<DiscoverDownloadState>('idle');
  /** Code-only failure message for the retry UI (redacted at the boundary). */
  downloadError = $state<string | null>(null);
  /** Bytes received so far in the current download. */
  progressBytes = $state(0);
  /** Total bytes from `content-length`, or null when the host omits it. */
  progressTotal = $state<number | null>(null);
  /** Static chip labels for client-side filtering over loaded rails. */
  trending = $state<string[]>([...TRENDING_CHIPS]);

  /** Rail orchestration (3-rail plan, per-rail machine, scoped browse). */
  readonly railsState: DiscoverRailsDomainState;

  private lastAttemptedPage = 0;
  private downloadController: AbortController | null = null;

  constructor(
    private readonly provider: CatalogProvider = liveCatalogProvider,
    private readonly downloadPorts: DiscoverDownloadPorts = {},
    rails: Pick<DiscoverRailsDeps, 'now'> = {},
  ) {
    this.railsState = new DiscoverRailsDomainState({ provider: this.provider, now: rails.now });
  }

  /** Browse rails in stable index order; `Hidden` rails render nothing. */
  get rails(): DiscoverRailState[] {
    return this.railsState.rails;
  }

  /** False once any rail observed a connectivity failure. */
  get isOnline(): boolean {
    return this.railsState.isOnline;
  }

  setQuery(query: string): void {
    this.query = query;
    if (query.trim() === '') {
      this.resetToIdle();
    }
  }

  async searchFirstPage(): Promise<void> {
    if (this.query.trim() === '') {
      this.resetToIdle();
      return;
    }
    this.status = 'loading';
    this.errorCode = null;
    this.lastAttemptedPage = 1;
    try {
      const page = await this.provider.search(this.query, 1);
      this.books = page.results;
      this.totalCount = page.totalCount;
      this.nextPage = page.nextPage;
      this.activePage = 1;
      this.status = page.results.length === 0 ? 'empty' : 'loaded';
    } catch (err) {
      const code = catalogCodeOf(err);
      this.errorCode = code;
      this.status = statusForCode(code);
    }
  }

  async loadNextPage(): Promise<void> {
    const page = this.nextPage;
    if (page == null || this.status === 'loading' || this.status === 'loadingMore') {
      return;
    }
    this.status = 'loadingMore';
    this.lastAttemptedPage = page;
    try {
      const result = await this.provider.search(this.query, page);
      const seen = new Set(this.books.map((b) => b.id));
      for (const book of result.results) {
        if (!seen.has(book.id)) {
          seen.add(book.id);
          this.books.push(book);
        }
      }
      this.totalCount = result.totalCount;
      this.nextPage = result.nextPage;
      this.activePage = page;
      this.errorCode = null;
      this.status = 'loaded';
    } catch (err) {
      const code = catalogCodeOf(err);
      this.errorCode = code;
      this.status = statusForCode(code);
    }
  }

  async openDetail(id: string): Promise<void> {
    // Opening a book supersedes any transfer in flight for the previous one.
    this.downloadController?.abort();
    this.downloadController = null;
    this.resetDownload();
    this.detailStatus = 'loading';
    this.detail = null;
    try {
      this.detail = await this.provider.getDetails(id);
      this.detailStatus = 'loaded';
    } catch (err) {
      const code = catalogCodeOf(err);
      this.detailStatus = code === 'NOT_FOUND' ? 'notFound' : 'error';
    }
  }

  dismissDetail(): void {
    this.downloadController?.abort();
    this.downloadController = null;
    this.detail = null;
    this.detailStatus = 'closed';
    this.resetDownload();
  }

  /**
   * Fetch the open book's catalog URL, then import the bytes into the
   * library. Progress reports received bytes; cancel aborts the fetch so a
   * halted transfer never reaches persistence.
   */
  async startDownload(): Promise<void> {
    if (this.downloadState === 'downloading' || this.downloadState === 'importing') return;
    const book = this.detail;
    const url = book?.downloadUrl;
    if (!book || !url || url.trim() === '') {
      this.downloadState = 'error';
      this.downloadError = 'UNAVAILABLE_DOWNLOAD';
      return;
    }
    const controller = new AbortController();
    this.downloadController = controller;
    this.downloadState = 'downloading';
    this.downloadError = null;
    this.progressBytes = 0;
    this.progressTotal = null;
    try {
      const bytes = await fetchBytesWithProgress(
        url,
        controller.signal,
        (done, total) => {
          this.progressBytes = done;
          this.progressTotal = total;
        },
        this.downloadPorts.fetchFn,
      );
      if (controller.signal.aborted) {
        this.downloadState = 'cancelled';
        return;
      }
      this.downloadState = 'importing';
      const result = await importDiscoverBytes(book, bytes, this.downloadPorts);
      if (result.ok) {
        this.downloadState = 'imported';
        this.downloadError = null;
      } else {
        this.downloadState = 'error';
        this.downloadError = result.error;
      }
    } catch (err) {
      if (controller.signal.aborted || (err instanceof Error && err.name === 'AbortError')) {
        this.downloadState = 'cancelled';
        this.downloadError = null;
      } else {
        this.downloadState = 'error';
        this.downloadError = err instanceof Error ? err.message : 'DOWNLOAD_FAILED';
      }
    } finally {
      if (this.downloadController === controller) this.downloadController = null;
    }
  }

  /** Abort an in-flight fetch; the halted transfer is never persisted. */
  cancelDownload(): void {
    if (this.downloadState !== 'downloading') return;
    this.downloadController?.abort();
  }

  /** Re-run the last failed or halted transfer for the same open book. */
  async retryDownload(): Promise<void> {
    if (this.downloadState !== 'error' && this.downloadState !== 'cancelled') return;
    await this.startDownload();
  }

  async retry(): Promise<void> {
    if (this.lastAttemptedPage <= 1) {
      await this.searchFirstPage();
      return;
    }
    this.nextPage = this.lastAttemptedPage;
    await this.loadNextPage();
  }

  /**
   * Live source list for the hero pill (`En línea · N fuentes` where
   * N = the returned length). Pure read-through, no I/O.
   */
  refreshSources(): CatalogSourceInfo[] {
    return this.provider.listSources();
  }

  /** Load the rail set once per session; a settled set is reused on remount. */
  async ensureRailsLoaded(): Promise<void> {
    await this.railsState.ensureLoaded();
  }

  /** Re-resolve every rail of the index-stable 3-rail plan. */
  async refreshRails(): Promise<void> {
    await this.railsState.refreshRails();
  }

  /** Re-resolve only the rail at `index`; other rails keep their content. */
  async retryRail(index: number): Promise<void> {
    await this.railsState.retryRail(index);
  }

  /** Open the rail-scoped browse view for a rail header ("Ver todo"). */
  async openRailScope(scope: DiscoverBrowseScope): Promise<void> {
    await this.railsState.openScope(scope);
  }

  /** Append the next page of the open rail scope. */
  async loadRailScopeNextPage(): Promise<void> {
    await this.railsState.loadScopeNextPage();
  }

  /** Leave the rail-scoped browse view. */
  closeRailScope(): void {
    this.railsState.closeScope();
  }

  private resetToIdle(): void {
    this.books = [];
    this.totalCount = 0;
    this.nextPage = null;
    this.activePage = 0;
    this.errorCode = null;
    this.status = 'idle';
    this.lastAttemptedPage = 0;
  }

  private resetDownload(): void {
    this.downloadState = 'idle';
    this.downloadError = null;
    this.progressBytes = 0;
    this.progressTotal = null;
  }
}

function statusForCode(code: CatalogErrorCode): DiscoverStatus {
  return isOfflineCatalogCode(code) ? 'offline' : 'error';
}

export const discoverState = new DiscoverDomainState();
export { DiscoverDomainState };
