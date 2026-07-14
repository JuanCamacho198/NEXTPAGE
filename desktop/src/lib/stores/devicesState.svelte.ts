/**
 * Reactive devices state using Svelte 5 runes ($state) + Supabase Realtime.
 *
 * Uses the session-authenticated Supabase client so RLS policies apply.
 * Subscribes to Realtime INSERT/UPDATE/DELETE on the `devices` table
 * so the device list updates live across all sessions.
 *
 * @module devicesState
 */

import { getSessionClient } from '$lib/services/supabase';
import type { RealtimePostgresChangesPayload } from '@supabase/supabase-js';
import {
  getHardwareId,
  getDeviceInfo,
  listDevices,
  registerDevice,
  updateHeartbeat,
  removeDevice,
  rowToViewModel,
  type DeviceRow,
  type DeviceViewModel,
} from '$lib/services/devices';

export function createDevicesState(): {
  readonly devices: DeviceViewModel[];
  readonly error: string | null;
  readonly isLoading: boolean;
  readonly currentDeviceId: string | null;
  readonly deviceCount: number;
  loadDevices: (userId: string) => Promise<void>;
  remove: (deviceId: string, userId: string) => Promise<void>;
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

  let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  let realtimeUnsubscribe: (() => void) | null = null;
  let isSubscribed = $state(false);

  // ── Heartbeat ──────────────────────────────────────────────────

  function stopHeartbeat(): void {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  }

  function startHeartbeat(id: string): void {
    stopHeartbeat();
    currentDeviceId = id;
    heartbeatTimer = setInterval(async () => {
      try {
        await updateHeartbeat(getSessionClient(), id);
      } catch {
        /* silent */
      }
    }, 120_000);
  }

  // ── Realtime subscription ──────────────────────────────────────

  /**
   * Subscribe to Postgres changes on the `devices` table.
   * Any INSERT/UPDATE/DELETE from another device or session
   * will be reflected in real time.
   */
  function subscribeToDeviceChanges(userId: string): void {
    if (isSubscribed) return;

    const client = getSessionClient();
    const channel = client
      .channel('devices-realtime')
      .on<DeviceRow>(
        'postgres_changes',
        {
          event: '*',
          schema: 'public',
          table: 'devices',
          filter: `user_id=eq.${userId}`,
        },
        async (payload: RealtimePostgresChangesPayload<DeviceRow>) => {
          if (payload.eventType === 'INSERT') {
            const row = payload.new;
            if (row) {
              devices = [
                rowToViewModel(row, currentHardwareId),
                ...devices.filter((d) => d.id !== row.id),
              ];
            }
          } else if (payload.eventType === 'UPDATE') {
            const row = payload.new;
            if (row) {
              devices = devices.map((d) =>
                d.id === row.id ? rowToViewModel(row, currentHardwareId) : d,
              );
            }
          } else if (payload.eventType === 'DELETE') {
            const row = payload.old;
            if (row) {
              devices = devices.filter((d) => d.id !== row.id);
            }
          }
        },
      )
      .subscribe();

    isSubscribed = true;
    realtimeUnsubscribe = () => {
      channel.unsubscribe();
      isSubscribed = false;
    };
  }

  function unsubscribeFromDeviceChanges(): void {
    if (realtimeUnsubscribe) {
      realtimeUnsubscribe();
      realtimeUnsubscribe = null;
    }
    isSubscribed = false;
  }

  // ── Load / register current device ─────────────────────────────

  async function loadDevices(userId: string): Promise<void> {
    isLoading = true;
    error = null;
    try {
      const client = getSessionClient();
      const rows = await listDevices(client, userId);
      const existing = rows.find((r) => r.hardware_id === currentHardwareId);

      if (existing) {
        // Already registered — update heartbeat now
        currentDeviceId = existing.id;
        await updateHeartbeat(client, existing.id);
        devices = rows.map((r) => rowToViewModel(r, currentHardwareId));
        startHeartbeat(existing.id);
      } else {
        // Register new device
        const info = await getDeviceInfo();
        const registered = await registerDevice(client, userId, info);
        const updatedRows = await listDevices(client, userId);
        devices = updatedRows.map((r) => rowToViewModel(r, currentHardwareId));
        startHeartbeat(registered.id);
      }

      // Subscribe to Realtime for live updates
      subscribeToDeviceChanges(userId);
    } catch (e) {
      error = e instanceof Error ? e.message : 'Could not load devices';
      devices = [];
    } finally {
      isLoading = false;
    }
  }

  async function remove(deviceId: string, userId: string): Promise<void> {
    const client = getSessionClient();
    await removeDevice(client, deviceId, userId);
    devices = devices.filter((d) => d.id !== deviceId);
  }

  // ── Cleanup ────────────────────────────────────────────────────

  function destroy(): void {
    stopHeartbeat();
    unsubscribeFromDeviceChanges();
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
    stopHeartbeat,
    startHeartbeat,
    destroy,
  };
}
