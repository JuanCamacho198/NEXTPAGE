export function canScrollElementInDirection(element: HTMLElement, delta: number): boolean {
  if (element.scrollHeight <= element.clientHeight + 1) {
    return false;
  }

  if (delta < 0) {
    return element.scrollTop > 0;
  }

  return element.scrollTop + element.clientHeight < element.scrollHeight - 1;
}

export function resolveEpubIframeScrollHost(epubContainer: HTMLElement): HTMLElement | null {
  const iframe = epubContainer.querySelector('iframe');
  if (!(iframe instanceof HTMLIFrameElement)) {
    return null;
  }

  try {
    const frameDocument = iframe.contentDocument;
    if (!frameDocument) {
      return null;
    }

    const scrollingElement = frameDocument.scrollingElement;
    if (scrollingElement instanceof HTMLElement) {
      return scrollingElement;
    }

    if (frameDocument.documentElement instanceof HTMLElement) {
      return frameDocument.documentElement;
    }

    if (frameDocument.body instanceof HTMLElement) {
      return frameDocument.body;
    }
  } catch {
    return null;
  }

  return null;
}

/**
 * Scrolls the EPUB content by the given number of pixels.
 * Tries the iframe scroll host first, then the epub container, then the window.
 */
export function scrollByVerticalStep(epubContainer: HTMLElement, deltaPx: number): void {
  const iframeScrollHost = resolveEpubIframeScrollHost(epubContainer);
  if (iframeScrollHost && canScrollElementInDirection(iframeScrollHost, deltaPx)) {
    iframeScrollHost.scrollBy({ top: deltaPx, behavior: 'auto' });
    return;
  }

  if (canScrollElementInDirection(epubContainer, deltaPx)) {
    epubContainer.scrollBy({ top: deltaPx, behavior: 'auto' });
    return;
  }

  if (typeof window !== 'undefined') {
    window.scrollBy({ top: deltaPx, behavior: 'auto' });
  }
}
