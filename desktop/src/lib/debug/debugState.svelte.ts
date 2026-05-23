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

class DebugState {
  enabled = $state(false);
  currentRoute = $state("");
  readerInfo: DebugReaderInfo = $state(null);
  selection: DebugSelectionInfo = $state(null);

  resetSelection() {
    this.selection = null;
  }
}

export const debugState = new DebugState();
export type { DebugReaderInfo, DebugSelectionInfo };
