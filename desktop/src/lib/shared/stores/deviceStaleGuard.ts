import { isDeviceStale } from '$lib/services/devices';
import type { DeviceRow, DeviceViewModel } from '$lib/services/devices';

export const STALE_DAYS = 30;
export const STALE_MS = STALE_DAYS * 24 * 60 * 60 * 1000;

/** Pure 30d stale check for a raw ISO timestamp. */
export function isStaleIso(iso: string): boolean {
  return isDeviceStale(iso, STALE_DAYS);
}

/** Pure check for a DeviceRow. */
export function isRowStale(row: DeviceRow): boolean {
  return isDeviceStale(row.last_active, STALE_DAYS);
}

/** Pure check for a view-model's relative time. */
export function isViewModelStale(vm: DeviceViewModel): boolean {
  return vm.lastActive.unit === 'day' && vm.lastActive.value >= STALE_DAYS;
}

/** Guard used by removeStale — returns true when the vm is NOT stale and removal must be blocked. */
export function shouldBlockStaleRemoval(vm: DeviceViewModel | undefined): boolean {
  if (!vm) return false;
  return !isViewModelStale(vm);
}
