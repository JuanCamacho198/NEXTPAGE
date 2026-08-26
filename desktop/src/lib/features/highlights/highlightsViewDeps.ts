import type { ViewerPort } from '$lib/shared/ports';
import { TauriViewerAdapter } from '$lib/shared/ports';

export type HighlightsViewDeps = {
  listHighlights: ViewerPort['listHighlights'];
  deleteHighlight: ViewerPort['deleteHighlight'];
  upsertRemoteHighlights: ViewerPort['upsertRemoteHighlights'];
  listTags: ViewerPort['listTags'];
  listTagsForHighlight: ViewerPort['listTagsForHighlight'];
};

export function createHighlightsViewDeps(viewerPort: ViewerPort = new TauriViewerAdapter()): HighlightsViewDeps {
  return {
    listHighlights: (bookId?: string) => viewerPort.listHighlights(bookId),
    deleteHighlight: (id: string) => viewerPort.deleteHighlight(id),
    upsertRemoteHighlights: (rows) => viewerPort.upsertRemoteHighlights(rows),
    listTags: () => viewerPort.listTags(),
    listTagsForHighlight: (highlightId: string) => viewerPort.listTagsForHighlight(highlightId),
  };
}
