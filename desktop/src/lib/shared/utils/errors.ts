import { createErrorEvent, type ErrorCategory, type ErrorSource } from "../events/ErrorEvent";
import { classifyError } from "../events/classifyError";
import { errorState } from "../stores/errorState";
import { logger } from "../logger";

export class AppError extends Error {
  constructor(
    public message: string,
    public code: string = "UNKNOWN_ERROR",
    public source: ErrorSource = "app_shell",
    public category: ErrorCategory = "runtime",
    public context: Record<string, unknown> = {},
    public recoverable: boolean = true
  ) {
    super(message);
    this.name = "AppError";
  }
}

export class ReaderError extends AppError {
  constructor(message: string, code: string = "READER_ERROR", context: Record<string, unknown> = {}) {
    super(message, code, "reader", "runtime", context, true);
    this.name = "ReaderError";
  }
}

export class FileSystemError extends AppError {
  constructor(message: string, code: string = "FS_ERROR", context: Record<string, unknown> = {}) {
    super(message, code, "app_shell", "command", context, true);
    this.name = "FileSystemError";
  }
}

/**
 * Centralized error handler for the application.
 * Processes errors, logs them, and updates the global error state for UI feedback.
 */
export const handleError = (error: unknown, source: ErrorSource = "app_shell") => {
  let appError: AppError;

  if (error instanceof AppError) {
    appError = error;
  } else if (error instanceof Error) {
    const classification = classifyError(error);
    appError = new AppError(
      error.message,
      (error as any).code || "UNEXPECTED_ERROR",
      source,
      "runtime",
      { stack: error.stack },
      classification.recoverable
    );
  } else {
    appError = new AppError(
      String(error),
      "UNKNOWN_ERROR",
      source,
      "unknown",
      { originalError: error },
      false
    );
  }

  const event = createErrorEvent({
    severity: classifyError(appError).severity,
    category: appError.category,
    code: appError.code,
    message: appError.message,
    context: appError.context,
    source: appError.source,
    recoverable: appError.recoverable,
  });

  // Log the error
  logger.error(event);

  // Update UI state
  errorState.setError(event);

  return event;
};
