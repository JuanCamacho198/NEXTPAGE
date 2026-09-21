/**
 * Wrapper-local menu semantics for caller-owned markup.
 *
 * `DropMenu` renders its `children` verbatim and its call sites hand it plain
 * `<button>` elements, so bits-ui cannot register them as `DropdownMenu.Item`:
 * its roving focus and typeahead apply only to its own item components. This
 * module is the missing item-registration layer (design D10).
 *
 * Both actions are stateless: they own no module-level state and read only the
 * node they are applied to, so the overlay swaps stay uncoupled and any single
 * one of them can be reverted alone.
 */

export interface MenuItemSemanticsOptions {
  /** Focus the first menu item when the content mounts. Defaults to `true`. */
  focusFirst?: boolean;
}

const INTERACTIVE_SELECTOR =
  'button, a[href], input:not([type="hidden"]), select, textarea, [role="menuitem"]';

interface TaggedItem {
  element: HTMLElement;
  role: string | null;
  tabIndex: string | null;
}

/**
 * Tags the interactive descendants of a menu content element as `menuitem`,
 * applies a roving tabindex and wires ArrowUp/ArrowDown/Home/End/Enter/Space.
 *
 * Escape is deliberately not consumed here: the enclosing bits-ui content owns
 * the dismissal and the focus restore, and swallowing the key would stop both.
 */
export function menuItemSemantics(
  node: HTMLElement,
  options: MenuItemSemanticsOptions = {},
): { destroy: () => void } {
  const focusFirst = options.focusFirst ?? true;

  let tagged: TaggedItem[] = [];
  let markedAncestors: HTMLElement[] = [];
  let activeIndex = -1;

  function collect(): HTMLElement[] {
    // Descendants, not direct children: measured, `HighlightsView` nests its
    // items inside a `div.flex.flex-col` while the other call sites render
    // their buttons as direct children of the content.
    return Array.from(node.querySelectorAll<HTMLElement>(INTERACTIVE_SELECTOR));
  }

  function untag(): void {
    for (const entry of tagged) {
      if (entry.role === null) entry.element.removeAttribute('role');
      else entry.element.setAttribute('role', entry.role);
      if (entry.tabIndex === null) entry.element.removeAttribute('tabindex');
      else entry.element.setAttribute('tabindex', entry.tabIndex);
    }
    tagged = [];
    for (const ancestor of markedAncestors) ancestor.removeAttribute('role');
    markedAncestors = [];
    activeIndex = -1;
  }

  function tag(): void {
    untag();
    const items = collect();
    for (const item of items) {
      tagged.push({
        element: item,
        role: item.getAttribute('role'),
        tabIndex: item.getAttribute('tabindex'),
      });
      item.setAttribute('role', 'menuitem');
      item.setAttribute('tabindex', '-1');
    }

    // A plain wrapper between `role="menu"` and its items would break the ARIA
    // ownership relationship. `role="none"` removes such a wrapper from the
    // accessibility tree and has no visual effect.
    const marked = new Set<HTMLElement>();
    for (const item of items) {
      let ancestor = item.parentElement;
      while (ancestor && ancestor !== node) {
        if (!marked.has(ancestor) && ancestor.getAttribute('role') === null) {
          marked.add(ancestor);
          markedAncestors.push(ancestor);
          ancestor.setAttribute('role', 'none');
        }
        ancestor = ancestor.parentElement;
      }
    }

    setActive(focusFirst && items.length > 0 ? 0 : -1, focusFirst);
  }

  function setActive(index: number, focus: boolean): void {
    activeIndex = index;
    tagged.forEach((entry, position) => {
      entry.element.setAttribute('tabindex', position === index ? '0' : '-1');
    });
    if (focus && index >= 0) tagged[index]?.element.focus();
  }

  function move(step: 1 | -1): void {
    const count = tagged.length;
    if (count === 0) return;
    const next =
      activeIndex < 0 ? (step === 1 ? 0 : count - 1) : (activeIndex + step + count) % count;
    setActive(next, true);
  }

  function onKeydown(event: KeyboardEvent): void {
    switch (event.key) {
      case 'ArrowDown':
        move(1);
        event.preventDefault();
        return;
      case 'ArrowUp':
        move(-1);
        event.preventDefault();
        return;
      case 'Home':
        setActive(0, true);
        event.preventDefault();
        return;
      case 'End':
        setActive(tagged.length - 1, true);
        event.preventDefault();
        return;
      case 'Enter':
      case ' ': {
        // The focused item wins when the key lands on one, so activation never
        // depends on the roving index having been updated by a previous arrow.
        const focused = tagged.find((entry) => entry.element === event.target);
        const active = focused ?? tagged[activeIndex];
        if (!active) return;
        // Prevented because the item is a real `<button>`: without this the
        // browser would fire its own click in addition to this one.
        event.preventDefault();
        active.element.click();
        return;
      }
      default:
        return;
    }
  }

  node.addEventListener('keydown', onKeydown);
  tag();

  return {
    destroy(): void {
      node.removeEventListener('keydown', onKeydown);
      untag();
    },
  };
}

/**
 * Routes the props bits-ui hands its `child` snippet onto the caller's own
 * trigger element.
 *
 * `DropdownMenu.Trigger` requires those props on the element that becomes the
 * trigger, but the four `trigger` snippets in the app are zero-argument
 * snippets that render their own `<button>`, and the swaps are forbidden to
 * change them. Measured against bits-ui 2.19.2: the props are plain attributes,
 * four event handlers under `on*` keys, and one symbol-keyed Svelte attachment
 * (`Object.getOwnPropertySymbols` reports `Symbol(@attach)`) that registers the
 * element as the floating anchor. Applying all three kinds to the caller's
 * element makes that button the real trigger, so no non-interactive wrapper
 * carries the interaction and the nested-interactive defect is gone.
 */
export function menuTriggerHost(
  node: HTMLElement,
  props: Record<string, unknown>,
): { update: (next: Record<string, unknown>) => void; destroy: () => void } {
  const candidate = (node.querySelector('button, [role="button"]') ??
    node.firstElementChild) as HTMLElement | null;

  if (!candidate) {
    return { update: (): void => undefined, destroy: (): void => undefined };
  }

  const target: HTMLElement = candidate;

  let cleanups: Array<() => void> = [];
  let listeners: Array<{ type: string; handler: EventListener }> = [];
  const owned = new Set<string>();

  function release(): void {
    for (const cleanup of cleanups) cleanup();
    cleanups = [];
    for (const { type, handler } of listeners) target.removeEventListener(type, handler);
    listeners = [];
  }

  function apply(next: Record<string, unknown>): void {
    release();
    const all = next as Record<string | symbol, unknown>;

    for (const symbol of Object.getOwnPropertySymbols(all)) {
      const candidate = all[symbol];
      if (typeof candidate !== 'function') continue;
      const cleanup = (candidate as (element: Element) => unknown)(target);
      if (typeof cleanup === 'function') cleanups.push(cleanup as () => void);
    }

    for (const [key, value] of Object.entries(next)) {
      if (value === undefined || value === null) continue;
      if (typeof value === 'function') {
        const type = key.slice(2);
        const handler = value as EventListener;
        target.addEventListener(type, handler);
        listeners.push({ type, handler });
        continue;
      }
      target.setAttribute(key, String(value));
      owned.add(key);
    }
  }

  apply(props);

  return {
    update: apply,
    destroy(): void {
      release();
      for (const key of owned) target.removeAttribute(key);
    },
  };
}
