export type BreadcrumbType = "navigation" | "action" | "error";

export interface BreadcrumbEntry {
  id: string;
  sessionId: string;
  timestamp: string;
  type: BreadcrumbType;
  label: string;
  data?: Record<string, unknown>;
}

export const BREADCRUMB_LABELS = {
  OPEN_READER: "open_reader",
  OPEN_LIBRARY: "open_library",
  OPEN_SETTINGS: "open_settings",
  SYNC_TRIGGER: "sync_trigger",
  IMPORT_START: "import_start",
  IMPORT_FAIL: "import_fail",
  HIGHLIGHT_CREATE: "highlight_create",
} as const;
