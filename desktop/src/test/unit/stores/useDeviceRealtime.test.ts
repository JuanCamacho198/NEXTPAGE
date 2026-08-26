import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockChannelOn = vi.fn();
const mockChannelSubscribe = vi.fn();
const mockChannelUnsubscribe = vi.fn();
const mockRemoveChannel = vi.fn();
let capturedHandler: ((payload: { eventType: string; new: unknown; old: unknown }) => void) | null = null;

const mockClient = {
  getChannel: vi.fn(),
  getChannels: vi.fn(),
  channel: vi.fn(),
  removeChannel: mockRemoveChannel,
};

function makeFakeChannel() {
  return {
    on: (event: string, _filter: unknown, handler: unknown) => {
      mockChannelOn(event, _filter, handler);
      capturedHandler = handler as never;
      return { subscribe: mockChannelSubscribe } as never;
    },
    subscribe: mockChannelSubscribe,
    unsubscribe: mockChannelUnsubscribe,
    state: 'subscribed',
    topic: '',
  };
}

vi.mock('$lib/services/supabase', () => ({
  getSessionClient: () => mockClient,
}));

// Mock rowToViewModel to be deterministic
vi.mock('$lib/services/devices', async () => {
  const actual = await vi.importActual<typeof import('$lib/services/devices')>('$lib/services/devices');
  return {
    ...actual,
    rowToViewModel: (row: { id: string; name: string; os: string; hardware_id: string; type: string; last_active: string }, hid: string) => ({
      id: row.id,
      name: row.name,
      os: row.os,
      icon: 'laptop' as const,
      lastActive: { value: 1, unit: 'day' as const },
      isCurrent: row.hardware_id === hid,
    }),
  };
});

describe('useDeviceRealtime — 4 casos INSERT/UPDATE/DELETE + global checks', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedHandler = null;
    mockClient.getChannel.mockReturnValue(undefined);
    mockClient.getChannels.mockReturnValue([]);
    const fake = makeFakeChannel();
    mockClient.channel.mockReturnValue(fake as never);
    mockClient.getChannel.mockReturnValue(undefined);
  });

  it('INSERT adds device via rowToViewModel (dedup)', async () => {
    const { createDeviceRealtime } = await import('$lib/shared/stores/useDeviceRealtime.svelte');
    let devices: { id: string; name: string }[] = [{ id: 'a', name: 'old' } as never];
    const rt = createDeviceRealtime({
      getHardwareId: () => 'hid-1',
      getDevices: () => devices as never,
      setDevices: (v) => { devices = v as never; },
    });
    rt.subscribe('user-1');
    expect(capturedHandler).not.toBeNull();
    capturedHandler!({ eventType: 'INSERT', new: { id: 'b', name: 'new', os: 'Win', hardware_id: 'hid-2', type: 'desktop', last_active: new Date().toISOString() }, old: {} });
    expect(devices.map((d) => d.id)).toContain('b');
    expect(devices[0].id).toBe('b');
    rt.destroy();
  });

  it('UPDATE maps existing device', async () => {
    const { createDeviceRealtime } = await import('$lib/shared/stores/useDeviceRealtime.svelte');
    let devices: { id: string; name: string }[] = [{ id: 'a', name: 'old' } as never];
    const rt = createDeviceRealtime({
      getHardwareId: () => 'hid-1',
      getDevices: () => devices as never,
      setDevices: (v) => { devices = v as never; },
    });
    rt.subscribe('user-1');
    capturedHandler!({ eventType: 'UPDATE', new: { id: 'a', name: 'updated', os: 'Win', hardware_id: 'hid-1', type: 'desktop', last_active: new Date().toISOString() }, old: {} });
    expect(devices.find((d) => d.id === 'a')?.name).toBe('updated');
    rt.destroy();
  });

  it('DELETE removes device', async () => {
    const { createDeviceRealtime } = await import('$lib/shared/stores/useDeviceRealtime.svelte');
    let devices: { id: string }[] = [{ id: 'a' } as never, { id: 'b' } as never];
    const rt = createDeviceRealtime({
      getHardwareId: () => 'hid-1',
      getDevices: () => devices as never,
      setDevices: (v) => { devices = v as never; },
    });
    rt.subscribe('user-1');
    capturedHandler!({ eventType: 'DELETE', new: {}, old: { id: 'a' } });
    expect(devices.map((d) => d.id)).toEqual(['b']);
    rt.destroy();
  });

  it('dual check isSubscribed/state subscribed prevents duplicate subscribe', async () => {
    const { createDeviceRealtime } = await import('$lib/shared/stores/useDeviceRealtime.svelte');
    let devices: unknown[] = [];
    const rt = createDeviceRealtime({
      getHardwareId: () => 'hid-1',
      getDevices: () => devices as never,
      setDevices: (v) => { devices = v as never; },
    });
    rt.subscribe('user-1');
    const firstCalls = mockClient.channel.mock.calls.length;
    rt.subscribe('user-1');
    expect(mockClient.channel.mock.calls.length).toBe(firstCalls);
    expect(rt.isSubscribed).toBe(true);
    rt.destroy();
  });

  it('global getChannel/getChannels adoption — existing subscribed channel is adopted', async () => {
    const existing = makeFakeChannel();
    (existing as unknown as { state: string }).state = 'subscribed';
    mockClient.getChannel.mockReturnValue(existing as never);
    const { createDeviceRealtime } = await import('$lib/shared/stores/useDeviceRealtime.svelte');
    let devices: unknown[] = [];
    const rt = createDeviceRealtime({
      getHardwareId: () => 'hid-1',
      getDevices: () => devices as never,
      setDevices: (v) => { devices = v as never; },
    });
    rt.subscribe('user-1');
    expect(mockClient.channel).not.toHaveBeenCalled();
    expect(rt.channel).toBe(existing as never);
    expect(rt.isSubscribed).toBe(true);
    rt.destroy();
  });
});
