import { fireEvent, render, screen } from '@testing-library/svelte';
import { afterEach, describe, expect, it, vi } from 'vitest';
import DictionaryView from '$lib/features/dictionary/components/DictionaryView.svelte';
import { deriveDictionaryKpis } from '$lib/features/dictionary/dictionaryKpis';
import type { DictionaryStateApi } from '$lib/shared/stores/DictionaryState.svelte';
import { messagesEn } from '$lib/shared/i18n/messages.en';
import { messagesEs } from '$lib/shared/i18n/messages.es';
import type { DictionaryWordDto } from '$lib/shared/types';

const HEX_PATTERN = /#[0-9a-fA-F]{3,8}/;
const NOW = new Date('2026-09-19T12:00:00.000Z');
const DAY_MS = 24 * 60 * 60 * 1000;

const dictEs = messagesEs as unknown as Record<string, string>;
const dictEn = messagesEn as unknown as Record<string, string>;
const tEs = (key: string): string => dictEs[key] ?? key;
const tEn = (key: string): string => dictEn[key] ?? key;

function word(
  overrides: Partial<DictionaryWordDto> & { id: string; word: string },
): DictionaryWordDto {
  return { createdAt: NOW.toISOString(), ...overrides };
}

function makeState(words: DictionaryWordDto[]): DictionaryStateApi {
  const state = {
    get words() {
      return words;
    },
    isLoading: false,
    load: vi.fn(async () => {}),
    search: vi.fn((query: string, limit = 20) =>
      words
        .filter((entry) => (entry.word ?? '').trim().toLowerCase().includes(query.toLowerCase()))
        .slice(0, limit),
    ),
    add: vi.fn(),
    remove: vi.fn(),
    update: vi.fn(),
    capture: vi.fn(),
    toggleFavorite: vi.fn(),
    exportData: vi.fn(),
    importData: vi.fn(),
    subscribeToRemoteChanges: vi.fn(),
    unsubscribe: vi.fn(),
  };
  return state as unknown as DictionaryStateApi;
}

afterEach(() => {
  vi.useRealTimers();
});

describe('DictionaryView shell (4A)', () => {
  it('renders the three frame KPI labels verbatim in Spanish', () => {
    const state = makeState([
      word({ id: '1', word: 'Efímero', sourceBookId: 'book-a' }),
      word({ id: '2', word: 'Sísifo' }),
    ]);

    render(DictionaryView, { props: { t: tEs, dictionary: state } });

    expect(screen.getByTestId('dictionary-kpi-row')).toBeInTheDocument();
    expect(screen.getByText('Palabras guardadas')).toBeInTheDocument();
    expect(screen.getByText('Esta semana')).toBeInTheDocument();
    expect(screen.getByText('Libros referenciados')).toBeInTheDocument();

    // The labels are the frame's own wording, not a paraphrase.
    expect(messagesEs['dictionary.kpiTotal']).toBe('Palabras guardadas');
    expect(messagesEs['dictionary.kpiThisWeek']).toBe('Esta semana');
    expect(messagesEs['dictionary.kpiReferencedBooks']).toBe('Libros referenciados');
  });

  it('renders faithful English equivalents and the header/search/tabs copy', () => {
    render(DictionaryView, { props: { t: tEn, dictionary: makeState([]) } });

    expect(screen.getByText('Saved words')).toBeInTheDocument();
    expect(screen.getByText('This week')).toBeInTheDocument();
    expect(screen.getByText('Books referenced')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Dictionary' })).toBeInTheDocument();
    expect(screen.getByText('New word')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Search word...')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'All' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Recent' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'A-Z' })).toBeInTheDocument();
  });

  it('contains no hardcoded hex colors', () => {
    const { container } = render(DictionaryView, {
      props: { t: tEs, dictionary: makeState([word({ id: '1', word: 'Efímero' })]) },
    });

    expect(container.innerHTML).not.toMatch(HEX_PATTERN);
  });

  it('filters by tab: store order, newest first, then alphabetical', async () => {
    const state = makeState([
      word({
        id: '1',
        word: 'Paradigma',
        createdAt: new Date(NOW.getTime() - 3 * DAY_MS).toISOString(),
      }),
      word({ id: '2', word: 'Efímero', createdAt: NOW.toISOString() }),
      word({
        id: '3',
        word: 'Kafkiano',
        createdAt: new Date(NOW.getTime() - DAY_MS).toISOString(),
      }),
    ]);

    const { container } = render(DictionaryView, { props: { t: tEs, dictionary: state } });
    const listed = (): string[] =>
      [...container.querySelectorAll('[data-testid="dictionary-list"] button')].map(
        (node) => node.textContent?.trim() ?? '',
      );

    expect(listed()).toEqual(['Paradigma', 'Efímero', 'Kafkiano']);

    await fireEvent.click(screen.getByRole('button', { name: 'Recientes' }));
    expect(listed()).toEqual(['Efímero', 'Kafkiano', 'Paradigma']);

    await fireEvent.click(screen.getByRole('button', { name: 'A-Z' }));
    expect(listed()).toEqual(['Efímero', 'Kafkiano', 'Paradigma']);
  });

  it('filters by the search field and shows the empty result state', async () => {
    vi.useFakeTimers();
    const state = makeState([
      word({ id: '1', word: 'Efímero' }),
      word({ id: '2', word: 'Sísifo' }),
    ]);

    render(DictionaryView, { props: { t: tEs, dictionary: state } });
    const input = screen.getByPlaceholderText('Buscar palabra...');

    await fireEvent.input(input, { target: { value: 'Efím' } });
    await vi.advanceTimersByTimeAsync(300);

    expect(screen.getByText('Efímero')).toBeInTheDocument();
    expect(screen.queryByText('Sísifo')).not.toBeInTheDocument();
    expect(state.search).toHaveBeenCalledWith('Efím', 50);

    await fireEvent.input(input, { target: { value: 'zzz' } });
    await vi.advanceTimersByTimeAsync(300);

    expect(screen.queryByTestId('dictionary-list')).not.toBeInTheDocument();
    expect(screen.getByText(dictEs['home.highlightsEmptyTitle'])).toBeInTheDocument();
  });
});

describe('dictionary KPI derivations (Decision 15)', () => {
  it('counts the total regardless of the entry shape', () => {
    expect(deriveDictionaryKpis([], NOW).total).toBe(0);
    expect(
      deriveDictionaryKpis([word({ id: '1', word: 'A' }), word({ id: '2', word: 'B' })], NOW).total,
    ).toBe(2);
  });

  it('counts only entries created in the last 7 days, inclusive of the boundary', () => {
    const entries = [
      word({ id: 'now', word: 'A', createdAt: NOW.toISOString() }),
      word({ id: '6d', word: 'B', createdAt: new Date(NOW.getTime() - 6 * DAY_MS).toISOString() }),
      word({ id: '7d', word: 'C', createdAt: new Date(NOW.getTime() - 7 * DAY_MS).toISOString() }),
      word({
        id: '7d+1ms',
        word: 'D',
        createdAt: new Date(NOW.getTime() - 7 * DAY_MS - 1).toISOString(),
      }),
      word({
        id: '30d',
        word: 'E',
        createdAt: new Date(NOW.getTime() - 30 * DAY_MS).toISOString(),
      }),
    ];

    expect(deriveDictionaryKpis(entries, NOW).thisWeek).toBe(3);
  });

  it('counts distinct referenced books and excludes entries without a source book', () => {
    const entries = [
      word({ id: '1', word: 'A', sourceBookId: 'book-a' }),
      word({ id: '2', word: 'B', sourceBookId: 'book-a' }),
      word({ id: '3', word: 'C', sourceBookId: 'book-b' }),
      word({ id: '4', word: 'D', sourceBookId: null }),
      word({ id: '5', word: 'E' }),
    ];

    expect(deriveDictionaryKpis(entries, NOW).referencedBooks).toBe(2);
  });
});

describe('dictionary screen i18n parity', () => {
  const screenKeys = [
    'dictionary.title',
    'dictionary.subtitle',
    'dictionary.searchPlaceholder',
    'dictionary.newWord',
    'dictionary.tabAll',
    'dictionary.tabRecent',
    'dictionary.tabAz',
    'dictionary.kpiTotal',
    'dictionary.kpiThisWeek',
    'dictionary.kpiReferencedBooks',
    'dictionary.description',
    'dictionary.personalExample',
    'dictionary.bookReference',
    'dictionary.viewBook',
    'dictionary.edit',
  ] as const;

  it('defines every screen key in both locales with an English KPI equivalent', () => {
    for (const key of screenKeys) {
      expect(messagesEn[key]).toBeDefined();
      expect(messagesEs[key]).toBeDefined();
    }

    expect(messagesEn['dictionary.kpiTotal']).toBe('Saved words');
    expect(messagesEn['dictionary.kpiThisWeek']).toBe('This week');
    expect(messagesEn['dictionary.kpiReferencedBooks']).toBe('Books referenced');
  });
});
