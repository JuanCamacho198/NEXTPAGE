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
import type { RealtimeChannel, RealtimePostgresChangesPayload } from '@supabase/supabase-js';
import {
  getHardwareId,
  getDeviceInfo,
  listDevices,
  registerDevice,
  updateHeartbeat,
  removeDevice,
  renameDevice,
  isDeviceStale,
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

  let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  let realtimeUnsubscribe: (() => void) | null = null;
  let isSubscribed = $state(false);
  let currentChannel: RealtimeChannel | null = null;
  let currentUserId: string | null = null;

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

  // ── Realtime helpers ───────────────────────────────────────────

  function teardownCurrentChannel(): void {
    if (currentChannel) {
      try {
        currentChannel.unsubscribe();
      } catch {
        /* ignore */
      }
      try {
        getSessionClient().removeChannel(currentChannel);
      } catch {
        /* ignore */
      }
      currentChannel = null;
    }
    currentUserId = null;
    isSubscribed = false;
    realtimeUnsubscribe = null;
  }

  /**
   * Subscribe to Postgres changes on the `devices` table.
   * Any INSERT/UPDATE/DELETE from another device or session
   * will be reflected in real time.
   *
   * Uses a per-user channel name (`devices:<userId>`) so two
   * `createDevicesState()` instances for the same user do not clash,
   * and a user switch cleans up the previous channel. Before creating
   * a channel, checks whether the Supabase client already holds a
   * subscribed channel with the same name to avoid the
   * "cannot add postgres_changes callbacks after subscribe()" error.
   */
  function subscribeToDeviceChanges(userId: string): void {
    const client = getSessionClient();
    const channelName = `devices:${userId}`;

    // Already subscribed for this user (instance-scoped check)
    if (
      currentUserId === userId &&
      currentChannel != null &&
      (isSubscribed || (currentChannel as unknown as { state?: string }).state === 'subscribed')
    ) {
      return;
    }

    // User switch: clean up previous channel before subscribing to the new user
    if (currentUserId !== null && currentUserId !== userId) {
      teardownCurrentChannel();
    }

    // Global check: does the Supabase client already have this channel?
    // supabase-js caches channels by name; adding `on()` after `subscribe()` throws.
    const anyClient = client as unknown as {
      getChannels?: () => RealtimeChannel[];
      getChannel?: (name: string) => RealtimeChannel | undefined;
    };
    let existing: RealtimeChannel | null = null;
    if (typeof anyClient.getChannel === 'function') {
      try {
        existing = anyClient.getChannel(channelName) ?? null;
      } catch {
        existing = null;
      }
    }
    if (!existing && typeof anyClient.getChannels === 'function') {
      try {
        const all = anyClient.getChannels();
        existing =
          all.find((c) => {
            const topic = (c as unknown as { topic?: string; channelName?: string }).topic ??
              (c as unknown as { channelName?: string }).channelName ??
              '';
            return topic.includes(channelName);
          }) ?? null;
      } catch {
        existing = null;
      }
    }
    if (existing) {
      const existingState = (existing as unknown as { state?: string }).state;
      if (existingState === 'subscribed' || existingState === 'joined') {
        // Adopt the existing subscribed channel to allow later teardown
        currentChannel = existing;
        currentUserId = userId;
        isSubscribed = true;
        realtimeUnsubscribe = () => {
          try {
            existing!.unsubscribe();
          } catch {
            /* ignore */
          }
          try {
            client.removeChannel(existing!);
          } catch {
            /* ignore */
          }
          if (currentChannel === existing) {
            currentChannel = null;
            currentUserId = null;
          }
          isSubscribed = false;
          realtimeUnsubscribe = null;
        };
        return;
      }
      // Stale channel (unsubscribed/errored) — remove before re-creating
      try {
        client.removeChannel(existing);
      } catch {
        /* ignore */
      }
    }

    // Clean up any stale instance channel that is no longer subscribed
    if (currentChannel) {
      teardownCurrentChannel();
    }

    const channel = client.channel(channelName);

    channel.on<DeviceRow>(
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
    );

    channel.subscribe();
    currentChannel = channel;
    currentUserId = userId;
    isSubscribed = true;
    realtimeUnsubscribe = () => {
      try {
        channel.unsubscribe();
      } catch {
        /* ignore */
      }
      try {
        client.removeChannel(channel);
      } catch {
        /* ignore */
      }
      if (currentChannel === channel) {
        currentChannel = null;
        currentUserId = null;
      }
      isSubscribed = false;
      realtimeUnsubscribe = null;
    };
  }

  function unsubscribeFromDeviceChanges(): void {
    if (realtimeUnsubscribe) {
      const fn = realtimeUnsubscribe;
      realtimeUnsubscribe = null;
      try {
        fn();
      } catch {
        /* ignore */
      }
    } else if (currentChannel) {
      teardownCurrentChannel();
    } else {
      isSubscribed = false;
    }
  }

  // ── Load / register current device ─────────────────────────────

  async function loadDevices(userId: string): Promise<void> {
    // If user switched, tear down previous subscription before loading
    if (currentUserId !== null && currentUserId !== userId) {
      teardownCurrentChannel();
    }
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

  async function rename(deviceId: string, name: string): Promise<void> {
    const client = getSessionClient();
    await renameDevice(client, deviceId, name);
    devices = devices.map((d) => (d.id === deviceId ? { ...d, name: name.trim() } : d));
  }

  async function removeStale(deviceId: string, userId: string): Promise<void> {
    // Enforce stale check >30d client-side before hitting Supabase
    const target = devices.find((d) => d.id === deviceId);
    // Need raw row last_active — fetch via listDevices cache if viewModel doesn't carry iso
    // Use isDeviceStale with viewModel's lastActive days approximation
    // If not stale (<30d), throw typed error for UI
    if (target) {
      const stale = target.lastActive.unit === 'day' && target.lastActive.value >= 30;
      if (!stale) throw new Error('device.not_stale');
    }
    // Verify via raw rows for precise check
    try {
      const rawRows = await listDevices(getSessionClient(), userId);
      const raw = rawRows.find((r) => r.id === deviceId);
      if (raw && !isDeviceStale(raw.last_active, 30)) throw new Error('device.not_stale');
    } catch (e) {
      if (e instanceof Error && e.message === 'device.not_stale') throw e;
      // ignore fetch errors, proceed to remove
    }
    const client = getSessionClient();
    await removeDevice(client, deviceId, userId);
    devices = devices.filter((d) => d.id !== deviceId);
  }

  // ── Cleanup ────────────────────────────────────────────────────

  function destroy(): void {
    stopHeartbeat();
    unsubscribeFromDeviceChanges();
    // Defensive: ensure no orphaned channel remains even if unsubscribe path was missed
    if (currentChannel) teardownCurrentChannel();
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
