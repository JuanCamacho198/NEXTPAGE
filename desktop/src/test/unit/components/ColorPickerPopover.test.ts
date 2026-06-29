import { render, fireEvent } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import ColorPickerPopover from '$lib/features/reader/highlight/ColorPickerPopover.svelte';

describe('ColorPickerPopover', () => {
  function createAnchor(): HTMLElement {
    const el = document.createElement('button');
    document.body.appendChild(el);
    el.getBoundingClientRect = () =>
      ({
        left: 100,
        top: 100,
        width: 24,
        height: 24,
        right: 124,
        bottom: 124,
        x: 100,
        y: 100,
      }) as DOMRect;
    return el;
  }

  it('calls onSelect with valid hex', async () => {
    const anchor = createAnchor();
    const onSelect = vi.fn();
    const onClose = vi.fn();
    const { getByText, container } = render(ColorPickerPopover, {
      props: {
        open: true,
        anchor,
        currentColor: '#FACC15',
        onSelect,
        onClose,
      },
    });

    const input = container.querySelector('input[type="text"]') as HTMLInputElement;
    await fireEvent.input(input, { target: { value: '#4ADE80' } });
    await fireEvent.click(getByText('Save'));
    expect(onSelect).toHaveBeenCalledWith('#4ADE80');
    expect(onClose).toHaveBeenCalled();
  });

  it('disables save for invalid hex', async () => {
    const anchor = createAnchor();
    const onSelect = vi.fn();
    const { container, getByText } = render(ColorPickerPopover, {
      props: {
        open: true,
        anchor,
        currentColor: '#FACC15',
        onSelect,
        onClose: () => undefined,
      },
    });

    const input = container.querySelector('input[type="text"]') as HTMLInputElement;
    await fireEvent.input(input, { target: { value: '#GGGGGG' } });
    const saveButton = getByText('Save') as HTMLButtonElement;
    expect(saveButton.disabled).toBe(true);
  });
});
