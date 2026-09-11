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
import type {
  CatalogBook,
  CatalogProvider,
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

export class AddonCatalogProvider implements CatalogProvider {
  private readonly source: CatalogSourceInfo;
  private readonly searchUrl?: string;
  private readonly detailsUrl?: string;

  constructor(
    manifest: AddonManifest,
    addonId: string,
    private readonly transport: AddonTransport = defaultAddonTransport(),
    private readonly retryDelayMs: number = backoffDelayMs(0),
  ) {
    this.source = {
      sourceId: addonSource(addonId),
      name: manifest.name,
      kind: 'addon' as const,
    };
    this.searchUrl = manifest.searchUrl;
    this.detailsUrl = manifest.detailsUrl;
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
