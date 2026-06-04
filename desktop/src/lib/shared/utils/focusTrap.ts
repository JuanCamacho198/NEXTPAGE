/**
 * Focus trap utility for modals, dialogs, and side panels.
 * 
 * Traps keyboard focus within a container element so users can't tab out.
 * Focuses the first focusable element on activation and restores focus
 * to the previously focused element on deactivation.
 *
 * Usage:
 *   const trap = createFocusTrap(containerEl, { onDeactivate });
 *   trap.activate();
 *   // later...
 *   trap.deactivate();
 */

const FOCUSABLE_SELECTOR = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled])",
  "textarea:not([disabled])",
  "select:not([disabled])",
  "[tabindex]:not([tabindex=\"-1\"])",
  "details > summary:first-of-type",
  "[contenteditable]:not([contenteditable=\"false\"])",
].join(", ");

export interface FocusTrapOptions {
  /** Called when the trap is deactivated */
  onDeactivate?: () => void;
  /** Element to focus initially. Defaults to first focusable child */
  initialFocusEl?: HTMLElement | null;
  /** Whether to return focus to the previously active element on deactivation. Default: true */
  restoreFocus?: boolean;
}

export interface FocusTrap {
  activate: () => void;
  deactivate: () => void;
  /** Update the container reference (useful if the element re-renders) */
  updateContainer: (el: HTMLElement) => void;
}

export function createFocusTrap(
  container: HTMLElement,
  options: FocusTrapOptions = {},
): FocusTrap {
  let activeElement = container;
  let previouslyFocused: HTMLElement | null = null;
  const { onDeactivate, initialFocusEl, restoreFocus = true } = options;

  function getFocusableElements(): HTMLElement[] {
    return Array.from(activeElement.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
  }

  function getFirstFocusable(): HTMLElement | null {
    const elements = getFocusableElements();
    return elements[0] ?? null;
  }

  function getLastFocusable(): HTMLElement | null {
    const elements = getFocusableElements();
    return elements[elements.length - 1] ?? null;
  }

  function focusFirst(): void {
    const target = initialFocusEl ?? getFirstFocusable();
    if (target) {
      target.focus();
    } else {
      // If no focusable elements, focus the container itself
      activeElement.setAttribute("tabindex", "-1");
      activeElement.focus();
    }
  }

  function handleKeyDown(event: KeyboardEvent): void {
    if (event.key !== "Tab") return;

    const firstEl = getFirstFocusable();
    const lastEl = getLastFocusable();

    if (!firstEl || !lastEl) {
      // No focusable elements — trap would break, so we prevent tab
      event.preventDefault();
      return;
    }

    if (event.shiftKey) {
      // Shift+Tab: go backwards, wrap to last if on first
      if (document.activeElement === firstEl) {
        event.preventDefault();
        lastEl.focus();
      }
    } else {
      // Tab: go forwards, wrap to first if on last
      if (document.activeElement === lastEl) {
        event.preventDefault();
        firstEl.focus();
      }
    }
  }

  function handleFocusOut(event: FocusEvent): void {
    // If focus moves outside the container, force it back
    const relatedTarget = event.relatedTarget as Node | null;
    if (relatedTarget && !activeElement.contains(relatedTarget)) {
      focusFirst();
    }
  }

  return {
    activate(): void {
      previouslyFocused = document.activeElement as HTMLElement | null;
      activeElement.addEventListener("keydown", handleKeyDown);
      activeElement.addEventListener("focusout", handleFocusOut);
      focusFirst();
    },

    deactivate(): void {
      activeElement.removeEventListener("keydown", handleKeyDown);
      activeElement.removeEventListener("focusout", handleFocusOut);

      if (restoreFocus && previouslyFocused && previouslyFocused !== document.body) {
        previouslyFocused.focus();
      }

      onDeactivate?.();
    },

    updateContainer(el: HTMLElement): void {
      activeElement = el;
    },
  };
}
