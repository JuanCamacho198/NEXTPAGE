/**
 * AddonCatalogProvider — one CatalogProvider per installed addon manifest.
 * Sources are `addon:<addonId>`; browse-only in this change: search returns an
 * empty page without I/O (the composite's zero-addon parity is preserved) and
 * getDetails fails stable NOT_FOUND ("not routable"). The manifest was already
 * validated by the registry before storage; listSources re-checks nothing.
 */
import { catalogError } from '../catalog/errors';
import type {
  CatalogBook,
  CatalogProvider,
  CatalogSourceInfo,
  PagedResult,
} from '../catalog/CatalogProvider';
import { addonSource } from '../catalog/CatalogProvider';
import { resolveDownloadUrl } from '../catalog/mappers';
import type { AddonManifest } from './validateManifest';

const EMPTY_PAGE: PagedResult = { results: [], nextPage: null, totalCount: 0 };

export class AddonCatalogProvider implements CatalogProvider {
  private readonly source: CatalogSourceInfo;

  constructor(manifest: AddonManifest, addonId: string) {
    this.source = {
      sourceId: addonSource(addonId),
      name: manifest.name,
      kind: 'addon' as const,
    };
  }

  listSources(): CatalogSourceInfo[] {
    return [this.source];
  }

  /** Browse-only: contributes nothing to composite search, never I/O. */
  async search(_query: string, _page: number): Promise<PagedResult> {
    return EMPTY_PAGE;
  }

  /** Addon sources are not routable in this change; stable NOT_FOUND, no I/O. */
  async getDetails(id: string): Promise<CatalogBook> {
    throw catalogError('NOT_FOUND', `addon sources are not routable: ${id}`);
  }

  resolveDownloadUrl(formats: Record<string, string>, preferEpub: boolean): string {
    return resolveDownloadUrl(formats, preferEpub);
  }
}
