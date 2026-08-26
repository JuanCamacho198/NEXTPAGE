import { describe, it, expect, vi } from 'vitest';
import { createSettingsProfile } from '$lib/features/settings/useSettingsProfile.svelte';

describe('useSettingsProfile', () => {
  it('defaults and dailyGoalCards length 4', () => {
    const p = createSettingsProfile({ t: (k) => k as never });
    expect(p.selectedDailyGoal).toBe(20);
    expect(p.dailyGoalCards).toHaveLength(4);
    expect(p.isSaving).toBe(false);
  });

  it('handleSelectDailyGoal updates and marks dirty', async () => {
    // settingsState default 20, so selecting 30 marks dirty
    const p = createSettingsProfile({ t: (k) => k as never });
    p.handleSelectDailyGoal(30);
    expect(p.selectedDailyGoal).toBe(30);
    expect(p.isDirty).toBe(true);
  });

  it('handleSaveDailyGoal calls appState.saveDailyGoalMinutes', async () => {
    const saveDailyGoalMinutes = vi.fn().mockResolvedValue(undefined);
    const appState = { saveDailyGoalMinutes } as never;
    const settingsState = { dailyGoalMinutes: 20 } as never;
    const p = createSettingsProfile({ appState, settingsState, t: (k) => k as never });
    p.handleSelectDailyGoal(45);
    await p.handleSaveDailyGoal();
    expect(saveDailyGoalMinutes).toHaveBeenCalledWith(45);
  });

  it('handleSignOut calls appState.signOutAndReturnToWelcome', async () => {
    const signOutAndReturnToWelcome = vi.fn().mockResolvedValue(undefined);
    const appState = { signOutAndReturnToWelcome } as never;
    const p = createSettingsProfile({ appState, t: (k) => k as never });
    await p.handleSignOut();
    expect(signOutAndReturnToWelcome).toHaveBeenCalled();
  });

  it('loadProfileData sets profile without error', async () => {
    const p = createSettingsProfile({ t: (k) => k as never });
    await p.loadProfileData();
    expect(p.isProfileLoading).toBe(false);
    expect(p.profileError).toBeNull();
    expect(p.profile).toBeDefined();
  });

  it('destroy and stopHeartbeat do not throw', () => {
    const destroy = vi.fn();
    const stopHeartbeat = vi.fn();
    const createDevicesState = () => ({ destroy, stopHeartbeat, loadDevices: vi.fn(), remove: vi.fn(), devices: [], error: null, isLoading: false } as never);
    const p = createSettingsProfile({ createDevicesState: createDevicesState as never, t: (k) => k as never });
    expect(() => p.destroy()).not.toThrow();
    expect(() => p.stopHeartbeat()).not.toThrow();
  });
});
