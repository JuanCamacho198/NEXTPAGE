/**
 * PII scrubber for Sentry outbound events.
 *
 * Source of truth: `desktop/src-tauri/src/logger.rs::Logger::redact_event`
 * (the Rust backend uses the same regex set on its `before_send` hook in
 * Phase 2 of this change).
 *
 * Policy:
 * - Redact any extra field whose KEY contains one of the sensitive patterns
 *   (case-insensitive): `password`, `token`, `secret`, `api_key`,
 *   `access_token`, `refresh_token`, `note`, `noteText`, `tag`, `tagName`.
 *   Value replaced with `[Redacted]`.
 * - For extra path-like fields (`epubPath`, `bookPath`, `filePath`), keep only
 *   the basename to avoid leaking user home/library directories.
 * - Redact `127.0.0.1:<port>` substrings in OAuth callback URLs but keep the
 *   surrounding URL/query string so the event is still useful (the access
 *   token value itself is already caught by the `token` key rule when present
 *   in the extras).
 * - Redact `user.ip_address`.
 *
 * Implementation notes:
 * - Returns a NEW object (deep-copied). The input event is never mutated so
 *   downstream sinks that observe the same event are not affected.
 * - Already-redacted values are left untouched (idempotent).
 * - Pure function: deterministic, no side effects, safe to call from
 *   `beforeSend` and unit tests.
 */

const SENSITIVE_KEY_PATTERNS: readonly string[] = [
  'password',
  'token',
  'secret',
  'api_key',
  'apikey', // camelCase variant of `api_key`
  'access_token',
  'accesstoken', // camelCase variant
  'refresh_token',
  'refreshtoken', // camelCase variant
  // Reader-context PII (Phase 1 of `reader-error-enrichment`): tag names,
  // highlight notes, and similar user-typed text MUST NOT leave the device.
  // Redact both the singular and plural / `text`-suffixed forms because the
  // call sites use different shapes (`tag`, `tagName`, `noteText`, `note`).
  'notetext',
  'note',
  'tagname',
  'tag',
];

const PATH_KEYS: readonly string[] = ['epubPath', 'bookPath', 'filePath'];

// OAuth callback URLs commonly carry access tokens, codes, and states in
// the query string. These extras are free-form strings, not key:value
// pairs, so the colon-based regex above does not cover them. We do a
// targeted pass for known sensitive query parameters.
const SENSITIVE_QUERY_PARAMS: readonly string[] = [
  'code',
  'state',
  'token',
  'access_token',
  'refresh_token',
  'id_token',
];

const REDACTED_VALUE = '[Redacted]';

/**
 * Minimal structural type that mirrors the subset of Sentry event fields we
 * need to redact. Avoids importing `@sentry/browser` types so this module is
 * usable from any context (tests, SSR, native).
 */
export type SentryLikeEvent = {
  tags?: Record<string, unknown>;
  extra?: Record<string, unknown>;
  user?: { ip_address?: string | null; email?: string | null; username?: string | null };
  request?: {
    url?: string;
    cookies?: string | Record<string, string>;
    headers?: Record<string, string>;
  };
  message?: string;
  exception?: { values?: Array<{ value?: string }> };
  [key: string]: unknown;
};

function shouldRedactKey(key: string): boolean {
  const lower = key.toLowerCase();
  return SENSITIVE_KEY_PATTERNS.some((pattern) => lower.includes(pattern));
}

function redactStringMessage(input: string): string {
  // Mirror Rust redact_string: redact any "<pattern>:<value>" occurrence where
  // value is a non-whitespace, non-comma, non-} chunk. Already-redacted values
  // are skipped (regex won't match `[Redacted]:...` because `[Redacted]` does
  // not contain whitespace).
  let result = input;
  for (const pattern of SENSITIVE_KEY_PATTERNS) {
    const escaped = pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    // Flags as second arg (V8 doesn't accept inline `(?i)` in string-built regex).
    const regex = new RegExp(`${escaped}:[^\\s,}]+`, 'gi');
    result = result.replace(regex, `${pattern}:${REDACTED_VALUE}`);
  }
  // OAuth loopback callback: redact port in `127.0.0.1:<port>` segments.
  result = result.replace(/127\.0\.0\.1:\d+/g, '127.0.0.1:[Redacted]');
  // Redact sensitive OAuth query params (free-form URLs, not key:value).
  for (const param of SENSITIVE_QUERY_PARAMS) {
    const escaped = param.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`([?&])${escaped}=[^&\\s]+`, 'gi');
    result = result.replace(regex, `$1${param}=${REDACTED_VALUE}`);
  }
  return result;
}

function basename(path: string): string {
  if (typeof path !== 'string' || path.length === 0) return path;
  // Handles both POSIX ("/a/b/c.epub") and Windows ("C:\a\b\c.epub") separators.
  const idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
  return idx >= 0 ? path.slice(idx + 1) : path;
}

function redactExtra(extra: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(extra)) {
    if (shouldRedactKey(key)) {
      result[key] = REDACTED_VALUE;
      continue;
    }
    if (PATH_KEYS.includes(key) && typeof value === 'string') {
      result[key] = basename(value);
      continue;
    }
    if (typeof value === 'string') {
      result[key] = redactStringMessage(value);
      continue;
    }
    result[key] = value;
  }
  return result;
}

/**
 * Returns a redacted copy of a Sentry event. Never mutates the input.
 */
export function scrubEvent<T extends SentryLikeEvent>(event: T): T {
  const next: Record<string, unknown> = { ...event };

  if (event.extra && typeof event.extra === 'object') {
    next.extra = redactExtra(event.extra as Record<string, unknown>);
  }

  if (event.user && typeof event.user === 'object') {
    const user = event.user;
    next.user = {
      ...user,
      ip_address: user.ip_address ? REDACTED_VALUE : user.ip_address,
      email: user.email ? REDACTED_VALUE : user.email,
      username: user.username ? REDACTED_VALUE : user.username,
    };
  }

  if (event.request && typeof event.request === 'object') {
    const request = event.request;
    next.request = {
      ...request,
      // Sentry's RequestEventData accepts either a `cookies` string or a
      // cookie map. Either way, the contents are session identifiers and
      // MUST NOT leave the device.
      cookies:
        request.cookies && Object.keys(request.cookies).length > 0
          ? REDACTED_VALUE
          : request.cookies,
    };
  }

  if (typeof event.message === 'string') {
    next.message = redactStringMessage(event.message);
  }

  if (event.exception?.values) {
    next.exception = {
      ...event.exception,
      values: event.exception.values.map((v) =>
        typeof v?.value === 'string'
          ? { ...v, value: redactStringMessage(v.value) }
          : v,
      ),
    };
  }

  return next as T;
}
