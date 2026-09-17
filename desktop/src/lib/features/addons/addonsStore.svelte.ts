/**
 * addonsStore — the single app-wide addon-management state (slice 7).
 *
 * `createSettingsAddons` remains the only mutation logic; this module holds
 * exactly ONE instance for the whole app, built over the shared registry
 * (`getAddonRegistry()`, not a new instance) so screen mutations drive the
 * existing `liveComposite` invalidation and both surfaces (Settings panel,
 * Addons screen) observe each other's mutations without a refetch race.
 *
 * The instance is created at module load, while the translator/toast host
 * belong to whichever surface is mounted — so `t`/`pushToast` are passed as
 * getters over the bound notifier (the factory resolves them per call) and
 * each surface calls `bindAddonsNotifier({ t, pushToast? })` on mount.
 */
import {
  createSettingsAddons,
  type AddonsDeps,
} from '$lib/features/settings/useSettingsAddons.svelte';
import { getAddonRegistry } from '$lib/shared/services/addons/AddonRegistry';

let boundNotifier: { t?: AddonsDeps['t']; pushToast?: AddonsDeps['pushToast'] } = {};

/** Bind the mounted surface's translator/toast host (call in an `$effect`). */
export function bindAddonsNotifier(next: typeof boundNotifier): void {
  boundNotifier = next;
}

/** The single shared addon-management state for Settings + Addons screen. */
export const addonsState = createSettingsAddons({
  registry: getAddonRegistry(),
  get t() {
    return boundNotifier.t;
  },
  get pushToast() {
    return boundNotifier.pushToast;
  },
});
