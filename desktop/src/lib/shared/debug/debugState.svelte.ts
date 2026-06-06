type DebugReaderInfo = {
  format: "pdf" | "epub" | null;
  isTocOpen: boolean;
  isSearchOpen: boolean;
  isFullscreen: boolean;
  pageInfo: string;
  scale: number;
} | null;

type DebugRect = {
  top: number;
  left: number;
  width: number;
  height: number;
};

type DebugSelectionInfo = {
  text: string;
  source: "pdf" | "epub";
  rectCount: number;
  firstRect: DebugRect;
} | null;

const BREAKPOINTS = [
  { name: "sm", min: 0, max: 639 },
  { name: "md", min: 640, max: 767 },
  { name: "lg", min: 768, max: 1023 },
  { name: "xl", min: 1024, max: 1279 },
  { name: "2xl", min: 1280, max: Infinity },
] as const;

class DebugState {
  enabled = $state(false);
  currentRoute = $state("");
  viewportWidth = $state(0);
  viewportHeight = $state(0);
  readerInfo: DebugReaderInfo = $state(null);
  selection: DebugSelectionInfo = $state(null);

  get breakpoint(): string {
    const bp = BREAKPOINTS.find(
      (b) => this.viewportWidth >= b.min && this.viewportWidth <= b.max,
    );
    return bp?.name ?? "lg";
  }

  updateViewport(): void {
    this.viewportWidth = window.innerWidth;
    this.viewportHeight = window.innerHeight;
  }

  getSnapshot(): string {
    const lines: string[] = [
      `Route: ${this.currentRoute || "—"}`,
      `Viewport: ${this.viewportWidth}x${this.viewportHeight} (${this.breakpoint})`,
    ];
    if (this.readerInfo) {
      lines.push(
        `Reader: ${this.readerInfo.format ?? "—"} | ${this.readerInfo.pageInfo} | fullscreen:${this.readerInfo.isFullscreen}`,
      );
    }
    if (this.selection) {
      lines.push(
        `Selection: ${this.selection.source} | "${this.selection.text.slice(0, 80)}"`,
      );
    }
    return lines.join("\n");
  }

  async copySnapshot(): Promise<void> {
    const text = this.getSnapshot();
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      console.warn("Failed to copy debug snapshot");
    }
  }

  resetSelection(): void {
    this.selection = null;
  }
}

export const debugState = new DebugState();
export type { DebugReaderInfo, DebugSelectionInfo };
