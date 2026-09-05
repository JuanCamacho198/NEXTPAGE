import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MockViewerAdapter } from '$lib/shared/ports';
import { createHighlightsViewDeps } from '$lib/features/highlights/highlightsViewDeps';
import HighlightsView from '$lib/features/highlights/components/HighlightsView.svelte';
import type { LibraryBookDto } from '$lib/shared/types';

vi.mock('@tauri-apps/api/core', () => ({
  invoke: vi.fn(),
  convertFileSrc: vi.fn((path: string) => `asset://localhost/${path}`),
}));

vi.mock('$lib/shared/api/tauriClient', () => ({
  listLibraryBooks: vi.fn(),
  getDefaultReaderSettings: vi.fn(() => ({
    themeMode: 'paper',
    brightness: 100,
    contrast: 100,
    selectionColor: '#3388ff',
    epub: { fontSize: 100, fontFamily: 'serif' },
    lineHeight: 1.8,
    letterSpacing: 0,
    paragraphSpacing: 1,
    textAlign: 'left',
    direction: 'ltr',
    hyphenation: false,
    verticalScrolling: false,
    margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 },
    showHeader: true,
    showFooter: true,
    showPageNumbers: true,
    progressIndicator: 'percentage',
  })),
  getReaderSettings: vi.fn(),
}));

vi.mock('@tauri-apps/plugin-fs', () => ({
  readFile: vi.fn(),
  BaseDirectory: { AppData: 0 },
  exists: vi.fn(),
  readTextFile: vi.fn(),
  writeTextFile: vi.fn(),
  remove: vi.fn(),
  rename: vi.fn(),
}));

const t = (key: string): string => key;

const books: LibraryBookDto[] = [
  {
    id: 'b1',
    title: 'Book One',
    author: 'Author One',
    format: 'epub',
    currentPage: 1,
    totalPages: 100,
    progressPercentage: 1,
    coverPath: null,
    minutesRead: 0,
    updatedAt: new Date().toISOString(),
    createdAt: new Date().toISOString(),
  },
];

describe('HighlightsView', () => {
  it('opens the note editor from the overflow menu and persists the edited note', async () => {
    const mock = new MockViewerAdapter();
    await mock.saveHighlight({
      id: 'h1',
      bookId: 'b1',
      text: 'A highlighted passage',
      color: '#facc15',
      pageNumber: 3,
      rectLeft: 0,
      rectRight: 10,
      rectTop: 0,
      rectBottom: 10,
      cfi: null,
      note: 'existing note',
    });
    const deps = createHighlightsViewDeps(mock);
    const user = userEvent.setup();
    render(HighlightsView, { props: { books, t, viewerPort: mock, deps } });

    const overflow = await screen.findByRole('button', { name: 'home.highlightsOptions' });
    await user.click(overflow);
    await user.click(screen.getByRole('button', { name: 'home.highlightsEditNote' }));

    expect(screen.getByRole('dialog')).toBeInTheDocument();

    const textarea = screen.getByPlaceholderText('highlight.notePlaceholder');
    expect(textarea).toHaveValue('existing note');
    await user.clear(textarea);
    await user.type(textarea, 'edited note');
    await user.click(screen.getByRole('button', { name: 'highlight.save' }));

    expect(await screen.findByText('edited note')).toBeInTheDocument();
    const stored = await mock.listHighlights('b1');
    const storedNote = stored[0]?.note;
    expect(storedNote).toBe('edited note');
    expect(await mock.listHighlights('b1')).toHaveLength(1);
  });
});
