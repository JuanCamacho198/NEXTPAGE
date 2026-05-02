export type ErrorSeverity = "low" | "medium" | "high" | "critical";

export type ErrorCategory =
  | "runtime"
  | "promise_rejection"
  | "command"
  | "network"
  | "parsing"
  | "validation"
  | "unknown"
  | "alert";

export type ErrorSource = "app_shell" | "reader" | "library" | "settings" | "import" | "sync";

export interface ErrorEvent {
  timestamp: string;
  severity: ErrorSeverity;
  category: ErrorCategory;
  code: string;
  message: string;
  context: Record<string, unknown>;
  correlationId: string;
  source: ErrorSource;
  recoverable: boolean;
}

export interface ErrorEventDto {
  timestamp: string;
  severity: ErrorSeverity;
  category: ErrorCategory;
  code: string;
  message: string;
  context: Record<string, unknown>;
  correlationId: string;
  source: ErrorSource;
  recoverable: boolean;
}

export const createErrorEvent = (
  params: Omit<ErrorEvent, "timestamp" | "correlationId"> & {
    correlationId?: string;
  }
): ErrorEvent => {
  return {
    timestamp: new Date().toISOString(),
    correlationId: params.correlationId ?? crypto.randomUUID(),
    ...params,
  };
};