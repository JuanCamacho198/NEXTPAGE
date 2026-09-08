import { describe, expect, it } from 'vitest';
import { messagesEn } from '$lib/shared/i18n/messages.en';
import { messagesEs } from '$lib/shared/i18n/messages.es';

describe('discover i18n EN+ES parity (PR4)', () => {
  it('exposes every discover.* and sidebar.discover key in both locales', () => {
    const discoverKeys = [
      'sidebar.discover',
      'discover.search',
      'discover.searchPlaceholder',
      'discover.searchAriaLabel',
      'discover.idle',
      'discover.empty',
      'discover.endOfResults',
      'discover.retry',
      'discover.loading',
      'discover.loadingMore',
      'discover.offline',
      'discover.errorUpstream',
      'discover.errorInvalidPage',
      'discover.errorNotFound',
      'discover.detailNotFound',
      'discover.dismiss',
      'discover.download',
      'discover.byAuthors',
      'discover.languages',
      'discover.subjects',
    ] as const;

    for (const key of discoverKeys) {
      expect(messagesEn[key]).toBeDefined();
      expect(messagesEs[key]).toBeDefined();
    }

    expect(messagesEs['discover.search']).toBe('Buscar');
    expect(messagesEs['sidebar.discover']).toBe('Descubrir');
  });
});
