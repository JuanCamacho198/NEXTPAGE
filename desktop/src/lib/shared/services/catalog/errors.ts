/**
 * Stable catalog error codes shared by desktop and Android.
 * Messages are redacted at the boundary; only `code` is contractual.
 */
export type CatalogErrorCode =
  | 'INVALID_PAGE'
  | 'UNAVAILABLE_DOWNLOAD'
  | 'NOT_FOUND'
  | 'RATE_LIMITED'
  | 'UPSTREAM_ERROR'
  | 'NETWORK_ERROR';

export class CatalogError extends Error {
  readonly code: CatalogErrorCode;
  readonly retryable: boolean;

  constructor(code: CatalogErrorCode, message: string, retryable = false) {
    super(message);
    this.name = 'CatalogError';
    this.code = code;
    this.retryable = retryable;
  }
}

/** Build a typed catalog error without leaking upstream details. */
export function catalogError(code: CatalogErrorCode, detail?: string): CatalogError {
  const retryable = code === 'RATE_LIMITED' || code === 'NETWORK_ERROR';
  return new CatalogError(code, detail ? `${code}: ${detail}` : code, retryable);
}

export function isCatalogError(err: unknown): err is CatalogError {
  return err instanceof CatalogError;
}

/** Map an upstream HTTP status to a stable contract code. */
export function mapHttpStatusToCode(status: number): CatalogErrorCode {
  if (status === 404) return 'NOT_FOUND';
  if (status === 429) return 'RATE_LIMITED';
  if (status >= 500) return 'UPSTREAM_ERROR';
  return 'UPSTREAM_ERROR';
}
