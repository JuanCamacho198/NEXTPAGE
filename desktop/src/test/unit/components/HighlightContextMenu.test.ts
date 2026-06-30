import { render, fireEvent } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import HighlightContextMenu from '$lib/features/reader/highlight/HighlightContextMenu.svelte';

const t = (key: string, params?: Record<string, string | number>) => {
  const translations: Record<string, string> = {
    'highlight.contextMenuAriaLabel': 'Highlight actions',
    'highlight.changeColor': 'Change color',
    'highlight.colors': 'Colors',
    'highlight.selectColor': 'Select {{color}} highlight',
    'highlight.color.yellow': 'Yellow',
    'highlight.color.green': 'Green',
    'highlight.color.blue': 'Blue',
    'highlight.color.purple': 'Purple',
    'highlight.color.orange': 'Orange',
    'highlight.customColor': 'Custom color',
    'reader.copiar': 'Copy',
    'highlight.tag': 'Tag',
    'highlight.note': 'Note',
    'reader.eliminar_destacado': 'Delete highlight',
  };
  let value = translations[key] ?? key;
  if (params) {
    for (const [paramKey, paramValue] of Object.entries(params)) {
      value = value.replace(`{{${paramKey}}}`, String(paramValue));
    }
  }
  return value;
};

describe('HighlightContextMenu', () => {
  it('renders the change color trigger and action buttons', () => {
    const { getByLabelText, getByText } = render(HighlightContextMenu, {
      highlightId: 'hl-1',
      position: { x: 100, y: 100 },
      assignedTags: [],
      onCustomColor: () => undefined,
      onCopy: () => undefined,
      onTag: () => undefined,
      onNote: () => undefined,
      onDelete: () => undefined,
      onClose: () => undefined,
      t,
    });

    expect(getByLabelText('Change color')).toBeTruthy();
    expect(getByText('Copy')).toBeTruthy();
    expect(getByText('Tag')).toBeTruthy();
    expect(getByText('Note')).toBeTruthy();
    expect(getByText('Delete highlight')).toBeTruthy();
  });

  it('opens color picker via onCustomColor when palette trigger clicked', async () => {
    const onCustomColor = vi.fn();
    const { getByLabelText } = render(HighlightContextMenu, {
      highlightId: 'hl-1',
      position: { x: 100, y: 100 },
      assignedTags: [],
      onCustomColor,
      onCopy: () => undefined,
      onTag: () => undefined,
      onNote: () => undefined,
      onDelete: () => undefined,
      onClose: () => undefined,
      t,
    });

    const paletteBtn = getByLabelText('Change color');
    await fireEvent.click(paletteBtn);
    expect(onCustomColor).toHaveBeenCalledOnce();
  });

  it('fires onCopy and closes menu', async () => {
    const onCopy = vi.fn();
    const onClose = vi.fn();
    // Use the Copy button by its aria-label. The visible "Copy" text only
    // lives in an opacity-0 tooltip span (not clickable), so getByText
    // targets the wrong element. getByLabelText resolves to the actual
    // <button> with onclick={handleCopyClick}.
    const { getByLabelText } = render(HighlightContextMenu, {
      highlightId: 'hl-1',
      position: { x: 100, y: 100 },
      assignedTags: [],
      onCustomColor: () => undefined,
      onCopy,
      onTag: () => undefined,
      onNote: () => undefined,
      onDelete: () => undefined,
      onClose,
      t,
    });

    await fireEvent.click(getByLabelText('Copy'));
    expect(onCopy).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('closes on Escape key', async () => {
    const onClose = vi.fn();
    const { container } = render(HighlightContextMenu, {
      highlightId: 'hl-1',
      position: { x: 100, y: 100 },
      assignedTags: [],
      onCustomColor: () => undefined,
      onCopy: () => undefined,
      onTag: () => undefined,
      onNote: () => undefined,
      onDelete: () => undefined,
      onClose,
      t,
    });

    const menu = container.querySelector('[role="menu"]');
    await fireEvent.keyDown(menu!, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
  });
});
