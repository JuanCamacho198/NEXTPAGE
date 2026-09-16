import {
  BUILTIN_GUTENDEX,
  catalogError,
  type CatalogFeaturedSort,
  type CatalogProvider,
  type PagedResult,
} from '$lib/shared/services/catalog';
import type { MessageKey } from '$lib/shared/i18n/messages.en';
import { thematicEntryFor } from './railRotation';

/**
 * The rail set is exactly three rails: two featured orderings plus one
 * term-based thematic rail. Every spec carries an ordering or a non-empty term,
 * so no rail can ever issue an unsorted catalog query.
 */
export type DiscoverRailSpec =
  | { kind: 'featured'; sort: CatalogFeaturedSort; titleKey: MessageKey }
  | { kind: 'thematic'; term: string; titleKey: MessageKey };

export const DISCOVER_RAIL_COUNT = 3;
/** Render truncation for rail cards (not a catalog page size). */
export const DISCOVER_RAIL_LIMIT = 6;
/** "Ver todo" featured scope: one first page of the featured ordering. */
export const RAIL_SCOPE_LIMIT = 24;

/**
 * Index-stable rail plan: [NEWEST featured, POPULAR featured, thematic(day)].
 * Rebuilt per refresh, so crossing local midnight rolls the theme forward.
 */
export function buildRailSpecs(now: Date = new Date()): readonly DiscoverRailSpec[] {
  const thematic = thematicEntryFor(now);
  return [
    { kind: 'featured', sort: 'NEWEST', titleKey: 'discover.rail.newest' },
    { kind: 'featured', sort: 'POPULAR', titleKey: 'discover.rail.popular' },
    { kind: 'thematic', term: thematic.term, titleKey: thematic.titleKey },
  ];
}

/** Resolve one rail: an explicit featured ordering or a non-empty term search. */
export function loadRail(
  provider: CatalogProvider,
  spec: DiscoverRailSpec,
  limit: number = DISCOVER_RAIL_LIMIT,
): Promise<PagedResult> {
  if (spec.kind === 'featured') return provider.featured(spec.sort, limit);
  return provider.searchSource(BUILTIN_GUTENDEX, spec.term, 1);
}

/**
 * Bound a rail attempt by `ms` so a hung request settles instead of staying
 * `Loading` forever. Deliberately a manual timer (not `AbortSignal.timeout`) so
 * fake timers can drive it under jsdom; expiry maps to the shared NETWORK_ERROR.
 */
export async function withDeadline<T>(work: Promise<T>, ms: number): Promise<T> {
  let handle: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      work,
      new Promise<never>((_, reject) => {
        handle = setTimeout(
          () => reject(catalogError('NETWORK_ERROR', `rail deadline of ${ms}ms elapsed`)),
          ms,
        );
      }),
    ]);
  } finally {
    if (handle !== undefined) clearTimeout(handle);
  }
}
