import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const mockUpdateHeartbeat = vi.fn();
const mockGetSessionClient = vi.fn();

vi.mock('$lib/services/devices', async () => {
  const actual = await vi.importActual<typeof import('$lib/services/devices')>('$lib/services/devices');
  return { ...actual, updateHeartbeat: (...args: unknown[]) => mockUpdateHeartbeat(...args) };
});
vi.mock('$lib/services/supabase', () => ({
  getSessionClient: () => mockGetSessionClient(),
}));

describe('useDeviceHeartbeat — 120s interval stop/start', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockUpdateHeartbeat.mockResolvedValue(undefined);
    mockGetSessionClient.mockReturnValue({} as never);
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('start begins 120s interval and calls updateHeartbeat', async () => {
    const { createDeviceHeartbeat } = await import('$lib/shared/stores/useDeviceHeartbeat.svelte');
    const hb = createDeviceHeartbeat();
    hb.start('dev-1');
    expect(mockUpdateHeartbeat).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(120_000);
    expect(mockUpdateHeartbeat).toHaveBeenCalledWith(expect.anything(), 'dev-1');
    expect(mockUpdateHeartbeat).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(120_000);
    expect(mockUpdateHeartbeat).toHaveBeenCalledTimes(2);
    hb.destroy();
  });

  it('stop clears interval', async () => {
    const { createDeviceHeartbeat } = await import('$lib/shared/stores/useDeviceHeartbeat.svelte');
    const hb = createDeviceHeartbeat();
    hb.start('dev-1');
    hb.stop();
    await vi.advanceTimersByTimeAsync(240_000);
    expect(mockUpdateHeartbeat).not.toHaveBeenCalled();
    hb.destroy();
  });

  it('start restarts interval for new id (stop/start)', async () => {
    const { createDeviceHeartbeat } = await import('$lib/shared/stores/useDeviceHeartbeat.svelte');
    const hb = createDeviceHeartbeat();
    hb.start('dev-1');
    await vi.advanceTimersByTimeAsync(60_000);
    hb.start('dev-2');
    await vi.advanceTimersByTimeAsync(120_000);
    expect(mockUpdateHeartbeat).toHaveBeenCalledWith(expect.anything(), 'dev-2');
    expect(mockUpdateHeartbeat).not.toHaveBeenCalledWith(expect.anything(), 'dev-1');
    hb.destroy();
  });

  it('destroy clears timer and is idempotent', async () => {
    const { createDeviceHeartbeat } = await import('$lib/shared/stores/useDeviceHeartbeat.svelte');
    const hb = createDeviceHeartbeat();
    hb.start('dev-1');
    hb.destroy();
    hb.destroy();
    await vi.advanceTimersByTimeAsync(360_000);
    expect(mockUpdateHeartbeat).not.toHaveBeenCalled();
  });
});
