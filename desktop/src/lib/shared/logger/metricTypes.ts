export type MetricName =
  | "page_load"
  | "reader_open"
  | "import_start"
  | "import_complete"
  | "sync_start"
  | "sync_complete";

export interface MetricEvent {
  id: string;
  sessionId: string;
  timestamp: string;
  name: MetricName;
  durationMs?: number;
  feature?: string;
  count: number;
  success: boolean;
  errorCode?: string;
}

export const METRIC_NAMES = {
  PAGE_LOAD: "page_load",
  READER_OPEN: "reader_open",
  IMPORT_START: "import_start",
  IMPORT_COMPLETE: "import_complete",
  SYNC_START: "sync_start",
  SYNC_COMPLETE: "sync_complete",
} as const;
