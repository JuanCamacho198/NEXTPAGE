import { fireEvent, render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SearchPanel } from '$lib/features/reader';
import type { SearchBookTextResponse } from '$lib/types';
import { searchBookText } from '$lib/shared/api/tauriClient';

const t = (key: string) => {
  const dictionary: Record<string, string> = {
    'search.title': 'In-Book Search',
    'search.placeholder': 'Search text in this book',
    'search.search': 'Search',
    'search.searching': 'Searching...',
    'search.locator': 'Locator',
    'search.page': 'Page',
    'search.matches': 'matches',
    'search.prev': 'Prev',
    'search.next': 'Next',
    'search.noMatches': 'No matches found for this query.',
  };

  return dictionary[key] ?? key;
};

// The reader feature barrel (`$lib/features/reader`) loads ReaderWorkspace,
// bookmarks state, pdfStreaming and ReaderDomainState at import time; the
// tauriClient mock must expose every export those modules bind at load.
// NOTE: no top-level variables inside the factory — vi.mock is hoisted.
vi.mock('$lib/shared/api/tauriClient', () => {
  const defaults = {
    themeMode: 'paper',
    brightness: 100,
    contrast: 100,
    selectionColor: '#3b82f6',
    epub: { fontSize: 16, fontFamily: 'serif' },
  };
  return {
    searchBookText: vi.fn(),
    getDefaultReaderSettings: vi.fn(() => defaults),
    getReaderSettings: vi.fn().mockResolvedValue(defaults),
    upsertReaderSettings: vi.fn().mockResolvedValue(defaults),
    saveHighlight: vi.fn().mockResolvedValue(undefined),
    deleteHighlight: vi.fn().mockResolvedValue(undefined),
    updateHighlight: vi.fn().mockResolvedValue(undefined),
    saveHighlightTags: vi.fn().mockResolvedValue(undefined),
    createTag: vi.fn().mockResolvedValue({ id: 1, name: 'tag' }),
    listTags: vi.fn().mockResolvedValue([]),
    listTagsForHighlight: vi.fn().mockResolvedValue([]),
    addDictionaryWord: vi.fn().mockResolvedValue({ id: 1, word: 'w' }),
    listHighlights: vi.fn().mockResolvedValue([]),
    listBookmarks: vi.fn().mockResolvedValue([]),
    saveBookmark: vi.fn().mockResolvedValue(undefined),
    deleteBookmark: vi.fn().mockResolvedValue(undefined),
    getFileBytes: vi.fn().mockResolvedValue([]),
    setReadingStatus: vi.fn().mockResolvedValue(undefined),
  };
});

// Use type cast since vi.mocked is not available in vitest 4.x
const mockedSearchBookText = searchBookText as unknown as ReturnType<typeof vi.fn>;

describe('SearchPanel', () => {
  it('shows no-match state and emits search/jump callbacks', async () => {
    mockedSearchBookText.mockResolvedValueOnce({
      items: [],
      total: 0,
      page: 1,
      pageSize: 200,
    });

    const noMatches = await searchBookText({
      bookId: 'book-1',
      query: 'absent',
      page: 1,
      pageSize: 200,
    });

    const onSearch = vi.fn();
    const onJump = vi.fn();

    const rendered = render(SearchPanel, {
      bookId: 'book-1',
      disabledReason: null,
      isSearching: false,
      response: noMatches,
      onSearch,
      onJump,
      t,
    });

    expect(screen.getByText('No matches found for this query.')).toBeInTheDocument();

    const user = userEvent.setup();
    const input = screen.getByPlaceholderText('Search text in this book');
    await user.type(input, 'needle');
    await fireEvent.submit(input.closest('form') as HTMLFormElement);
    expect(onSearch).toHaveBeenCalledWith('needle', 1);

    const withMatches: SearchBookTextResponse = {
      items: [
        {
          chunkId: 'chunk-1',
          bookId: 'book-1',
          locator: 'epubcfi(/6/2)',
          snippet: '...needle...',
          rank: 0.2,
        },
      ],
      total: 1,
      page: 1,
      pageSize: 200,
    };

    rendered.unmount();
    render(SearchPanel, {
      bookId: 'book-1',
      disabledReason: null,
      isSearching: false,
      response: withMatches,
      onSearch,
      onJump,
      t,
    });
    await user.click(screen.getByRole('button', { name: /needle/ }));

    expect(onJump).toHaveBeenCalledTimes(1);
    expect(onJump.mock.calls[0][0]).toMatchObject({
      locator: 'epubcfi(/6/2)',
    });
  });
});
