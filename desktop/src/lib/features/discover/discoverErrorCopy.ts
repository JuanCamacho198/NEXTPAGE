/**
 * Catalog failure → copy key, shared by the inline rail error and the
 * screen-level search/scope error paths so the three-state split never drifts:
 * offline (connectivity), rate-limited (catalog throttling) and upstream.
 */
import type { CatalogErrorCode } from '$lib/shared/services/catalog';
import type { MessageKey } from '$lib/shared/i18n/messages.en';
import { isOfflineCatalogCode } from './DiscoverRailsDomainState.svelte';

/** Connectivity → offline, 429 → rate-limited, anything else → upstream. */
export function discoverErrorKey(code: CatalogErrorCode | null | undefined): MessageKey {
  if (code != null && isOfflineCatalogCode(code)) return 'discover.offline';
  if (code === 'RATE_LIMITED') return 'discover.rateLimited';
  return 'discover.errorUpstream';
}
