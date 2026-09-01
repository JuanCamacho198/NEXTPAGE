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
});
