export {
  MAX_MANIFEST_BYTES,
  AddonFetchErrorCode,
  AddonFetchError,
  assertHttpsInstallUrl,
  validateManifest,
} from '@nextpage/manifest-validator';
export type {
  AddonManifest,
  AddonCatalogEntry,
  AddonFetchErrorCode as AddonFetchErrorCodeValue,
} from '@nextpage/manifest-validator';
export { addonIdFromUrl } from './addonId';
export { CuratedCatalogProvider } from './CuratedCatalogProvider';
