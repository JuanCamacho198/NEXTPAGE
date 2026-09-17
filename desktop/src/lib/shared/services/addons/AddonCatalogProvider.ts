/**
 * AddonCatalogProvider — one CatalogProvider per installed addon manifest.
 * Sources are `addon:<addonId>`. Manifests MAY declare endpoint templates
 * (searchUrl/detailsUrl, validated https by the manifest validator); when
 * present, search/getDetails fetch the addon's own catalog payloads through
 * the platform network layer (desktop: the fetchAddonResource Rust command)
 * and parse them into CatalogBooks with provider = the addon source id.
 * Endpoint-less manifests stay browse-only: stable empty page / NOT_FOUND,
 * zero I/O. Malformed payloads reject the whole page with a stable error.
 */
import { catalogError, mapHttpStatusToCode } from '../catalog/errors';
import { backoffDelayMs, MAX_PAGE_SIZE, shouldRetryStatus } from '../catalog/policy';
import type { AddonConsentGate } from './AddonConsent';
import type {
  CatalogBook,
  CatalogFeaturedSort,
  CatalogProvider,
  CatalogSource,
  CatalogSourceInfo,
  PagedResult,
} from '../catalog/CatalogProvider';
import { addonSource } from '../catalog/CatalogProvider';
import { computeNextPage, resolveDownloadUrl } from '../catalog/mappers';
import { MAX_MANIFEST_BYTES, type AddonManifest } from '@nextpage/manifest-validator';
import { defaultAddonTransport, type AddonTransport } from './AddonRegistry';

const EMPTY_PAGE: PagedResult = { results: [], nextPage: null, totalCount: 0 };

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function malformed(detail: string): never {
  throw catalogError('UPSTREAM_ERROR', `malformed addon payload: ${detail}`);
}

/** Substitute `{placeholders}` in a validated endpoint template. */
function renderTemplate(template: string, params: Record<string, string>): string {
  return template.replace(/\{(\w+)\}/g, (match, key: string) => params[key] ?? match);
}

/** Optional cover/download URLs: https strings pass, anything else maps to null. */
function httpsOrNull(value: unknown): string | null {
  return typeof value === 'string' && value.startsWith('https://') ? value : null;
}

/** Optional string-array field: absent → []; anything else → stable malformed error. */
function stringArray(value: unknown, field: string): string[] {
  if (value === undefined) return [];
  if (!Array.isArray(value) || !value.every((v) => typeof v === 'string')) {
    malformed(`${field} must be an array of strings`);
  }
  return value as string[];
}

function parseAddonBook(value: unknown, bookId: string, sourceId: string): CatalogBook {
  if (!isPlainObject(value)) malformed('book must be an object');
  if (!isNonEmptyString(value.id) || !isNonEmptyString(value.title)) {
    malformed('book missing id/title');
  }
  return {
    id: bookId,
    provider: sourceId,
    title: value.title,
    authors: stringArray(value.authors, 'authors'),
    coverUrl: httpsOrNull(value.coverUrl),
    languages: stringArray(value.languages, 'languages'),
    subjects: stringArray(value.subjects, 'subjects'),
    downloadUrl: httpsOrNull(value.downloadUrl),
  };
}

function parseSearchPayload(payload: unknown, sourceId: string, page: number): PagedResult {
  if (!isPlainObject(payload) || !Array.isArray(payload.results)) {
    malformed('payload must be an object with a results array');
  }
  let totalCount = payload.results.length;
  if (payload.totalCount !== undefined) {
    if (
      typeof payload.totalCount !== 'number' ||
      !Number.isInteger(payload.totalCount) ||
      payload.totalCount < 0
    ) {
      malformed('totalCount must be a non-negative integer');
    }
    totalCount = payload.totalCount;
  }
  const books = payload.results.map((entry, i) => {
    if (!isPlainObject(entry) || !isNonEmptyString(entry.id)) malformed(`book ${i} missing id`);
    return parseAddonBook(entry, `${sourceId}:${entry.id as string}`, sourceId);
  });
  const clamped = books.slice(0, MAX_PAGE_SIZE);
  return {
    results: clamped,
    nextPage: computeNextPage(page, clamped.length, totalCount),
    totalCount,
  };
}

/**
 * Fail-closed default gate: a provider built without an explicit consent gate
 * (tests, one-off constructions) denies every resolve. Production providers
 * always receive the shared `addonConsent` singleton via `liveComposite`.
 */
const denyAllAddonConsent: AddonConsentGate = {
  ensureLoaded: () => Promise.resolve(),
  hasConsent: () => false,
};

/**
 * Addon access resolution (slice 8 shell). Slice 9 fills the
 * `resolveUrl` / capabilities / access-item logic; this shell only enforces
 * the consent gate and the no-endpoint empty result, both with zero I/O.
 */
export interface AddonAccessOption {
  label: string;
  url: string;
}

export interface AddonAccessResolution {
  canDownloadInApp: boolean;
  downloadUrl: string | null;
  options: AddonAccessOption[];
}

/** Empty resolution: no in-app download, no external options, zero I/O. */
export const EMPTY_ADDON_ACCESS: AddonAccessResolution = {
  canDownloadInApp: false,
  downloadUrl: null,
  options: [],
};

export class AddonCatalogProvider implements CatalogProvider {
  private readonly source: CatalogSourceInfo;
  private readonly addonId: string;
  private readonly manifest: AddonManifest;
  private readonly searchUrl?: string;
  private readonly detailsUrl?: string;
  private readonly consent: AddonConsentGate;

  constructor(
    manifest: AddonManifest,
    addonId: string,
    private readonly transport: AddonTransport = defaultAddonTransport(),
    private readonly retryDelayMs: number = backoffDelayMs(0),
    consent: AddonConsentGate = denyAllAddonConsent,
  ) {
    this.source = {
      sourceId: addonSource(addonId),
      name: manifest.name,
      kind: 'addon' as const,
    };
    this.addonId = addonId;
    this.manifest = manifest;
    this.searchUrl = manifest.searchUrl;
    this.detailsUrl = manifest.detailsUrl;
    this.consent = consent;
  }

  listSources(): CatalogSourceInfo[] {
    return [this.source];
  }

  async search(query: string, page: number): Promise<PagedResult> {
    if (!this.searchUrl) return EMPTY_PAGE;
    const url = renderTemplate(this.searchUrl, {
      query: encodeURIComponent(query),
      page: String(page),
    });
    const payload = await this.fetchJson(url);
    return parseSearchPayload(payload, this.source.sourceId, page);
  }

  async getDetails(id: string): Promise<CatalogBook> {
    const prefix = `${this.source.sourceId}:`;
    if (!id.startsWith(prefix) || id.length <= prefix.length) {
      throw catalogError('NOT_FOUND', `unknown catalog id ${id}`);
    }
    if (!this.detailsUrl) {
      throw catalogError('NOT_FOUND', `addon source has no details endpoint: ${id}`);
    }
    const url = renderTemplate(this.detailsUrl, {
      bookId: encodeURIComponent(id.slice(prefix.length)),
    });
    return parseAddonBook(await this.fetchJson(url), id, this.source.sourceId);
  }

  /** Addons opt out of featured rails; the rail auto-hides (fail-closed). */
  async featured(_sort: CatalogFeaturedSort, limit: number): Promise<PagedResult> {
    if (!Number.isInteger(limit) || limit < 1) {
      throw catalogError('INVALID_PAGE', `limit must be >= 1, got ${limit}`);
    }
    return EMPTY_PAGE;
  }

  supportsFeatured(_sort: CatalogFeaturedSort): boolean {
    return false;
  }

  /**
   * Per-source search scoped to this addon's own source; anything else fails
   * closed with an empty page (never a crash, never a silent composite search).
   */
  async searchSource(sourceId: CatalogSource, query: string, page: number): Promise<PagedResult> {
    if (sourceId !== this.source.sourceId) {
      return EMPTY_PAGE;
    }
    return this.search(query, page);
  }

  /**
   * Consent-gated resolve (slice 8 shell). Consent gates RESOLVE ONLY:
   * `search` / `getDetails` stay ungated (Android parity + the spec's
   * "resolve operation" phrasing).
   *
   * - No consent ⇒ rejects `CONSENT_REQUIRED` with ZERO transport calls.
   * - No `resolveUrl` (every v1 manifest) ⇒ empty resolution, zero I/O.
   * - The `resolveUrl` / capabilities / access-item fetch logic lands in
   *   slice 9; until then a granted resolve also returns the empty
   *   resolution without touching the transport.
   */
  async resolveAddonAccess(_book: CatalogBook): Promise<AddonAccessResolution> {
    await this.consent.ensureLoaded();
    if (!this.consent.hasConsent(this.addonId)) {
      throw catalogError(
        'CONSENT_REQUIRED',
        `addon has no network consent: ${this.source.sourceId}`,
      );
    }
    const resolveUrl = (this.manifest as { resolveUrl?: unknown }).resolveUrl;
    if (typeof resolveUrl !== 'string' || resolveUrl.length === 0) {
      return { ...EMPTY_ADDON_ACCESS, options: [] };
    }
    // Slice-9 boundary: the endpoint/capability/access-item logic is not here
    // yet, so a granted resolve with an endpoint still returns the empty
    // resolution and performs zero I/O.
    return { ...EMPTY_ADDON_ACCESS, options: [] };
  }

  private async fetchJson(url: string): Promise<unknown> {
    let res = await this.callTransport(url);
    if (shouldRetryStatus(res.status)) {
      await new Promise((resolve) => setTimeout(resolve, this.retryDelayMs));
      res = await this.callTransport(url);
    }
    if (res.status < 200 || res.status >= 300) {
      throw catalogError(mapHttpStatusToCode(res.status), `addon payload status ${res.status}`);
    }
    if (res.body.byteLength > MAX_MANIFEST_BYTES) {
      throw catalogError('UPSTREAM_ERROR', 'addon payload exceeds size cap');
    }
    try {
      return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(res.body));
    } catch {
      throw catalogError('UPSTREAM_ERROR', 'addon payload is not valid JSON');
    }
  }

  private async callTransport(url: string) {
    try {
      return await this.transport(url);
    } catch {
      throw catalogError('NETWORK_ERROR', 'addon payload request failed');
    }
  }

  resolveDownloadUrl(formats: Record<string, string>, preferEpub: boolean): string {
    return resolveDownloadUrl(formats, preferEpub);
  }
}
