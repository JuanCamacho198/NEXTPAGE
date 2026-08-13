export type SearchBookTextInput = {
  bookId: string;
  query: string;
  page: number;
  pageSize: number;
};

export type SearchResult = {
  chunkId: string;
  bookId: string;
  locator: string;
  snippet: string;
  rank: number;
};

export type SearchBookTextResponse = {
  items: SearchResult[];
  total: number;
  page: number;
  pageSize: number;
};

export type SearchNavigationTarget = {
  resultId: string;
  locator: string;
  snippet: string;
};

export type PdfOutlineItem = {
  id: string;
  title: string;
  dest: string | unknown[] | null;
  items: PdfOutlineItem[];
};

export type ReadingSessionInput = {
  bookId: string;
  startedAt: string;
  endedAt?: string;
  durationSeconds: number;
  startPercentage?: number;
  endPercentage?: number;
  userId: string;
};

/**
 * DTO returned by the Rust `saveReadingSession` command (serde camelCase).
 * The deterministic id is computed in Rust — the frontend cannot know it — so
 * this DTO is the single source for the READING_SESSION outbox payload.
 */
export type ReadingSessionSavedDto = {
  id: string;
  durationMinutes: number;
  date: string;
  updatedAtEpochMillis: number;
};

export type CommandErrorDto = {
  code: string;
  message: string;
  recoverable: boolean;
};

export type SelectionRect = {
  x: number;
  y: number;
  width: number;
  height: number;
};

export type SelectionEvent = {
  text: string;
  rect: DOMRect;
  range: Range;
  containerRect: DOMRect;
};
