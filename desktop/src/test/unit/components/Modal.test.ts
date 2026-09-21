import { fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import { stubElementRect } from '../../harness/jsdomHarness';
import ModalBoundStub from '../../stubs/ModalBoundStub.svelte';
import ModalNestedStub from '../../stubs/ModalNestedStub.svelte';

/**
 * `Modal` now runs on bits-ui `Dialog` with its public API unchanged: the same
 * props, the same snippets, and the same class strings on the same elements.
 *
 * Three behaviours are library-specific rather than obvious:
 *  - the dialog is portalled to `document.body`, so nothing is queried through
 *    the render container;
 *  - bits-ui closes on an outside `pointerdown` and reads the content node's box
 *    to decide whether the pointer was truly outside, so a backdrop case must
 *    give the content element a real rect first - with jsdom's all-zero box the
 *    pointer is never outside and the close silently does not happen;
 *  - the dismissible layer registers its document listeners 1ms after mount
 *    (`afterSleep(1)`) and debounces the outside check by 10ms, so each
 *    backdrop case settles before it dispatches and waits for the outcome.
 */

const SIZES = [
  { size: 'sm', expected: 'max-w-sm' },
  { size: 'md', expected: 'max-w-lg' },
  { size: 'lg', expected: 'max-w-2xl' },
  { size: 'xl', expected: 'max-w-4xl' },
] as const;

function dialog(): HTMLElement {
  const found = document.querySelector('[data-dialog-content]');
  expect(found).toBeTruthy();
  return found as HTMLElement;
}

function overlay(): HTMLElement {
  const found = document.querySelector('[data-dialog-overlay]');
  expect(found).toBeTruthy();
  return found as HTMLElement;
}

function openStub(props: Record<string, unknown> = {}) {
  return render(ModalBoundStub, { props: { initialOpen: true, ...props } });
}

/** Gives the layer time to attach its listeners before a pointer is dispatched. */
function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 10));
}

async function escape(): Promise<void> {
  await fireEvent.keyDown(document, { key: 'Escape' });
}

/** A pointerdown on the backdrop, outside the content element's stubbed box. */
async function clickBackdrop(): Promise<void> {
  const content = dialog();
  stubElementRect(content, { left: 100, top: 100, width: 400, height: 300 });
  const target = overlay();
  await fireEvent.pointerDown(target, {
    clientX: 5,
    clientY: 5,
    pointerId: 1,
    pointerType: 'mouse',
    button: 0,
  });
  await fireEvent.pointerUp(target, {
    clientX: 5,
    clientY: 5,
    pointerId: 1,
    pointerType: 'mouse',
    button: 0,
  });
}

describe('Modal — open contract', () => {
  it('writes the bound open prop back to false on Escape, exactly once', async () => {
    const onopen = vi.fn();
    openStub({ onopen });

    await escape();
    await waitFor(() => expect(document.querySelector('[data-dialog-content]')).toBeNull());

    expect(onopen.mock.calls.flat()).toEqual([true, false]);
    // Escape is owned by bits-ui alone: no second writer, so no duplicate close.
    await fireEvent.keyDown(document, { key: 'Escape' });
    expect(onopen.mock.calls.flat()).toEqual([true, false]);
  });

  it('writes the bound open prop back to false on a backdrop pointerdown, exactly once', async () => {
    const onopen = vi.fn();
    openStub({ onopen });
    await settle();

    await clickBackdrop();
    await waitFor(() => expect(dialog().closest('[data-dialog-overlay]')).not.toBeNull());
    await waitFor(() => expect(document.querySelector('[data-dialog-content]')).toBeNull());

    expect(onopen.mock.calls.flat()).toEqual([true, false]);
  });

  it('does not close when the pointer goes down inside the content', async () => {
    const onopen = vi.fn();
    openStub({ onopen });
    await settle();

    const content = dialog();
    stubElementRect(content, { left: 100, top: 100, width: 400, height: 300 });
    await fireEvent.pointerDown(content, {
      clientX: 200,
      clientY: 200,
      pointerId: 1,
      pointerType: 'mouse',
      button: 0,
    });
    await settle();

    expect(document.querySelector('[data-dialog-content]')).not.toBeNull();
    expect(onopen.mock.calls.flat()).toEqual([true]);
  });

  it('renders nothing while closed', () => {
    const { container } = openStub({ initialOpen: false });
    expect(container.querySelector('[data-dialog-content]')).toBeNull();
    expect(document.querySelector('[data-dialog-content]')).toBeNull();
  });

  it('closes from the caller close button by writing the bound prop back', async () => {
    const onopen = vi.fn();
    openStub({ onopen });

    await fireEvent.click(dialog().querySelector('button') as HTMLElement);
    await waitFor(() => expect(document.querySelector('[data-dialog-content]')).toBeNull());

    expect(onopen.mock.calls.flat()).toEqual([true, false]);
  });
});

describe('Modal — rendered contract', () => {
  it('exposes role=dialog, aria-modal and the title as the accessible name', () => {
    openStub({ title: 'Edit metadata' });

    const found = screen.getByRole('dialog', { name: 'Edit metadata' });
    expect(found.getAttribute('aria-modal')).toBe('true');
    expect(found.getAttribute('aria-labelledby')).toBe('modal-title');
    const heading = document.getElementById('modal-title');
    expect(heading?.tagName).toBe('H2');
    expect(heading?.textContent).toBe('Edit metadata');
    expect(document.querySelector('[data-dialog-content]')).toBe(found);
  });

  it('renders the caller children inside the body scroll region', () => {
    openStub();

    const body = dialog().querySelector('[role="region"]') as HTMLElement;
    expect(body.textContent?.trim()).toBe('body content');
  });

  it('renders the footer container only when the footer snippet is supplied', async () => {
    const without = openStub();
    expect(dialog().querySelector('[class*="justify-end"]')).toBeNull();
    without.unmount();

    const withFooter = openStub({ withFooter: true, footerText: 'Save changes' });
    const footer = dialog().querySelector('[class*="justify-end"]') as HTMLElement;
    expect(footer).toBeTruthy();
    expect(footer.className).toBe(
      'flex shrink-0 items-center justify-end gap-3 border-t border-(--color-border) px-6 py-4',
    );
    expect(footer.textContent).toBe('Save changes');
    withFooter.unmount();
  });

  it('renders every size, including the unused lg, on the content element', () => {
    for (const { size, expected } of SIZES) {
      const { unmount } = openStub({ size });
      const content = dialog();
      expect(content.classList.contains(expected)).toBe(true);
      expect(content.className).toContain('rounded-xl');
      unmount();
    }
  });

  it('keeps the header, body and scroll contract byte-identical', () => {
    openStub();

    const content = dialog();
    expect(content.className).toContain(
      'flex max-h-[calc(100vh-2rem)] w-full max-w-lg flex-col overflow-hidden',
    );
    expect(content.className).toContain('rounded-xl border border-(--color-border)');
    expect(content.className).toContain('bg-(--color-elevated) shadow-xl');

    const header = content.querySelector('[class*="border-b"]') as HTMLElement;
    expect(header.className).toBe(
      'flex shrink-0 items-center justify-between border-b border-(--color-border) px-6 py-4',
    );

    const body = content.querySelector('[role="region"]') as HTMLElement;
    expect(body.className).toBe('min-h-0 flex-1 overflow-y-auto px-6 py-4');
    expect(body.getAttribute('tabindex')).toBe('0');
    expect(body.getAttribute('aria-label')).toBe('Test Modal');
  });

  it('places the caller class on the content element, not on a wrapper', () => {
    openStub({ class: 'my-custom-width' });

    const content = dialog();
    expect(content.classList.contains('my-custom-width')).toBe(true);
    expect(content.getAttribute('role')).toBe('dialog');
  });

  it('honours noCloseButton, and renders the labelled close button by default', () => {
    const withButton = openStub();
    const labelled = dialog().querySelector('button[aria-label]') as HTMLElement;
    expect(labelled).toBeTruthy();
    expect(labelled.getAttribute('aria-label')).toBeTruthy();
    withButton.unmount();

    const withoutButton = openStub({ noCloseButton: true });
    expect(dialog().querySelector('button')).toBeNull();
    withoutButton.unmount();
  });
});

describe('Modal — stacking and scroll lock', () => {
  it('closes only the topmost dialog on Escape and unlocks scroll last', async () => {
    const onouter = vi.fn();
    const oninner = vi.fn();
    const { container } = render(ModalNestedStub, { props: { onouter, oninner } });

    await fireEvent.click(container.querySelector('#open-outer') as HTMLElement);
    await waitFor(() => expect(document.querySelectorAll('[data-dialog-content]').length).toBe(1));
    await waitFor(() => expect(document.body.style.overflow).toBe('hidden'));

    await fireEvent.click(document.querySelector('#open-inner') as HTMLElement);
    await waitFor(() => expect(document.querySelectorAll('[data-dialog-content]').length).toBe(2));

    await escape();
    await waitFor(() => expect(document.querySelectorAll('[data-dialog-content]').length).toBe(1));
    expect(oninner.mock.calls.flat()).toEqual([false, true, false]);
    expect(onouter.mock.calls.flat()).toEqual([false, true]);
    // The outer dialog is still open, so background scroll stays locked.
    expect(document.body.style.overflow).toBe('hidden');

    await escape();
    await waitFor(() => expect(document.querySelectorAll('[data-dialog-content]').length).toBe(0));
    expect(onouter.mock.calls.flat()).toEqual([false, true, false]);
    await waitFor(() => expect(document.body.style.overflow).not.toBe('hidden'));
  });

  it('closes only the topmost dialog on a backdrop pointerdown', async () => {
    const onouter = vi.fn();
    const oninner = vi.fn();
    const { container } = render(ModalNestedStub, { props: { onouter, oninner } });

    await fireEvent.click(container.querySelector('#open-outer') as HTMLElement);
    await waitFor(() => expect(document.querySelectorAll('[data-dialog-content]').length).toBe(1));
    await fireEvent.click(document.querySelector('#open-inner') as HTMLElement);
    await waitFor(() => expect(document.querySelectorAll('[data-dialog-content]').length).toBe(2));
    await settle();

    await clickBackdrop();
    await waitFor(() => expect(document.querySelectorAll('[data-dialog-content]').length).toBe(1));
    expect(oninner.mock.calls.flat()).toEqual([false, true, false]);
    expect(onouter.mock.calls.flat()).toEqual([false, true]);

    const outerDialog = document.querySelector('[data-dialog-content]') as HTMLElement;
    expect(outerDialog.getAttribute('aria-labelledby')).toBe('modal-title');
    expect(document.getElementById('modal-title')?.textContent).toBe('Outer dialog');

    await escape();
    await waitFor(() => expect(document.querySelectorAll('[data-dialog-content]').length).toBe(0));
    expect(onouter.mock.calls.flat()).toEqual([false, true, false]);
  });

  it('returns focus to the element that opened each dialog', async () => {
    const { container } = render(ModalNestedStub, {});
    const outerOpener = container.querySelector('#open-outer') as HTMLElement;

    await fireEvent.click(outerOpener);
    await waitFor(() => expect(document.querySelectorAll('[data-dialog-content]').length).toBe(1));
    const innerOpener = document.querySelector('#open-inner') as HTMLElement;
    await fireEvent.click(innerOpener);
    await waitFor(() => expect(document.querySelectorAll('[data-dialog-content]').length).toBe(2));

    await escape();
    await waitFor(() => expect(document.querySelectorAll('[data-dialog-content]').length).toBe(1));
    await waitFor(() => expect(document.activeElement).toBe(innerOpener));

    await escape();
    await waitFor(() => expect(document.querySelectorAll('[data-dialog-content]').length).toBe(0));
    await waitFor(() => expect(document.activeElement).toBe(outerOpener));
  });
});
