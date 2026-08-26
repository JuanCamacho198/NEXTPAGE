/**
 * DevicesState facade — thin orchestration delegating heartbeat + realtime
 * to composables. Keeps load/register/remove/rename coordination.
 */
import { getSessionClient } from '$lib/services/supabase';
import {
  getHardwareId,
  getDeviceInfo,
  listDevices,
  registerDevice,
  updateHeartbeat,
  removeDevice,
  renameDevice,
  rowToViewModel,
  type DeviceViewModel,
} from '$lib/services/devices';
import { createDeviceHeartbeat } from './useDeviceHeartbeat.svelte';
import { createDeviceRealtime } from './useDeviceRealtime.svelte';
import { isRowStale, isViewModelStale } from './deviceStaleGuard';

export function createDevicesState(): {
  readonly devices: DeviceViewModel[];
  readonly error: string | null;
  readonly isLoading: boolean;
  readonly currentDeviceId: string | null;
  readonly deviceCount: number;
  loadDevices: (userId: string) => Promise<void>;
  remove: (deviceId: string, userId: string) => Promise<void>;
  rename: (deviceId: string, name: string) => Promise<void>;
  removeStale: (deviceId: string, userId: string) => Promise<void>;
  stopHeartbeat: () => void;
  startHeartbeat: (id: string) => void;
  destroy: () => void;
} {
  let devices = $state<DeviceViewModel[]>([]);
  let error = $state<string | null>(null);
  let isLoading = $state(false);
  let currentDeviceId = $state<string | null>(null);
  let currentHardwareId = $state(getHardwareId());
  let deviceCount = $derived(devices.length);

  const heartbeat = createDeviceHeartbeat();
  const realtime = createDeviceRealtime({
    getHardwareId: () => currentHardwareId,
    getDevices: () => devices,
    setDevices: (v) => {
      devices = v;
    },
  });

  function stopHeartbeat(): void {
    heartbeat.stop();
  }

  function startHeartbeat(id: string): void {
    currentDeviceId = id;
    heartbeat.start(id);
  }

  async function loadDevices(userId: string): Promise<void> {
    isLoading = true;
    error = null;
    try {
      const client = getSessionClient();
      const rows = await listDevices(client, userId);
      const existing = rows.find((r) => r.hardware_id === currentHardwareId);
      if (existing) {
        currentDeviceId = existing.id;
        await updateHeartbeat(client, existing.id);
        devices = rows.map((r) => rowToViewModel(r, currentHardwareId));
        startHeartbeat(existing.id);
      } else {
        const info = await getDeviceInfo();
        const registered = await registerDevice(client, userId, info);
        const updatedRows = await listDevices(client, userId);
        devices = updatedRows.map((r) => rowToViewModel(r, currentHardwareId));
        startHeartbeat(registered.id);
      }
      realtime.subscribe(userId);
    } catch (e) {
      error = e instanceof Error ? e.message : 'Could not load devices';
      devices = [];
    } finally {
      isLoading = false;
    }
  }

  async function remove(deviceId: string, userId: string): Promise<void> {
    await removeDevice(getSessionClient(), deviceId, userId);
    devices = devices.filter((d) => d.id !== deviceId);
  }

  async function rename(deviceId: string, name: string): Promise<void> {
    await renameDevice(getSessionClient(), deviceId, name);
    devices = devices.map((d) => (d.id === deviceId ? { ...d, name: name.trim() } : d));
  }

  async function removeStale(deviceId: string, userId: string): Promise<void> {
    const target = devices.find((d) => d.id === deviceId);
    if (target && !isViewModelStale(target)) throw new Error('device.not_stale');
    try {
      const rawRows = await listDevices(getSessionClient(), userId);
      const raw = rawRows.find((r) => r.id === deviceId);
      if (raw && !isRowStale(raw)) throw new Error('device.not_stale');
    } catch (e) {
      if (e instanceof Error && e.message === 'device.not_stale') throw e;
    }
    await removeDevice(getSessionClient(), deviceId, userId);
    devices = devices.filter((d) => d.id !== deviceId);
  }

  function destroy(): void {
    heartbeat.destroy();
    realtime.destroy();
  }

  return {
    get devices() {
      return devices;
    },
    get error() {
      return error;
    },
    get isLoading() {
      return isLoading;
    },
    get currentDeviceId() {
      return currentDeviceId;
    },
    get deviceCount() {
      return deviceCount;
    },
    loadDevices,
    remove,
    rename,
    removeStale,
    stopHeartbeat,
    startHeartbeat,
    destroy,
  };
}
