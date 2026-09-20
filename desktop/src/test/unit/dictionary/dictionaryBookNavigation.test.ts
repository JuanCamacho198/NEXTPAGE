import { describe, expect, it, vi } from 'vitest';
import {
  openDictionaryBook,
  resolveBookTarget,
  type DictionaryBookNavigationDeps,
} from '$lib/features/dictionary/dictionaryBookNavigation';
import type { ReaderBook } from '$lib/shared/types';

const NOW = new Date('2026-09-19T12:00:00.000Z');

function makeBook(id: string, format = 'epub'): ReaderBook {
  return {
    id,
    title: 'Hábitos Atómicos',
    author: 'James Clear',
    format,
    currentPage: 0,
    totalPages: 0,
    progressPercentage: 0,
    coverPath: null,
    minutesRead: 0,
    updatedAt: NOW.toISOString(),
    createdAt: NOW.toISOString(),
    filePath: `/books/${id}.${format}`,
  };
}

function makeDeps(book: ReaderBook | null): {
  deps: DictionaryBookNavigationDeps;
  calls: string[];
} {
  const calls: string[] = [];
  const deps: DictionaryBookNavigationDeps = {
    getBookById: vi.fn((id: string) => {
      calls.push(`getBookById:${id}`);
      return book && book.id === id ? book : null;
    }),
    promoteBookForReading: vi.fn((id: string) => calls.push(`promote:${id}`)),
    setActiveReadingBookId: vi.fn((id: string) => calls.push(`active:${id}`)),
    clearShelfDetails: vi.fn(() => calls.push('clearShelfDetails')),
    openReader: vi.fn(() => calls.push('openReader')),
    resetSearch: vi.fn(() => calls.push('resetSearch')),
    recordReaderOpenMetric: vi.fn((format: string) => calls.push(`metric:${format}`)),
    startReading: vi.fn(async () => {
      calls.push('startReading');
    }),
    loadStats: vi.fn((id: string) => calls.push(`loadStats:${id}`)),
    setSearchTargetLocator: vi.fn((locator: string | null) =>
      calls.push(`locator:${locator ?? 'null'}`),
    ),
  };
  return { deps, calls };
}

describe('resolveBookTarget', () => {
  it('returns null when there is no entry or no source book', () => {
    expect(resolveBookTarget(null)).toBeNull();
    expect(resolveBookTarget({})).toBeNull();
    expect(resolveBookTarget({ sourceBookId: null })).toBeNull();
    expect(resolveBookTarget({ sourceBookId: '   ' })).toBeNull();
  });

  it('trims the book id and keeps a captured locator', () => {
    expect(resolveBookTarget({ sourceBookId: ' book-a ' })).toEqual({
      bookId: 'book-a',
      locator: null,
    });
    expect(resolveBookTarget({ sourceBookId: 'book-a', sourceLocator: ' epubcfi(/6/4) ' })).toEqual(
      { bookId: 'book-a', locator: 'epubcfi(/6/4)' },
    );
    expect(resolveBookTarget({ sourceBookId: 'book-a', sourceLocator: '  ' })).toEqual({
      bookId: 'book-a',
      locator: null,
    });
  });
});

describe('openDictionaryBook', () => {
  it('does nothing without a target', async () => {
    const { deps, calls } = makeDeps(makeBook('book-a'));

    await openDictionaryBook(null, deps);

    expect(calls).toEqual([]);
  });

  it('does nothing when the book is no longer in the library', async () => {
    const { deps, calls } = makeDeps(null);

    await openDictionaryBook({ bookId: 'book-a', locator: 'epubcfi(/6/4)' }, deps);

    expect(calls).toEqual(['getBookById:book-a']);
    expect(deps.startReading).not.toHaveBeenCalled();
    expect(deps.setSearchTargetLocator).not.toHaveBeenCalled();
  });

  it('opens the reader at the captured passage, locator set last', async () => {
    const { deps, calls } = makeDeps(makeBook('book-a'));

    await openDictionaryBook({ bookId: 'book-a', locator: 'epubcfi(/6/4!/4/2)' }, deps);

    expect(calls).toEqual([
      'getBookById:book-a',
      'promote:book-a',
      'active:book-a',
      'clearShelfDetails',
      'openReader',
      'resetSearch',
      'metric:epub',
      'startReading',
      'loadStats:book-a',
      'locator:epubcfi(/6/4!/4/2)',
    ]);
  });

  it('clears the target locator when the entry captured none', async () => {
    const { deps } = makeDeps(makeBook('book-b', 'pdf'));

    await openDictionaryBook({ bookId: 'book-b', locator: null }, deps);

    expect(deps.setSearchTargetLocator).toHaveBeenCalledWith(null);
    expect(deps.recordReaderOpenMetric).toHaveBeenCalledWith('pdf');
  });
});
