import type { ViewerPort, UpdateHighlightInput } from '$lib/shared/ports/ViewerPort';
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
import * as tauriClient from '$lib/shared/api/tauriClient';

export class TauriViewerAdapter implements ViewerPort {
  listHighlights(bookId?: string): Promise<HighlightDto[]> {
    return tauriClient.listHighlights(bookId);
  }

  saveHighlight(input: SaveHighlightInput): Promise<void> {
    return tauriClient.saveHighlight(input);
  }

  deleteHighlight(id: string): Promise<void> {
    return tauriClient.deleteHighlight(id);
  }

  updateHighlight(input: UpdateHighlightInput): Promise<HighlightDto> {
    return tauriClient.updateHighlight(input);
  }

  listTags(): Promise<TagDto[]> {
    return tauriClient.listTags();
  }

  listTagsForHighlight(highlightId: string): Promise<TagDto[]> {
    return tauriClient.listTagsForHighlight(highlightId);
  }

  listBookmarks(bookId?: string): Promise<BookmarkDto[]> {
    return tauriClient.listBookmarks(bookId);
  }

  saveBookmark(input: SaveBookmarkInput): Promise<void> {
    return tauriClient.saveBookmark(input);
  }

  deleteBookmark(id: string): Promise<void> {
    return tauriClient.deleteBookmark(id);
  }

  getProgress(bookId: string): Promise<ReadingProgressDto | null> {
    return tauriClient.getProgress(bookId);
  }

  saveProgress(payload: SaveProgressInput): Promise<void> {
    return tauriClient.saveProgress(payload);
  }

  searchBookText(payload: SearchBookTextInput): Promise<SearchBookTextResponse> {
    return tauriClient.searchBookText(payload);
  }

  upsertRemoteHighlights(rows: RemoteHighlightRow[]): Promise<UpsertRemoteSummary> {
    return tauriClient.upsertRemoteHighlights(rows);
  }

  upsertRemoteReadingSessions(rows: RemoteReadingSessionRow[]): Promise<number> {
    return tauriClient.upsertRemoteReadingSessions(rows);
  }
}
