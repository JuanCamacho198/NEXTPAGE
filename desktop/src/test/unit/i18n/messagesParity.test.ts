import { describe, expect, it } from 'vitest';
import { messagesEn } from '$lib/shared/i18n/messages.en';
import { messagesEs } from '$lib/shared/i18n/messages.es';

describe('i18n es/en parity (REQ-X-Cross-2)', () => {
  it('messages.es exposes exactly the same keys as messages.en', () => {
    const enKeys = Object.keys(messagesEn).sort();
    const esKeys = Object.keys(messagesEs).sort();

    expect(esKeys).toEqual(enKeys);
  });

  it('includes the 8 home-redesign keys in both locales', () => {
    const homeKeys = [
      'home.greetingImport',
      'home.metrics.dailyGoalLabel',
      'home.metrics.minutesFormat',
      'home.continue.progress',
      'home.continue.liveBadge',
      'home.continue.countAria',
      'home.continue.nextBook',
      'home.continue.prevBook',
    ] as const;

    for (const key of homeKeys) {
      expect(messagesEn[key]).toBeDefined();
      expect(messagesEs[key]).toBeDefined();
    }

    // Copy parity for the carousel/greeting keys: `home.continue.progress`
    // mirrors the pre-existing `home.shelfSort.progress` pair (Progreso/Progress)
    expect(messagesEs['home.continue.progress']).toBe('Progreso');
    expect(messagesEn['home.continue.progress']).toBe('Progress');
    expect(messagesEs['home.continue.nextBook']).toBe('Siguiente');
    expect(messagesEn['home.continue.nextBook']).toBe('Next');
    expect(messagesEs['home.continue.prevBook']).toBe('Anterior');
    expect(messagesEn['home.continue.prevBook']).toBe('Previous');
  });

  it('includes the addons route keys in both locales', () => {
    const addonsKeys = ['sidebar.addons', 'discover.manageAddons'] as const;

    for (const key of addonsKeys) {
      expect(messagesEn[key]).toBeDefined();
      expect(messagesEs[key]).toBeDefined();
    }

    // Exact copy: the sidebar label is identical in both locales,
    // the Discover CTA uses the verbatim spec labels.
    expect(messagesEn['sidebar.addons']).toBe('Addons');
    expect(messagesEs['sidebar.addons']).toBe('Addons');
    expect(messagesEn['discover.manageAddons']).toBe('Manage addons');
    expect(messagesEs['discover.manageAddons']).toBe('Gestionar addons');
  });

  it('includes the addons screen keys in both locales', () => {
    const screenKeys = [
      'addons.title',
      'addons.subtitle',
      'addons.firstParty.title',
      'addons.firstParty.builtinTitle',
      'addons.firstParty.curatedTitle',
      'addons.firstParty.readOnly',
      'addons.firstParty.builtinBadge',
      'addons.install.errorInline',
      'addons.install.offline',
      'addons.install.retry',
      'addons.install.installing',
      'addons.consent.label',
      'addons.consent.granted',
      'addons.consent.title',
      'addons.consent.body',
      'addons.consent.allow',
      'addons.consent.deny',
    ] as const;

    for (const key of screenKeys) {
      expect(messagesEn[key]).toBeDefined();
      expect(messagesEs[key]).toBeDefined();
    }

    // Exact copy: screen title + badge + progress states + consent actions.
    expect(messagesEn['addons.title']).toBe('Addons');
    expect(messagesEs['addons.title']).toBe('Addons');
    expect(messagesEn['addons.firstParty.builtinBadge']).toBe('Built-in');
    expect(messagesEs['addons.firstParty.builtinBadge']).toBe('Integrada');
    expect(messagesEn['addons.install.retry']).toBe('Retry');
    expect(messagesEs['addons.install.retry']).toBe('Reintentar');
    expect(messagesEn['addons.install.installing']).toBe('Installing…');
    expect(messagesEs['addons.install.installing']).toBe('Instalando…');
    expect(messagesEn['addons.consent.allow']).toBe('Allow');
    expect(messagesEs['addons.consent.allow']).toBe('Permitir');
    expect(messagesEn['addons.consent.deny']).toBe('Deny');
    expect(messagesEs['addons.consent.deny']).toBe('Denegar');
  });
});
