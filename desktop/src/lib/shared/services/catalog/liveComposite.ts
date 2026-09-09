/**
 * LiveCatalogProvider — desktop composition seam for Discover: the composite
 * rebuilds from installed addon rows whenever the registry mutates, so
 * install/enable/disable/uninstall change the active source set immediately,
 * and rows are re-read after restart (design A1/A4 wiring; no Discover UI
 * changes needed — DiscoverDomainState just consumes this provider).
 */
import {
  createRebuildingCatalogProvider,
  type CatalogProviderSupplier,
} from './CompositeCatalogProvider';
import type {
  CatalogBook,
  CatalogProvider,
  CatalogSourceInfo,
  PagedResult,
} from './CatalogProvider';
import { resolveDownloadUrl } from './mappers';
import { AddonRegistry } from '../addons/AddonRegistry';

const registry = new AddonRegistry();
const supplier: CatalogProviderSupplier = createRebuildingCatalogProvider(() =>
  registry.listInstalled(),
);
registry.onChanged(() => supplier.invalidate());

class LiveCatalogProvider implements CatalogProvider {
  constructor(private readonly supplier: CatalogProviderSupplier) {}

  search(query: string, page: number): Promise<PagedResult> {
    return supplier.current().then((c) => c.search(query, page));
  }

  getDetails(id: string): Promise<CatalogBook> {
    return supplier.current().then((c) => c.getDetails(id));
  }

  resolveDownloadUrl(formats: Record<string, string>, preferEpub: boolean): string {
    return resolveDownloadUrl(formats, preferEpub);
  }

  listSources(): CatalogSourceInfo[] {
    return supplier.peek()?.listSources() ?? [];
  }
}

export const liveCatalogProvider: CatalogProvider = new LiveCatalogProvider(supplier);
