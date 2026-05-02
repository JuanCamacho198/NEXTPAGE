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
};

export type CommandErrorDto = {
  code: string;
  message: string;
  recoverable: boolean;
};