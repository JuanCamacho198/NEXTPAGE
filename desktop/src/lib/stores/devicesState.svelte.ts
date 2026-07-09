import { getSupabaseClient } from '$lib/services/supabase'
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
} from '$lib/services/devices'

export function createDevicesState() {
  let devices = $state<DeviceViewModel[]>([])
  let error = $state<string | null>(null)
  let isLoading = $state(false)
  let currentDeviceId = $state<string | null>(null)
  let currentHardwareId = $state(getHardwareId())
  let deviceCount = $derived(devices.length)

  let heartbeatTimer: ReturnType<typeof setInterval> | null = null

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function startHeartbeat(id: string) {
    stopHeartbeat()
    currentDeviceId = id
    heartbeatTimer = setInterval(async () => {
      try {
        await updateHeartbeat(getSupabaseClient(), id)
      } catch {
        /* silent */
      }
    }, 120_000)
  }

  async function loadDevices(email: string) {
    isLoading = true
    error = null
    try {
      const client = getSupabaseClient()
      const rows = await listDevices(client, email)
      const existing = rows.find((r) => r.hardware_id === currentHardwareId)

      if (existing) {
        // Already registered — update heartbeat now
        currentDeviceId = existing.id
        await updateHeartbeat(client, existing.id)
        devices = rows.map((r) => rowToViewModel(r, currentHardwareId))
        startHeartbeat(existing.id)
      } else {
        // Register new device
        const info = await getDeviceInfo()
        const registered = await registerDevice(client, email, info)
        const updatedRows = await listDevices(client, email)
        devices = updatedRows.map((r) => rowToViewModel(r, currentHardwareId))
        startHeartbeat(registered.id)
      }
    } catch (e) {
      error = e instanceof Error ? e.message : 'Could not load devices'
      devices = []
    } finally {
      isLoading = false
    }
  }

  async function remove(deviceId: string, userEmail: string) {
    const client = getSupabaseClient()
    await removeDevice(client, deviceId, userEmail)
    devices = devices.filter((d) => d.id !== deviceId)
  }

  return {
    get devices() {
      return devices
    },
    get error() {
      return error
    },
    get isLoading() {
      return isLoading
    },
    get currentDeviceId() {
      return currentDeviceId
    },
    get deviceCount() {
      return deviceCount
    },
    loadDevices,
    remove,
    stopHeartbeat,
    startHeartbeat,
  }
}
