import { render, fireEvent } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import SelectionToolbar, {
  type SelectionData,
} from '$lib/features/reader/highlight/SelectionToolbar.svelte';

const t = (key: string) => {
  const translations: Record<string, string> = {
    'highlight.menuAriaLabel': 'Selection actions',
    'highlight.selectColor': 'Select color highlight',
    'highlight.color.yellow': 'Yellow',
    'reader.copiar': 'Copy',
    'reader.copiedToClipboard': 'Copied to clipboard',
    'reader.addToDictionary': 'Add to Dictionary',
    'reader.addedToDictionary': 'Saved',
  };
  return translations[key] ?? key;
};

function makeSelectionData(overrides: Partial<SelectionData> = {}): SelectionData {
  return {
    text: 'Selection text',
    bounds: { left: 40, top: 120, right: 160, bottom: 140 },
    rects: [{ left: 40, top: 120, width: 120, height: 20 }],
    pageNumber: 1,
    cfi: null,
    ...overrides,
  };
}

const baseProps = () => ({
  selectedText: 'Selection text',
  selectionBounds: { left: 40, top: 120, right: 160, bottom: 140 },
  containerRect: { left: 100, top: 200, width: 320, height: 480 },
  selectionData: makeSelectionData(),
  onCopy: () => undefined,
  onAddToDictionary: () => undefined,
  onColorSelect: () => undefined,
  t,
});

describe('SelectionToolbar', () => {
  it('positions within container using viewerSpace bounds', () => {
    const { container } = render(SelectionToolbar, baseProps());

    const toolbar = container.querySelector('.selection-toolbar');
    expect(toolbar).toBeTruthy();
    expect(toolbar?.getAttribute('style')).toContain('left: 116px');
    expect(toolbar?.getAttribute('style')).toContain('top: 248px');
  });

  it('clamps toolbar inside container width', () => {
    const { container } = render(SelectionToolbar, {
      ...baseProps(),
      selectionBounds: { left: 5, top: 140, right: 25, bottom: 160 },
      containerRect: { left: 20, top: 100, width: 240, height: 400 },
    });

    const toolbar = container.querySelector('.selection-toolbar');
    expect(toolbar).toBeTruthy();
    expect(toolbar?.getAttribute('style')).toContain('left: 36px');
  });

  it('translates container offset to parent-viewport when containerRect.left > 0 (EPUB coords)', () => {
    // EPUB scenario: the iframe lives at left=200 inside the parent
    // viewport. The selection math (viewerAnchorX / viewerToolbarX)
    // runs against container-relative coords, and the toolbar's
    // parent-viewport X must be `containerRect.left + viewerToolbarX`.
    //
    // In this fixture:
    //   selectionCenterX   = (100 + 160) / 2 = 130
    //   viewerAnchorX      = max(176, min(130, 144)) = 176
    //   viewerToolbarX     = max(0, 176 - 160)      = 16
    //   viewerToolbarY     = 120 - 56 - 16          = 48 (selection has room above)
    //   toolbarX (parent)  = containerRect.left + 16 = 216
    //   toolbarY (parent)  = containerRect.top  + 48 = 248
    //
    // Without the parent-viewport translation (the Menu 1 fix) the
    // toolbar would land at left=16 (purely container-relative), the
    // top-left of the iframe.
    const { container } = render(SelectionToolbar, {
      ...baseProps(),
      selectionBounds: { left: 100, top: 120, right: 160, bottom: 140 },
      containerRect: { left: 200, top: 200, width: 320, height: 480 },
    });

    const toolbar = container.querySelector('.selection-toolbar');
    expect(toolbar).toBeTruthy();
    const style = toolbar?.getAttribute('style') ?? '';
    expect(style).toContain('left: 216px');
    expect(style).toContain('top: 248px');
  });

  it('calls onAddToDictionary with selected text', async () => {
    const onAddToDictionary = vi.fn();
    const { getByLabelText } = render(SelectionToolbar, {
      ...baseProps(),
      selectedText: 'ephemeral',
      onAddToDictionary,
    });

    const button = getByLabelText('Add to Dictionary');
    await fireEvent.click(button);
    expect(onAddToDictionary).toHaveBeenCalledWith('ephemeral');
  });

  it('calls onCopy and shows copy feedback when copy button clicked', async () => {
    const onCopy = vi.fn();
    const { getByLabelText, queryByText } = render(SelectionToolbar, {
      ...baseProps(),
      selectedText: 'hello',
      onCopy,
    });

    // Feedback hidden before click
    expect(queryByText('Copied to clipboard')).toBeNull();

    const button = getByLabelText('Copy');
    await fireEvent.click(button);

    expect(onCopy).toHaveBeenCalledOnce();
    expect(queryByText('Copied to clipboard')).toBeTruthy();
  });

  it('renders SVG icons and tooltips for copy and dictionary actions', () => {
    const { getByLabelText, getAllByRole } = render(SelectionToolbar, {
      ...baseProps(),
      selectedText: 'hello',
    });

    // Both action buttons must be present with proper aria-labels
    expect(getByLabelText('Copy')).toBeTruthy();
    expect(getByLabelText('Add to Dictionary')).toBeTruthy();

    // Each icon-only button has an associated tooltip
    const tooltips = getAllByRole('tooltip');
    expect(tooltips).toHaveLength(2);
    expect(tooltips[0]).toHaveTextContent('Copy');
    expect(tooltips[1]).toHaveTextContent('Add to Dictionary');
  });

  // ── Selection data forwarding (race-condition fix) ─────────────────────
  // The toolbar captures selectionData at mount time and forwards it to
  // onColorSelect on click. This way the parent's handler has the data it
  // needs even if its global selection state has been cleared by a
  // selectionchange event in the meantime.

  it('forwards selectionData to onColorSelect when a color is clicked', async () => {
    const onColorSelect = vi.fn();
    const data = makeSelectionData({
      text: 'ephemeral',
      cfi: 'epubcfi(/6/4!/4/2/1:0)',
      pageNumber: 7,
      rects: [{ left: 1, top: 2, width: 30, height: 4 }],
    });
    const { container } = render(SelectionToolbar, {
      ...baseProps(),
      selectionData: data,
      onColorSelect,
    });

    // The color buttons have aria-label "Select color highlight <color>".
    const yellowBtn = container.querySelector('button[aria-label*="Yellow"]') as HTMLButtonElement;
    expect(yellowBtn).toBeTruthy();
    await fireEvent.click(yellowBtn);

    expect(onColorSelect).toHaveBeenCalledOnce();
    const [color, forwarded] = onColorSelect.mock.calls[0];
    expect(color).toBe('#FACC15'); // HIGHLIGHT_COLORS[0].hex
    expect(forwarded).toEqual(data);
  });

  it('does not call onColorSelect when selectionData is null', async () => {
    const onColorSelect = vi.fn();
    const { container } = render(SelectionToolbar, {
      ...baseProps(),
      selectionData: null,
      onColorSelect,
    });

    const yellowBtn = container.querySelector('button[aria-label*="Yellow"]') as HTMLButtonElement;
    expect(yellowBtn).toBeTruthy();
    await fireEvent.click(yellowBtn);

    expect(onColorSelect).not.toHaveBeenCalled();
  });
});
