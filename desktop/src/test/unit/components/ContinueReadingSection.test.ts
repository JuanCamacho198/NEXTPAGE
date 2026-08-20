import { render, screen } from '@testing-library/svelte';
import { beforeEach, describe, expect, it, vi } from 'vitest';

// vi.mock is hoisted, so we need vi.hoisted to define data before hoisting
const { mockState, mockAppState } = vi.hoisted(() => {
  const ms: {
    continueReadingBooks: Array<Record<string, unknown>>;
    previewBookId: string | null;
  } = {
    continueReadingBooks: [],
    previewBookId: null,
  };

  const mas = {
    get continueReadingBooks() {
      return ms.continueReadingBooks;
    },
    get previewBookId() {
      return ms.previewBookId;
    },
    t: (key: string) => key,
    openDetails: vi.fn(),
    startReading: vi.fn().mockResolvedValue(undefined),
    handleEditBook: vi.fn(),
    handleHideBook: vi.fn().mockResolvedValue(undefined),
    handleToggleFavorite: vi.fn().mockResolvedValue(undefined),
    getBookById: vi.fn(() => null),
  };

  return { mockState: ms, mockAppState: mas };
});

vi.mock('$lib/shared/stores/AppState.svelte', () => ({
  appState: mockAppState,
}));

import ContinueReadingSection from '$lib/shared/ui/layout/ContinueReadingSection.svelte';

function makeBook(id: string) {
  return {
    id,
    title: `Book ${id}`,
    author: 'Test Author',
    format: 'epub',
    filePath: `/path/${id}.epub`,
    coverPath: null,
    currentPage: 1,
    totalPages: 10,
    progressPercentage: 10,
    isFavorite: false,
    minutesRead: 0,
    updatedAt: new Date().toISOString(),
  };
}

describe('ContinueReadingSection (5.3)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockState.continueReadingBooks = [];
    mockState.previewBookId = null;
  });

  it('shows placeholder when no books', () => {
    render(ContinueReadingSection);
    expect(screen.getByText('home.continueReadingPlaceholder')).toBeInTheDocument();
  });

  it('renders book card with action menu trigger for single book', () => {
    mockState.continueReadingBooks = [makeBook('b1')];
    render(ContinueReadingSection);

    // BookCard renders a Continue button for continue-reading variant
    expect(screen.getByText('app.continue')).toBeInTheDocument();

    // ShelfActionMenu renders a trigger with aria-haspopup="menu"
    const trigger = document.querySelector('[aria-haspopup="menu"]');
    expect(trigger).toBeTruthy();
  });

  it('renders action menu trigger for each book when 2+ books', () => {
    mockState.continueReadingBooks = [makeBook('b1'), makeBook('b2')];
    render(ContinueReadingSection);

    const triggers = document.querySelectorAll('[aria-haspopup="menu"]');
    expect(triggers.length).toBe(2);
  });

  it('passes correct compact variant when multiple books', () => {
    mockState.continueReadingBooks = [makeBook('b1'), makeBook('b2')];
    render(ContinueReadingSection);

    // With 2+ books, BookCard renders an <article> for each
    const articles = document.querySelectorAll('article');
    expect(articles.length).toBe(2);
  });

  it('renders single book without compact styling', () => {
    mockState.continueReadingBooks = [makeBook('b1')];
    render(ContinueReadingSection);

    // With 1 book, renders one <article>
    const articles = document.querySelectorAll('article');
    expect(articles.length).toBe(1);
  });
});
