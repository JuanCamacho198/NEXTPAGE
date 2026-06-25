import { render, fireEvent } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import HighlightContextMenu from '$lib/features/reader/components/HighlightContextMenu.svelte';

const t = (key: string, params?: Record<string, string | number>) => {
  const translations: Record<string, string> = {
    "highlight.contextMenuAriaLabel": "Highlight actions",
    "highlight.colors": "Colors",
    "highlight.selectColor": "Select {{color}} highlight",
    "highlight.color.yellow": "Yellow",
    "highlight.color.green": "Green",
    "highlight.color.blue": "Blue",
    "highlight.color.purple": "Purple",
    "highlight.color.orange": "Orange",
    "highlight.customColor": "Custom color",
    "reader.copiar": "Copy",
    "highlight.tag": "Tag",
    "highlight.note": "Note",
    "reader.eliminar_destacado": "Delete highlight",
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
  it('renders color palette and action buttons', () => {
    const { getByLabelText, getByText } = render(HighlightContextMenu, {
      highlightId: 'hl-1',
      highlightColor: '#FACC15',
      position: { x: 100, y: 100 },
      assignedTags: [],
      onColorSelect: () => undefined,
      onCustomColor: () => undefined,
      onCopy: () => undefined,
      onTag: () => undefined,
      onNote: () => undefined,
      onDelete: () => undefined,
      onClose: () => undefined,
      t,
    });

    expect(getByLabelText('Custom color')).toBeTruthy();
    expect(getByText('Copy')).toBeTruthy();
    expect(getByText('Tag')).toBeTruthy();
    expect(getByText('Note')).toBeTruthy();
    expect(getByText('Delete highlight')).toBeTruthy();
  });

  it('fires onColorSelect when a preset color is clicked', async () => {
    const onColorSelect = vi.fn();
    const { getByLabelText } = render(HighlightContextMenu, {
      highlightId: 'hl-1',
      highlightColor: '#FACC15',
      position: { x: 100, y: 100 },
      assignedTags: [],
      onColorSelect,
      onCustomColor: () => undefined,
      onCopy: () => undefined,
      onTag: () => undefined,
      onNote: () => undefined,
      onDelete: () => undefined,
      onClose: () => undefined,
      t,
    });

    const yellowButton = getByLabelText('Select Yellow highlight');
    await fireEvent.click(yellowButton);
    expect(onColorSelect).toHaveBeenCalledWith('#FACC15');
  });

  it('fires onCopy and closes menu', async () => {
    const onCopy = vi.fn();
    const onClose = vi.fn();
    const { getByText } = render(HighlightContextMenu, {
      highlightId: 'hl-1',
      highlightColor: '#FACC15',
      position: { x: 100, y: 100 },
      assignedTags: [],
      onColorSelect: () => undefined,
      onCustomColor: () => undefined,
      onCopy,
      onTag: () => undefined,
      onNote: () => undefined,
      onDelete: () => undefined,
      onClose,
      t,
    });

    await fireEvent.click(getByText('Copy'));
    expect(onCopy).toHaveBeenCalled();
  });

  it('closes on Escape key', async () => {
    const onClose = vi.fn();
    const { container } = render(HighlightContextMenu, {
      highlightId: 'hl-1',
      highlightColor: '#FACC15',
      position: { x: 100, y: 100 },
      assignedTags: [],
      onColorSelect: () => undefined,
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
