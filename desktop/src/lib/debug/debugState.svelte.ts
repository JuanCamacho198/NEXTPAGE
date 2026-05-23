type DebugReaderInfo = {
  format: "pdf" | "epub" | null;
  isTocOpen: boolean;
  isSearchOpen: boolean;
  isFullscreen: boolean;
  pageInfo: string;
  scale: number;
} | null;

type DebugSelectionInfo = {
  text: string;
  source: "pdf" | "epub";
  rectCount: number;
} | null;

class DebugState {
  enabled = $state(false);
  currentRoute = $state("");
  readerInfo: DebugReaderInfo = $state(null);
  selection: DebugSelectionInfo = $state(null);
}

export const debugState = new DebugState();
export type { DebugReaderInfo, DebugSelectionInfo };
