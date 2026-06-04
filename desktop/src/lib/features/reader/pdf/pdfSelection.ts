import type { SelectionOverlayRect } from "./pdfState.svelte";
import {
  SELECTION_X_PADDING_PX,
  SELECTION_Y_INSET_PX,
  SELECTION_LINE_TOLERANCE_PX,
  clampSelectionPoint,
} from "./pdfState.svelte";

export const HIGHLIGHT_COLORS = [
  { hex: "#FACC15", label: "yellow" },
  { hex: "#4ADE80", label: "green" },
  { hex: "#60A5FA", label: "blue" },
  { hex: "#C084FC", label: "purple" },
  { hex: "#FB923C", label: "orange" },
] as const;

export function buildSelectionOverlayRects(range: Range, containerRect: DOMRect): SelectionOverlayRect[] {
  const rawRects = Array.from(range.getClientRects()).filter(
    (rect) => rect.width > 0 && rect.height > 0,
  );
  if (rawRects.length === 0) {
    const fallbackRect = range.getBoundingClientRect();
    if (fallbackRect.width <= 0 || fallbackRect.height <= 0) {
      return [];
    }
    rawRects.push(fallbackRect);
  }

  const unscaledWidth = containerRect.width;
  const unscaledHeight = containerRect.height;

  const normalizedRects = rawRects
    .map((rect) => {
      const left = clampSelectionPoint(rect.left - containerRect.left, 0, unscaledWidth);
      const right = clampSelectionPoint(rect.right - containerRect.left, 0, unscaledWidth);
      const top = clampSelectionPoint(rect.top - containerRect.top, 0, unscaledHeight);
      const bottom = clampSelectionPoint(rect.bottom - containerRect.top, 0, unscaledHeight);
      return { left, right, top, bottom };
    })
    .filter((rect) => rect.right - rect.left > 0 && rect.bottom - rect.top > 0)
    .sort((left, right) => {
      if (Math.abs(left.top - right.top) <= SELECTION_LINE_TOLERANCE_PX) {
        return left.left - right.left;
      }
      return left.top - right.top;
    });

  const mergedLines: Array<{
    left: number;
    right: number;
    top: number;
    bottom: number;
  }> = [];

  normalizedRects.forEach((rect) => {
    const previous = mergedLines[mergedLines.length - 1];
    if (!previous) {
      mergedLines.push({ ...rect });
      return;
    }

    const sameLine =
      Math.abs(rect.top - previous.top) <= SELECTION_LINE_TOLERANCE_PX &&
      Math.abs(rect.bottom - previous.bottom) <=
        Math.max(SELECTION_LINE_TOLERANCE_PX, rect.bottom - rect.top);

    if (!sameLine) {
      mergedLines.push({ ...rect });
      return;
    }

    previous.left = Math.min(previous.left, rect.left);
    previous.right = Math.max(previous.right, rect.right);
    previous.top = Math.min(previous.top, rect.top);
    previous.bottom = Math.max(previous.bottom, rect.bottom);
  });

  return mergedLines.map((rect) => {
    const left = clampSelectionPoint(rect.left - SELECTION_X_PADDING_PX, 0, unscaledWidth);
    const right = clampSelectionPoint(rect.right + SELECTION_X_PADDING_PX, 0, unscaledWidth);
    const top = clampSelectionPoint(rect.top + SELECTION_Y_INSET_PX, 0, unscaledHeight);
    const bottom = clampSelectionPoint(rect.bottom - SELECTION_Y_INSET_PX, top, unscaledHeight);

    return {
      left,
      top,
      width: Math.max(1, right - left),
      height: Math.max(1, bottom - top),
    };
  });
}

export function readProgressPercent(page: number, total: number): number {
  if (total <= 0) return 0;
  return Math.max(0, Math.min(100, (page / total) * 100));
}

export function parseLocatorPage(locator: string | null | undefined): number | null {
  if (!locator) return null;
  const match = locator.match(/(\d+)$/);
  if (!match) return null;
  const parsed = Number.parseInt(match[1], 10);
  if (!Number.isFinite(parsed) || parsed <= 0) return null;
  return parsed;
}

export type ScrollAnchor = {
  previousScrollTop: number;
  previousHeight: number;
  viewportHeight: number;
};

export function captureScrollAnchor(host: HTMLElement | null): ScrollAnchor | null {
  if (!host) return null;
  return {
    previousScrollTop: host.scrollTop,
    previousHeight: host.scrollHeight,
    viewportHeight: host.clientHeight,
  };
}

export function restoreScrollAnchor(
  anchor: ScrollAnchor | null,
  host: HTMLElement | null,
): void {
  if (!anchor || !host) return;
  const { previousScrollTop, previousHeight, viewportHeight } = anchor;
  const previousCenter = previousScrollTop + viewportHeight / 2;
  const nextHeight = host.scrollHeight;
  if (previousHeight <= 0 || nextHeight <= 0) return;
  const centerRatio = previousCenter / previousHeight;
  const nextCenter = centerRatio * nextHeight;
  const nextScrollTop = Math.max(0, nextCenter - host.clientHeight / 2);
  host.scrollTop = nextScrollTop;
}

export function canScrollElementInDirection(
  element: HTMLElement,
  delta: number,
): boolean {
  if (element.scrollHeight <= element.clientHeight + 1) return false;
  if (delta < 0) return element.scrollTop > 0;
  return element.scrollTop + element.clientHeight < element.scrollHeight - 1;
}

type RefLike = { num: number; gen: number };

export function isRefLike(value: unknown): value is RefLike {
  if (!value || typeof value !== "object") return false;
  const candidate = value as { num?: unknown; gen?: unknown };
  return typeof candidate.num === "number" && typeof candidate.gen === "number";
}

export function toOutlineTitle(
  title: unknown,
  fallbackLabel: string,
): string {
  if (typeof title !== "string") return fallbackLabel;
  const normalized = title.trim();
  return normalized.length > 0 ? normalized : fallbackLabel;
}

export function normalizeOutlineItems(
  items: unknown[],
  fallbackLabel: string,
  parentId = "outline",
): Array<{
  id: string;
  title: string;
  dest: string | unknown[] | null;
  items: Array<{
    id: string;
    title: string;
    dest: string | unknown[] | null;
    items: Array<{
      id: string;
      title: string;
      dest: string | unknown[] | null;
      items: never[];
    }>;
  }>;
}> {
  const normalized: Array<{
    id: string;
    title: string;
    dest: string | unknown[] | null;
    items: Array<Record<string, unknown>>;
  }> = [];

  items.forEach((rawItem, index) => {
    if (!rawItem || typeof rawItem !== "object") return;
    const item = rawItem as { title?: unknown; dest?: unknown; items?: unknown };
    const children = Array.isArray(item.items)
      ? normalizeOutlineItems(item.items, fallbackLabel, `${parentId}-${index}`)
      : [];
    const destination =
      typeof item.dest === "string" || Array.isArray(item.dest) ? item.dest : null;
    normalized.push({
      id: `${parentId}-${index}`,
      title: toOutlineTitle(item.title, fallbackLabel),
      dest: destination,
      items: children,
    });
  });

  return normalized;
}

export function flattenOutline<T extends { items: T[] }>(
  items: T[],
  depth = 0,
): Array<{ item: T; depth: number }> {
  const flattened: Array<{ item: T; depth: number }> = [];
  for (const item of items) {
    flattened.push({ item, depth });
    if (item.items.length > 0) {
      flattened.push(...flattenOutline(item.items, depth + 1));
    }
  }
  return flattened;
}
