import { render, fireEvent } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import SelectionToolbar from '$lib/features/reader/components/SelectionToolbar.svelte';

const t = (key: string) => {
  const translations: Record<string, string> = {
    "highlight.menuAriaLabel": "Selection actions",
    "highlight.selectColor": "Select color highlight",
    "highlight.color.yellow": "Yellow",
    "reader.copiar": "Copy",
    "reader.addToDictionary": "Add to Dictionary",
    "reader.addedToDictionary": "Saved",
  };
  return translations[key] ?? key;
};

describe('SelectionToolbar', () => {
  it('positions within container using viewerSpace bounds', () => {
    const { container } = render(SelectionToolbar, {
      selectedText: 'Selection text',
      selectionBounds: { left: 40, top: 120, right: 160, bottom: 140 },
      containerRect: { left: 100, top: 200, width: 320, height: 480 },
      onCopy: () => undefined,
      onAddToDictionary: () => undefined,
      onColorSelect: () => undefined,
      t,
    });

    const toolbar = container.querySelector('.selection-toolbar');
    expect(toolbar).toBeTruthy();
    expect(toolbar?.getAttribute('style')).toContain('left: 116px');
    expect(toolbar?.getAttribute('style')).toContain('top: 248px');
  });

  it('clamps toolbar inside container width', () => {
    const { container } = render(SelectionToolbar, {
      selectedText: 'Selection text',
      selectionBounds: { left: 5, top: 140, right: 25, bottom: 160 },
      containerRect: { left: 20, top: 100, width: 240, height: 400 },
      onCopy: () => undefined,
      onAddToDictionary: () => undefined,
      onColorSelect: () => undefined,
      t,
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
      selectedText: 'Selection text',
      selectionBounds: { left: 100, top: 120, right: 160, bottom: 140 },
      containerRect: { left: 200, top: 200, width: 320, height: 480 },
      onCopy: () => undefined,
      onAddToDictionary: () => undefined,
      onColorSelect: () => undefined,
      t,
    });

    const toolbar = container.querySelector('.selection-toolbar');
    expect(toolbar).toBeTruthy();
    const style = toolbar?.getAttribute('style') ?? '';
    expect(style).toContain('left: 216px');
    expect(style).toContain('top: 248px');
  });

  it('calls onAddToDictionary with selected text', async () => {
    const onAddToDictionary = vi.fn();
    const { getByText } = render(SelectionToolbar, {
      selectedText: 'ephemeral',
      selectionBounds: { left: 40, top: 120, right: 160, bottom: 140 },
      containerRect: { left: 100, top: 200, width: 320, height: 480 },
      onCopy: () => undefined,
      onAddToDictionary,
      onColorSelect: () => undefined,
      t,
    });

    const button = getByText('Add to Dictionary');
    await fireEvent.click(button);
    expect(onAddToDictionary).toHaveBeenCalledWith('ephemeral');
  });
});
