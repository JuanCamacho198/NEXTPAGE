/**
 * useAddonConsentState — consent snapshot + toggle handlers for the Addons
 * surfaces (slice 8, Domain C).
 *
 * The hook owns no persistence: every read/write goes through the injected
 * consent service (default: the production `addonConsent` singleton over the
 * durable Tauri store). `refresh(ids)` hydrates the snapshot once and then
 * re-reads it for the visible rows; `handleConsentToggle` grants/withdraws
 * durably and mirrors the outcome into the snapshot so the toggle reflects
 * the persisted state on the next render.
 */
import { addonConsent, AddonConsentService } from '$lib/shared/services/addons/AddonConsent';

export type AddonConsentDeps = {
  consent?: AddonConsentService;
};

export function createAddonConsentState(deps: AddonConsentDeps = {}): {
  consentById: Record<string, boolean>;
  refresh: (ids: readonly string[]) => Promise<void>;
  handleConsentToggle: (id: string, granted: boolean) => Promise<void>;
} {
  const consent = deps.consent ?? addonConsent;

  let consentById = $state<Record<string, boolean>>({});

  async function refresh(ids: readonly string[]): Promise<void> {
    await consent.ensureLoaded();
    const next: Record<string, boolean> = {};
    for (const id of ids) next[id] = consent.hasConsent(id);
    consentById = next;
  }

  async function handleConsentToggle(id: string, granted: boolean): Promise<void> {
    if (granted) await consent.grant(id);
    else await consent.revoke(id);
    consentById = { ...consentById, [id]: granted };
  }

  return {
    get consentById() {
      return consentById;
    },
    refresh,
    handleConsentToggle,
  };
}
