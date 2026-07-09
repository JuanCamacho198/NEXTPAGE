import { getSupabaseClient } from './supabase'
import type { SupabaseClient } from '@supabase/supabase-js'

// --- Types ---
export interface DeviceRow {
  id: string
  user_id: string
  hardware_id: string
  name: string
  os: string
  type: 'desktop' | 'mobile' | 'tablet' | 'web'
  last_active: string
  created_at: string
}

export interface DeviceViewModel {
  id: string
  name: string
  os: string
  icon: 'windows' | 'apple' | 'android' | 'linux'
  lastActive: { value: number; unit: 'now' | 'min' | 'hour' | 'day' }
  isCurrent: boolean
}

export interface DeviceInfo {
  hardwareId: string
  name: string
  os: string
  type: 'desktop'
}

// --- Helpers ---
export function getHardwareId(): string {
  const key = 'nextpage-hardware-id'
  let id = localStorage.getItem(key)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(key, id)
  }
  return id
}

export async function getDeviceInfo(): Promise<DeviceInfo> {
  let deviceName = 'Desktop'
  let deviceOs = 'Unknown'

  try {
    // Tauri v2 plugin-os — available inside Tauri runtime
    const { hostname, type, version } = await import('@tauri-apps/plugin-os')
    const [host, osType, osVer] = await Promise.all([hostname(), type(), version()])
    deviceName = host ? `${host} PC` : 'Desktop'
    deviceOs = `${osType} ${osVer}`
      .replace('Windows_NT', 'Windows')
      .replace('Darwin', 'macOS')
  } catch {
    // Fallback for dev in browser (HMR)
    deviceName = 'Desktop'
    deviceOs = navigator.platform
  }

  return {
    hardwareId: getHardwareId(),
    name: deviceName,
    os: deviceOs,
    type: 'desktop',
  }
}

export function formatRelativeTime(dateStr: string): { value: number; unit: 'now' | 'min' | 'hour' | 'day' } {
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return { value: 0, unit: 'now' }
  if (mins < 60) return { value: mins, unit: 'min' }
  const hours = Math.floor(mins / 60)
  if (hours < 24) return { value: hours, unit: 'hour' }
  const days = Math.floor(hours / 24)
  return { value: days, unit: 'day' }
}

export function rowToViewModel(
  row: DeviceRow,
  currentHardwareId: string,
): DeviceViewModel {
  const icon = row.os.toLowerCase().includes('windows')
    ? 'windows'
    : row.os.toLowerCase().includes('mac') || row.os.includes('Darwin')
      ? 'apple'
      : row.os.toLowerCase().includes('android')
        ? 'android'
        : 'linux'

  return {
    id: row.id,
    name: row.name,
    os: row.os,
    icon,
    lastActive: formatRelativeTime(row.last_active),
    isCurrent: row.hardware_id === currentHardwareId,
  }
}

// --- CRUD ---
export async function registerDevice(
  client: SupabaseClient,
  userId: string,
  info: DeviceInfo,
): Promise<DeviceRow> {
  const { data, error } = await client
    .from('devices')
    .upsert(
      {
        user_id: userId,
        hardware_id: info.hardwareId,
        name: info.name,
        os: info.os,
        type: info.type,
        last_active: new Date().toISOString(),
      },
      {
        onConflict: 'user_id,hardware_id',
        ignoreDuplicates: false,
      },
    )
    .select()
    .single()

  if (error) throw error
  return data
}

export async function listDevices(
  client: SupabaseClient,
  userId: string,
): Promise<DeviceRow[]> {
  const { data, error } = await client
    .from('devices')
    .select('*')
    .eq('user_id', userId)
    .order('last_active', { ascending: false })

  if (error) throw error
  return data ?? []
}

export async function updateHeartbeat(
  client: SupabaseClient,
  deviceId: string,
): Promise<void> {
  const { error } = await client
    .from('devices')
    .update({ last_active: new Date().toISOString() })
    .eq('id', deviceId)

  if (error) console.warn('Heartbeat failed:', error.message)
}

export async function removeDevice(
  client: SupabaseClient,
  deviceId: string,
  userId: string,
): Promise<void> {
  const { error } = await client
    .from('devices')
    .delete()
    .eq('id', deviceId)
    .eq('user_id', userId)

  if (error) throw error
}
