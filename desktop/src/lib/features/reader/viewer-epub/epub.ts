/* eslint-disable @typescript-eslint/no-explicit-any */
import ePub, { Book, Rendition, NavItem } from "epubjs";

export interface EpubChapter {
  id: string;
  href: string;
  label: string;
  subitems?: EpubChapter[];
}

export interface EpubMetadata {
  title: string;
  author: string;
  language?: string;
  publisher?: string;
  cover?: string;
}

export interface EpubLocation {
  index: number;
  cfi: string;
  href: string;
}

export class EpubReaderService {
  private book: Book | null = null;
  private rendition: Rendition | null = null;
  private container: HTMLElement | null = null;

  async loadFromArrayBuffer(buffer: ArrayBuffer): Promise<EpubMetadata> {
    this.cleanup();

    this.book = ePub(buffer) as any;
    await (this.book as any).ready;

    const pkg = (this.book as any).package as any;
    const metadata = pkg?.metadata || {};

    return {
      title: metadata.title || "Unknown Title",
      author: metadata.creator || "Unknown Author",
      language: metadata.language,
      publisher: metadata.publisher,
      cover: await this.getCoverUrl(),
    };
  }

  private async getCoverUrl(): Promise<string | undefined> {
    if (!this.book) return undefined;

    try {
      const cover = await (this.book as any).coverUrl();
      return cover || undefined;
    } catch {
      return undefined;
    }
  }

  async getToc(): Promise<EpubChapter[]> {
    if (!this.book) {
      throw new Error("Book not loaded");
    }

    const navigation = await (this.book as any).loaded.navigation;
    return this.mapNavItems(navigation.toc);
  }

  private mapNavItems(items: NavItem[]): EpubChapter[] {
    return items.map((item) => ({
      id: item.id,
      href: item.href,
      label: item.label,
      subitems: item.subitems ? this.mapNavItems(item.subitems) : undefined,
    }));
  }

  render(container: HTMLElement): void {
    if (!this.book) {
      throw new Error("Book not loaded");
    }

    this.container = container;
    container.innerHTML = "";

    this.rendition = this.book.renderTo(container, {
      width: "100%",
      height: "100%",
      spread: "none",
    }) as any;
  }

  async display(cfi?: string): Promise<void> {
    if (!this.rendition) {
      throw new Error("Rendition not initialized");
    }

    await this.rendition.display(cfi);
  }

  getCurrentLocation(): EpubLocation | null {
    if (!this.rendition) return null;

    const location = (this.rendition as any).location as any;
    if (!location) return null;

    return {
      index: location.index ?? 0,
      cfi: location.cfi ?? "",
      href: location.href ?? "",
    };
  }

  async goToCfi(cfi: string): Promise<void> {
    if (!this.rendition) {
      throw new Error("Rendition not initialized");
    }

    await this.rendition.display(cfi);
  }

  async goToChapter(index: number): Promise<void> {
    if (!this.rendition || !this.book) {
      throw new Error("Book or rendition not initialized");
    }

    const spineItems = await (this.book as any).loaded.spine as any[];
    const spineItem = spineItems[index];
    if (spineItem) {
      await this.rendition.display(spineItem.href);
    }
  }

  onLocationChange(callback: (location: EpubLocation) => void): void {
    if (!this.rendition) return;

    (this.rendition as any).on("locationChanged", (loc: any) => {
      callback({
        index: loc?.start?.index ?? 0,
        cfi: loc?.start?.cfi ?? "",
        href: loc?.start?.href ?? "",
      });
    });
  }

  onRendered(callback: () => void): void {
    if (!this.rendition) return;

    (this.rendition as any).on("rendered", callback);
  }

  getRendition(): Rendition | null {
    return this.rendition;
  }

  cleanup(): void {
    if (this.rendition) {
      this.rendition.destroy();
      this.rendition = null;
    }

    if (this.book) {
      this.book.destroy();
      this.book = null;
    }

    if (this.container) {
      this.container.innerHTML = "";
      this.container = null;
    }
  }
}

export function createEpubReader(): EpubReaderService {
  return new EpubReaderService();
}