export type BreadcrumbType = 'navigation' | 'action' | 'error';

export interface BreadcrumbEntry {
  id: string;
  sessionId: string;
  timestamp: string;
  type: BreadcrumbType;
  label: string;
  data?: Record<string, unknown>;
}

export const BREADCRUMB_LABELS = {
  OPEN_READER: 'open_reader',
  OPEN_LIBRARY: 'open_library',
  OPEN_SETTINGS: 'open_settings',
  SYNC_TRIGGER: 'sync_trigger',
  IMPORT_START: 'import_start',
  IMPORT_FAIL: 'import_fail',
  HIGHLIGHT_CREATE: 'highlight_create',
  HIGHLIGHT_UPDATE: 'highlight_update',
  HIGHLIGHT_NOTE: 'highlight_note',
  HIGHLIGHT_DELETE: 'highlight_delete',
  BOOKMARK_ADD: 'bookmark_add',
  BOOKMARK_REMOVE: 'bookmark_remove',
  CHAPTER_CHANGE: 'chapter_change',
  SYNC_FAIL: 'sync_fail',
} as const;
