/**
 * Addon consent toggle (slice 8, task 8.14): the per-addon consent control.
 *
 * Activating (check → Allow) grants and persists; deactivating (uncheck)
 * withdraws and persists; Deny closes the disclosure without granting; and
 * the control reflects the persisted state on render (granted ⇒ checked +
 * granted copy, fresh ⇒ unchecked + label copy). The REAL
 * `AddonConsentToggle` runs against the REAL `createAddonConsentState` over
 * an in-memory store (no Tauri invoke touched); the screen wiring is pinned
 * by source-read assertions (the screen itself is never rendered: its
 * `$effect` calls the Tauri-backed singleton refresh, which needs invoke).
 */
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { readFileSync } from 'node:fs';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';

import AddonConsentToggle from '$lib/features/addons/components/AddonConsentToggle.svelte';
import { createAddonConsentState } from '$lib/features/addons/useAddonConsentState.svelte';
import {
  AddonConsentService,
  InMemoryAddonConsentStore,
} from '$lib/shared/services/addons/AddonConsent';
import type { MessageKey } from '$lib/shared/i18n';

const here = dirname(fileURLToPath(import.meta.url));
const readSource = (rel: string): string => readFileSync(resolve(here, rel), 'utf8');

const t = (key: MessageKey): string => key;

const ADDON_ID = 'a1b2c3d4e5f60718';
const ADDON_NAME = 'Space Books';

function consentHarness(): {
  service: AddonConsentService;
  state: ReturnType<typeof createAddonConsentState>;
  props: (granted: boolean) => Record<string, unknown>;
} {
  const service = new AddonConsentService(new InMemoryAddonConsentStore());
  const state = createAddonConsentState({ consent: service });
  const props = (granted: boolean): Record<string, unknown> => ({
    t,
    addonId: ADDON_ID,
    addonName: ADDON_NAME,
    granted,
    onToggle: (id: string, next: boolean) => void state.handleConsentToggle(id, next),
  });
  return { service, state, props };
}

describe('addon consent toggle (real component + real state)', () => {
  it('renders unchecked with the label copy when nothing is persisted', () => {
    const { props } = consentHarness();
    const { container } = render(AddonConsentToggle, props(false));
    const box = container.querySelector('input[type="checkbox"]') as HTMLInputElement;
    expect(box.checked).toBe(false);
    expect(container.textContent).toContain('addons.consent.label');
    expect(container.querySelector('[role="dialog"]')).toBeNull();
  });

  it('activating (check → Allow) grants and persists', async () => {
    const user = userEvent.setup();
    const { service, state, props } = consentHarness();
    const { container } = render(AddonConsentToggle, props(false));

    await user.click(container.querySelector('input[type="checkbox"]') as HTMLInputElement);
    // Informed consent: a disclosure opens BEFORE anything is persisted.
    expect(container.querySelector('[role="dialog"]')?.textContent).toContain(
      'addons.consent.title',
    );
    expect(service.hasConsent(ADDON_ID)).toBe(false);

    await user.click(screen.getByRole('button', { name: 'addons.consent.allow' }));
    await vi.waitFor(() => expect(service.hasConsent(ADDON_ID)).toBe(true));

    // The snapshot mirrors the durable outcome, so the next render reflects it.
    await state.refresh([ADDON_ID]);
    expect(state.consentById[ADDON_ID]).toBe(true);
  });

  it('Deny closes the disclosure without granting', async () => {
    const user = userEvent.setup();
    const { service, props } = consentHarness();
    const { container } = render(AddonConsentToggle, props(false));

    await user.click(container.querySelector('input[type="checkbox"]') as HTMLInputElement);
    expect(container.querySelector('[role="dialog"]')).not.toBeNull();
    await user.click(screen.getByRole('button', { name: 'addons.consent.deny' }));
    expect(container.querySelector('[role="dialog"]')).toBeNull();
    expect(service.hasConsent(ADDON_ID)).toBe(false);
  });

  it('deactivating (uncheck) withdraws and persists', async () => {
    const user = userEvent.setup();
    const { service, state, props } = consentHarness();
    await state.handleConsentToggle(ADDON_ID, true);
    expect(service.hasConsent(ADDON_ID)).toBe(true);

    const { container } = render(AddonConsentToggle, props(true));
    expect((container.querySelector('input[type="checkbox"]') as HTMLInputElement).checked).toBe(
      true,
    );
    await user.click(container.querySelector('input[type="checkbox"]') as HTMLInputElement);
    await vi.waitFor(() => expect(service.hasConsent(ADDON_ID)).toBe(false));

    await state.refresh([ADDON_ID]);
    expect(state.consentById[ADDON_ID]).toBe(false);
  });

  it('the control reflects the persisted state on render (granted ⇒ checked + granted copy)', async () => {
    const { state, props } = consentHarness();
    await state.handleConsentToggle(ADDON_ID, true);
    await state.refresh([ADDON_ID]);

    const { container } = render(AddonConsentToggle, props(state.consentById[ADDON_ID] ?? false));
    expect((container.querySelector('input[type="checkbox"]') as HTMLInputElement).checked).toBe(
      true,
    );
    expect(container.textContent).toContain('addons.consent.granted');
  });

  it('a restart (new state over the same store) still renders the persisted grant', async () => {
    const store = new InMemoryAddonConsentStore();
    const before = createAddonConsentState({ consent: new AddonConsentService(store) });
    await before.handleConsentToggle(ADDON_ID, true);

    const after = createAddonConsentState({ consent: new AddonConsentService(store) });
    await after.refresh([ADDON_ID]);
    const { container } = render(
      AddonConsentToggle,
      propsWith(after.consentById[ADDON_ID] ?? false),
    );
    expect((container.querySelector('input[type="checkbox"]') as HTMLInputElement).checked).toBe(
      true,
    );

    function propsWith(granted: boolean): Record<string, unknown> {
      return {
        t,
        addonId: ADDON_ID,
        addonName: ADDON_NAME,
        granted,
        onToggle: (id: string, next: boolean) => void after.handleConsentToggle(id, next),
      };
    }
  });
});

describe('addons screen consent wiring (composition)', () => {
  it('fills the consentControl slot with the toggle bound to the consent snapshot', () => {
    const screenSource = readSource('../../../../lib/features/addons/AddonsScreen.svelte');
    expect(screenSource).toContain('createAddonConsentState');
    expect(screenSource).toContain('consentState.refresh');
    expect(screenSource).toContain('consentById={consentState.consentById}');
    expect(screenSource).toContain('consentControl');
    expect(screenSource).toContain('<AddonConsentToggle');
    expect(screenSource).toContain('consentState.handleConsentToggle');

    const hook = readSource('../../../../lib/features/addons/useAddonConsentState.svelte.ts');
    expect(hook).toContain('addonConsent');
    expect(hook).toContain('ensureLoaded');
    expect(hook).toContain('.grant(');
    expect(hook).toContain('.revoke(');
    expect(hook).not.toContain('invoke(');

    const list = readSource(
      '../../../../lib/features/addons/components/AddonsInstalledList.svelte',
    );
    expect(list).toContain('consentControl');
    expect(list).toContain('consentById');
  });
});
