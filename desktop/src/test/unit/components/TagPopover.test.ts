import { render, fireEvent } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import TagPopover from '$lib/features/reader/highlight/TagPopover.svelte';

const t = (key: string) => {
  const translations: Record<string, string> = {
    "highlight.tagPopoverAriaLabel": "Tags",
    "highlight.tags": "Tags",
    "highlight.createTag": "Create",
    "highlight.newTagPlaceholder": "New tag",
    "highlight.noTagsYet": "No tags yet",
  };
  return translations[key] ?? key;
};

describe('TagPopover', () => {
  function createAnchor(): HTMLElement {
    const el = document.createElement('button');
    document.body.appendChild(el);
    el.getBoundingClientRect = () => ({ left: 100, top: 100, width: 24, height: 24, right: 124, bottom: 124, x: 100, y: 100 } as DOMRect);
    return el;
  }

  it('toggles assigned tag', async () => {
    const anchor = createAnchor();
    const onToggle = vi.fn();
    const { getByText } = render(TagPopover, {
      props: {
        open: true,
        anchor,
        assignedTagIds: [],
        allTags: [{ id: 'tag-1', name: 'Review', createdAt: '2024-01-01T00:00:00Z' }],
        onCreate: () => undefined,
        onToggle,
        onClose: () => undefined,
        t,
      },
    });

    await fireEvent.click(getByText('Review'));
    expect(onToggle).toHaveBeenCalledWith('tag-1');
  });

  it('creates a new tag on Enter', async () => {
    const anchor = createAnchor();
    const onCreate = vi.fn();
    const { getByPlaceholderText } = render(TagPopover, {
      props: {
        open: true,
        anchor,
        assignedTagIds: [],
        allTags: [],
        onCreate,
        onToggle: () => undefined,
        onClose: () => undefined,
        t,
      },
    });

    const input = getByPlaceholderText('New tag') as HTMLInputElement;
    await fireEvent.input(input, { target: { value: 'Idea' } });
    await fireEvent.keyDown(input, { key: 'Enter' });
    expect(onCreate).toHaveBeenCalledWith('Idea');
  });

  it('toggles existing tag instead of creating duplicate', async () => {
    const anchor = createAnchor();
    const onCreate = vi.fn();
    const onToggle = vi.fn();
    const { getByPlaceholderText } = render(TagPopover, {
      props: {
        open: true,
        anchor,
        assignedTagIds: [],
        allTags: [{ id: 'tag-1', name: 'Review', createdAt: '2024-01-01T00:00:00Z' }],
        onCreate,
        onToggle,
        onClose: () => undefined,
        t,
      },
    });

    const input = getByPlaceholderText('New tag') as HTMLInputElement;
    await fireEvent.input(input, { target: { value: 'review' } });
    await fireEvent.keyDown(input, { key: 'Enter' });
    expect(onCreate).not.toHaveBeenCalled();
    expect(onToggle).toHaveBeenCalledWith('tag-1');
  });
});
