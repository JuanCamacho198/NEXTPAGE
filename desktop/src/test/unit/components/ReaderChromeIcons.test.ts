import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import ReaderControls from '$lib/features/reader/chrome/ReaderControls.svelte';
import ReaderHeader from '$lib/features/reader/chrome/ReaderHeader.svelte';
import ReaderTextSettings from '$lib/features/reader/chrome/ReaderTextSettings.svelte';
import type { MessageKey } from '$lib/shared/i18n';
import type { ReaderSettings } from '$lib/shared/types';

/**
 * Reader-chrome icon migration (slice 11). The three migrated files hold 13
 * `<Icon` call sites, five of them ternaries. The shim resolved a name to a
 * lucide component; these call sites resolve the same glyphs directly, so the
 * assertion here is per-branch glyph identity under the post-slice-9 map
 * (`fullscreen-exit` is `Shrink`, not the expand glyph the retired `paths`
 * record drew for both fullscreen states).
 */

const t = (key: MessageKey, _params?: Record<string, string | number>): string => key;

/** The glyph class lucide emits: `lucide-icon lucide-<name> ...`. */
const glyphOf = (icon: Element): string | undefined =>
  (icon.getAttribute('class') ?? '')
    .split(/\s+/)
    .find((cls) => cls.startsWith('lucide-') && cls !== 'lucide-icon');

const SVG_CONTRACT = {
  viewBox: '0 0 24 24',
  stroke: 'currentColor',
  'stroke-width': '1.8',
  'aria-hidden': 'true',
} as const;

const glyphs = (root: HTMLElement): (string | undefined)[] =>
  Array.from(root.querySelectorAll('svg.lucide-icon')).map(glyphOf);

function expectGlyphContract(icon: Element): void {
  expect(icon.getAttribute('class')).toContain('lucide-icon');
  for (const [attr, value] of Object.entries(SVG_CONTRACT)) {
    expect(icon.getAttribute(attr), attr).toBe(value);
  }
  expect(icon.getAttribute('width')).toBe('14');
}

const readerSettings: ReaderSettings = {
  themeMode: 'paper',
  brightness: 1,
  contrast: 1,
  selectionColor: '#fde68a',
  epub: { fontSize: 100, fontFamily: '' },
  lineHeight: 1.8,
  letterSpacing: 0,
  paragraphSpacing: 1,
  textAlign: 'left',
  direction: 'ltr',
  hyphenation: false,
  verticalScrolling: false,
  margins: { top: 1, bottom: 1, left: 1, right: 1 },
  showHeader: true,
  showFooter: true,
  showPageNumbers: true,
  progressIndicator: 'percentage',
};

function renderHeader(overrides: Record<string, unknown> = {}) {
  return render(ReaderHeader, {
    title: 'Book',
    showTocPanel: false,
    searchPanelOpen: false,
    showTextSettings: false,
    showBookmarks: false,
    isFullscreen: false,
    t,
    onBackToHome: () => {},
    onToggleToc: () => {},
    onToggleSearch: () => {},
    onToggleTextSettings: () => {},
    onToggleBookmarks: () => {},
    onToggleFullscreen: () => {},
    ...overrides,
  });
}

describe('reader chrome icon migration', () => {
  it('renders ReaderControls through direct lucide components on both fullscreen branches', () => {
    const props = {
      currentPage: 1,
      totalPages: 10,
      t,
      onPrev: () => {},
      onNext: () => {},
      onGoToPage: async () => true,
      onToggleFullscreen: () => {},
      onToggleToc: () => {},
    };

    const { container, unmount } = render(ReaderControls, { ...props, isFullscreen: false });
    // The left/children snippets are absent, so the TOC trigger is present.
    expect(glyphs(container)).toEqual([
      'lucide-menu',
      'lucide-chevron-left',
      'lucide-arrow-right',
      'lucide-expand',
    ]);
    for (const icon of container.querySelectorAll('svg.lucide-icon')) expectGlyphContract(icon);
    unmount();

    const full = render(ReaderControls, { ...props, isFullscreen: true });
    expect(glyphs(full.container)).toEqual([
      'lucide-menu',
      'lucide-chevron-left',
      'lucide-arrow-right',
      'lucide-shrink',
    ]);
    full.unmount();
  });

  it('keeps the accessible name identical on both ReaderControls fullscreen branches', () => {
    const props = {
      currentPage: 1,
      totalPages: 10,
      t,
      onPrev: () => {},
      onNext: () => {},
      onGoToPage: async () => true,
      onToggleFullscreen: () => {},
      onToggleToc: () => {},
    };

    const off = render(ReaderControls, { ...props, isFullscreen: false });
    expect(screen.getByLabelText('pdf.fullscreenEnter')).toBeInTheDocument();
    off.unmount();

    render(ReaderControls, { ...props, isFullscreen: true });
    expect(screen.getByLabelText('pdf.fullscreenExit')).toBeInTheDocument();
  });

  it('renders the six header icons as components on the closed-tool branch', () => {
    const { container } = renderHeader();

    expect(glyphs(container)).toEqual([
      'lucide-chevron-left',
      'lucide-menu',
      'lucide-search',
      'lucide-settings',
      'lucide-bookmark',
      'lucide-expand',
    ]);
    for (const icon of container.querySelectorAll('svg.lucide-icon')) expectGlyphContract(icon);
  });

  it('swaps all five header ternaries to the close glyph when their panel is open', () => {
    const { container } = renderHeader({
      showTocPanel: true,
      searchPanelOpen: true,
      showTextSettings: true,
      showBookmarks: true,
      isFullscreen: true,
    });

    expect(glyphs(container)).toEqual([
      'lucide-chevron-left',
      'lucide-x',
      'lucide-x',
      'lucide-x',
      'lucide-x',
      'lucide-shrink',
    ]);
  });

  it('renders the immersive reading row with chevron and arrow glyphs', () => {
    const { container } = renderHeader({
      isFullscreen: true,
      currentPage: 3,
      totalPages: 10,
      onPrev: () => {},
      onNext: () => {},
      onGoToPage: async () => true,
    });

    expect(glyphs(container)).toEqual([
      'lucide-chevron-left',
      'lucide-menu',
      'lucide-search',
      'lucide-settings',
      'lucide-bookmark',
      'lucide-shrink',
      'lucide-chevron-left',
      'lucide-arrow-right',
    ]);
  });

  it('renders the saved-toast glyph through the direct lucide component', async () => {
    const { container } = render(ReaderTextSettings, {
      open: true,
      format: 'epub',
      readerSettings,
      onSettingsChange: () => {},
      t,
      onClose: () => {},
    });

    expect(container.querySelector('svg.lucide-check')).toBeNull();

    await fireEvent.click(screen.getByLabelText('reader.font_increase'));

    const toastIcon = container.querySelector('svg.lucide-check');
    expect(toastIcon).not.toBeNull();
    expect(toastIcon?.getAttribute('class')).toContain('shrink-0');
    if (!toastIcon) throw new Error('the saved toast did not render its check glyph');
    expectGlyphContract(toastIcon);
  });
});
