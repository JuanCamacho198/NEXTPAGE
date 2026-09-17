/**
 * Addon consent model (slice 8, task 8.12): the fail-closed per-addon gate.
 *
 * A fresh addon has no consent; consent is per addon (granting one leaves
 * the other absent); grant/revoke persist through the store port; and the
 * state survives a "restart" (a new service over the same store) — including
 * a withdrawal, which re-blocks immediately.
 */
import { describe, expect, it, vi } from 'vitest';

import {
  AddonConsentService,
  InMemoryAddonConsentStore,
} from '$lib/shared/services/addons/AddonConsent';

describe('AddonConsentService (fail-closed per-addon gate)', () => {
  it('a fresh addon has no consent (absent ⇒ denied by default)', async () => {
    const service = new AddonConsentService(new InMemoryAddonConsentStore());
    await service.ensureLoaded();
    expect(service.hasConsent('a1b2c3d4e5f60718')).toBe(false);
  });

  it('the gate stays fail-closed before hydration (unknown/unhydrated ⇒ false)', () => {
    const service = new AddonConsentService(new InMemoryAddonConsentStore());
    expect(service.hasConsent('a1b2c3d4e5f60718')).toBe(false);
  });

  it('consent is per addon: granting one leaves the other absent', async () => {
    const service = new AddonConsentService(new InMemoryAddonConsentStore());
    await service.grant('a1b2c3d4e5f60718');
    expect(service.hasConsent('a1b2c3d4e5f60718')).toBe(true);
    expect(service.hasConsent('b1b2c3d4e5f60718')).toBe(false);
  });

  it('grant/revoke persist through the store port', async () => {
    const store = new InMemoryAddonConsentStore();
    const setSpy = vi.spyOn(store, 'set');
    const service = new AddonConsentService(store);

    await service.grant('a1b2c3d4e5f60718');
    expect(setSpy).toHaveBeenLastCalledWith('a1b2c3d4e5f60718', true);
    expect(service.hasConsent('a1b2c3d4e5f60718')).toBe(true);

    await service.revoke('a1b2c3d4e5f60718');
    expect(setSpy).toHaveBeenLastCalledWith('a1b2c3d4e5f60718', false);
    expect(service.hasConsent('a1b2c3d4e5f60718')).toBe(false);
  });

  it('state survives a restart: a new service over the same store keeps the grant', async () => {
    const store = new InMemoryAddonConsentStore();
    const before = new AddonConsentService(store);
    await before.grant('a1b2c3d4e5f60718');

    const after = new AddonConsentService(store);
    expect(after.hasConsent('a1b2c3d4e5f60718')).toBe(false);
    await after.ensureLoaded();
    expect(after.hasConsent('a1b2c3d4e5f60718')).toBe(true);
  });

  it('a withdrawal survives a restart and re-blocks immediately', async () => {
    const store = new InMemoryAddonConsentStore();
    const before = new AddonConsentService(store);
    await before.grant('a1b2c3d4e5f60718');
    await before.revoke('a1b2c3d4e5f60718');

    const after = new AddonConsentService(store);
    await after.ensureLoaded();
    expect(after.hasConsent('a1b2c3d4e5f60718')).toBe(false);
  });

  it('ensureLoaded is memoized: concurrent and repeated calls share one store read', async () => {
    const store = new InMemoryAddonConsentStore();
    const listSpy = vi.spyOn(store, 'list');
    const service = new AddonConsentService(store);

    await Promise.all([service.ensureLoaded(), service.ensureLoaded(), service.ensureLoaded()]);
    await service.ensureLoaded();
    expect(listSpy).toHaveBeenCalledTimes(1);
  });
});
