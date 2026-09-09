export {
  MAX_MANIFEST_BYTES,
  AddonFetchErrorCode,
  AddonFetchError,
  assertHttpsInstallUrl,
  validateManifest,
} from "./validateManifest";
export type { AddonManifest, AddonCatalogEntry, AddonFetchErrorCode as AddonFetchErrorCodeValue } from "./validateManifest";
export { addonIdFromUrl } from "./addonId";
