/**
 * LocatorCodec — services layer re-export for page derivation.
 *
 * Canonical path is `sync/LocatorCodec`. This file exists so that
 * `services/*` callers can `import { fromCfi } from './LocatorCodec'`
 * without crossing into `sync` directly, and so the spec's
 * `desktop/src/lib/shared/services/LocatorCodec.ts` file exists.
 *
 * Contract: `page = fromCfi(cfiRange ?? cfiLocation) ?? 1`
 * EPUB page is always derived; `current_page` is deprecated.
 */

export { fromCfi, derivePage, parseSpineIndex } from '../sync/LocatorCodec';
export type { CanonicalLocator, LocatorLocations, LocatorChapterMetric } from '../sync/LocatorCodec';
