/**
 * jsdom harness for the desktop test environment.
 *
 * jsdom has no layout engine: every `getBoundingClientRect()` is all zeros, and
 * the observer/scroll surfaces the overlay primitives (bits-ui, floating-ui)
 * read do not exist. Without this harness their positioning and focus
 * assertions are simultaneously vacuous (nothing is ever delivered, so nothing
 * is asserted) and brittle (a missing global throws where hand-rolled markup
 * did not).
 *
 * Contract: nothing is delivered spontaneously. `flushResizeObservers()` is the
 * only delivery point, because jsdom has no layout to observe.
 */

const liveObservers = new Set<RegistryResizeObserver>();

function buildEntry(element: Element): ResizeObserverEntry {
  const rect = element.getBoundingClientRect();
  const contentRect = new DOMRectReadOnly(rect.x, rect.y, rect.width, rect.height);
  const boxSize = [{ inlineSize: rect.width, blockSize: rect.height }];
  return {
    target: element,
    contentRect,
    borderBoxSize: boxSize,
    contentBoxSize: boxSize,
    devicePixelContentBoxSize: boxSize,
  };
}

class RegistryResizeObserver implements ResizeObserver {
  readonly #callback: ResizeObserverCallback;
  readonly #targets = new Set<Element>();

  constructor(callback: ResizeObserverCallback) {
    this.#callback = callback;
  }

  observe(target: Element): void {
    this.#targets.add(target);
    liveObservers.add(this);
  }

  unobserve(target: Element): void {
    this.#targets.delete(target);
  }

  disconnect(): void {
    this.#targets.clear();
    liveObservers.delete(this);
  }

  takeRecords(): ResizeObserverEntry[] {
    return [];
  }

  /** Called by `flushResizeObservers()` only — never by the environment. */
  deliver(): void {
    if (this.#targets.size === 0) return;
    const entries = [...this.#targets].map(buildEntry);
    this.#callback(entries, this);
  }
}

class HarnessPointerEvent extends MouseEvent {
  readonly pointerId: number;
  readonly pointerType: string;
  readonly isPrimary: boolean;
  readonly width: number;
  readonly height: number;
  readonly pressure: number;

  constructor(type: string, init: PointerEventInit = {}) {
    super(type, init);
    this.pointerId = init.pointerId ?? 0;
    this.pointerType = init.pointerType ?? '';
    this.isPrimary = init.isPrimary ?? false;
    this.width = init.width ?? 1;
    this.height = init.height ?? 1;
    this.pressure = init.pressure ?? 0;
  }
}

function installResizeObserver(): void {
  // Unconditional on purpose: a noop or foreign implementation would make the
  // flush hook silently vacuous, which is the exact failure this slice removes.
  globalThis.ResizeObserver = RegistryResizeObserver as unknown as typeof ResizeObserver;
}

function installPointerEvent(): void {
  if (typeof globalThis.PointerEvent !== 'undefined') return;
  globalThis.PointerEvent = HarnessPointerEvent as unknown as typeof PointerEvent;
}

function installScrollStubs(): void {
  if (typeof Element === 'undefined') return;
  const prototype = Element.prototype as unknown as Record<string, unknown>;
  for (const name of ['scrollIntoView', 'scrollTo', 'scrollBy']) {
    if (typeof prototype[name] !== 'function') {
      prototype[name] = (): void => undefined;
    }
  }
}

/** Delivers current geometry to every live observation. Only delivery point. */
export function flushResizeObservers(): void {
  for (const observer of [...liveObservers]) {
    observer.deliver();
  }
}

export function installJsdomHarness(): void {
  installResizeObserver();
  installPointerEvent();
  installScrollStubs();
}

export interface StubbedRect {
  left?: number;
  top?: number;
  width?: number;
  height?: number;
}

/**
 * Gives an element a geometry instead of jsdom's all-zero one.
 *
 * Three surfaces are stubbed because three different readers consume them:
 * floating-ui's `getDimensions()` reads `offsetWidth`/`offsetHeight` (falling
 * back from a computed `width: auto`) and `getInnerBoundingClientRect()` reads
 * `clientWidth`/`clientHeight`, while its rect math reads
 * `getBoundingClientRect()`. Stubbing only the client rect would still position
 * everything at zero and pass silently.
 *
 * `getClientRects()` is stubbed too, and it is not optional for anything
 * focus-related: `tabbable`'s display gate reports an element as hidden while
 * `getClientRects()` is empty (`!node.getClientRects().length`), which is how
 * jsdom describes *every* element with no layout engine. Measured: without this
 * stub `isTabbable()`/`isFocusable()` return `false` for a plain
 * `<button>`, so bits-ui's close-auto-focus skips its focus restore and no test
 * can observe it.
 */
export function stubElementRect(element: Element, rect: StubbedRect): void {
  const left = rect.left ?? 0;
  const top = rect.top ?? 0;
  const width = rect.width ?? 0;
  const height = rect.height ?? 0;

  const domRect = (): DOMRect => new DOMRect(left, top, width, height);
  element.getBoundingClientRect = domRect;
  element.getClientRects = (): DOMRectList => [domRect()] as unknown as DOMRectList;

  if (typeof HTMLElement === 'undefined' || !(element instanceof HTMLElement)) return;

  const boxes: ReadonlyArray<readonly [string, number]> = [
    ['offsetWidth', width],
    ['offsetHeight', height],
    ['clientWidth', width],
    ['clientHeight', height],
  ];
  for (const [name, value] of boxes) {
    Object.defineProperty(element, name, { value, configurable: true });
  }
}
