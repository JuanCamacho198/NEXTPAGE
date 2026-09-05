import {
  createErrorEvent,
  type ErrorEvent,
  type ErrorCategory,
  type ErrorSource,
} from '../events/ErrorEvent';
import { classifyError } from '../events/classifyError';
import { errorState } from '../stores/ErrorState';
import { logger } from '../logger';

export class AppError extends Error {
  constructor(
    public message: string,
    public code: string = 'UNKNOWN_ERROR',
    public source: ErrorSource = 'app_shell',
    public category: ErrorCategory = 'runtime',
    public context: Record<string, unknown> = {},
    public recoverable: boolean = true,
  ) {
    super(message);
    this.name = 'AppError';
  }
}

export class ReaderError extends AppError {
  constructor(
    message: string,
    code: string = 'READER_ERROR',
    context: Record<string, unknown> = {},
  ) {
    super(message, code, 'reader', 'runtime', context, true);
    this.name = 'ReaderError';
  }

  /**
   * Returns a NEW `ReaderError` whose `context` is the merge of the receiver's
   * context with `extra`. The receiver is NOT mutated; callers MUST use the
   * returned error.
   *
   * Merge order: `extra` is spread AFTER the receiver's context, so on
   * conflict `extra` wins (last-write-wins semantics).
   *
   * No-op semantics: when `extra` is empty, the returned error carries the
   * same context shape but is a fresh instance (NOT the same reference).
   *
   * PII contract: callers MUST NOT pass user-typed text (notes, tag names,
   * highlight text) directly. Use length-only fields (`noteLength`,
   * `tagNameLength`) or rely on `sentryPiiScrubber` to redact the matching
   * key (`noteText`, `tagName`, `tag`, `note`) before the event reaches
   * Sentry.
   */
  withContext(extra: Record<string, unknown>): ReaderError {
    return new ReaderError(this.message, this.code, { ...this.context, ...extra });
  }
}

export class FileSystemError extends AppError {
  constructor(message: string, code: string = 'FS_ERROR', context: Record<string, unknown> = {}) {
    super(message, code, 'app_shell', 'command', context, true);
    this.name = 'FileSystemError';
  }
}

/**
 * Centralized error handler for the application.
 * Processes errors, logs them, and updates the global error state for UI feedback.
 */
export const handleError = (error: unknown, source: ErrorSource = 'app_shell'): ErrorEvent => {
  let appError: AppError;

  if (error instanceof AppError) {
    appError = error;
  } else if (error instanceof Error) {
    const classification = classifyError(error);
    appError = new AppError(
      error.message,
      (error as Error & { code?: string }).code || 'UNEXPECTED_ERROR',
      source,
      'runtime',
      { stack: error.stack },
      classification.recoverable,
    );
  } else {
    appError = new AppError(
      String(error),
      'UNKNOWN_ERROR',
      source,
      'unknown',
      { originalError: error },
      false,
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
