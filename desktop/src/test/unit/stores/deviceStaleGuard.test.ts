import { describe, it, expect } from 'vitest';
import { STALE_DAYS, isStaleIso, isRowStale, isViewModelStale } from '$lib/shared/stores/deviceStaleGuard';
import type { DeviceRow, DeviceViewModel } from '$lib/services/devices';

describe('deviceStaleGuard — 30d pure', () => {
  it('STALE_DAYS is 30', () => {
    expect(STALE_DAYS).toBe(30);
  });

  it('isStaleIso true when >30d ago', () => {
    const old = new Date(Date.now() - 31 * 24 * 60 * 60 * 1000).toISOString();
    expect(isStaleIso(old)).toBe(true);
  });

  it('isStaleIso false when <30d ago', () => {
    const recent = new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString();
    expect(isStaleIso(recent)).toBe(false);
  });

  it('isRowStale delegates to isDeviceStale with 30d', () => {
    const row = { last_active: new Date(Date.now() - 40 * 24 * 60 * 60 * 1000).toISOString() } as DeviceRow;
    expect(isRowStale(row)).toBe(true);
    const fresh = { last_active: new Date().toISOString() } as DeviceRow;
    expect(isRowStale(fresh)).toBe(false);
  });

  it('isViewModelStale true when day >=30', () => {
    const vm = { lastActive: { value: 30, unit: 'day' } } as DeviceViewModel;
    expect(isViewModelStale(vm)).toBe(true);
    const vm31 = { lastActive: { value: 31, unit: 'day' } } as DeviceViewModel;
    expect(isViewModelStale(vm31)).toBe(true);
  });

  it('isViewModelStale false when unit not day or value <30', () => {
    const vmHour = { lastActive: { value: 23, unit: 'hour' } } as DeviceViewModel;
    expect(isViewModelStale(vmHour)).toBe(false);
    const vmDay10 = { lastActive: { value: 10, unit: 'day' } } as DeviceViewModel;
    expect(isViewModelStale(vmDay10)).toBe(false);
    const vmNow = { lastActive: { value: 0, unit: 'now' } } as DeviceViewModel;
    expect(isViewModelStale(vmNow)).toBe(false);
  });
});
