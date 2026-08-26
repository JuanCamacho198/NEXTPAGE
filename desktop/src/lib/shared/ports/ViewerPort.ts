import type {
  BookmarkDto,
  HighlightDto,
  ReadingProgressDto,
  SaveBookmarkInput,
  SaveHighlightInput,
  SaveProgressInput,
  SearchBookTextInput,
  SearchBookTextResponse,
  TagDto,
  RemoteHighlightRow,
  RemoteReadingSessionRow,
  UpsertRemoteSummary
} from '$lib/shared/types';

export type UpdateHighlightInput = {
  id: string;
  color?: string;
  note?: string;
  page?: number;
  pageNumber?: number;
};

export interface ViewerPort {
  listHighlights(bookId?: string): Promise<HighlightDto[]>;
  saveHighlight(input: SaveHighlightInput): Promise<void>;
  deleteHighlight(id: string): Promise<void>;
  updateHighlight(input: UpdateHighlightInput): Promise<HighlightDto>;
  listTags(): Promise<TagDto[]>;
  listTagsForHighlight(highlightId: string): Promise<TagDto[]>;
  listBookmarks(bookId?: string): Promise<BookmarkDto[]>;
  saveBookmark(input: SaveBookmarkInput): Promise<void>;
  deleteBookmark(id: string): Promise<void>;
  getProgress(bookId: string): Promise<ReadingProgressDto | null>;
  saveProgress(payload: SaveProgressInput): Promise<void>;
  searchBookText(payload: SearchBookTextInput): Promise<SearchBookTextResponse>;
  upsertRemoteHighlights(rows: RemoteHighlightRow[]): Promise<UpsertRemoteSummary>;
  upsertRemoteReadingSessions(rows: RemoteReadingSessionRow[]): Promise<number>;
}
