/**
 * Crash feedback queue (sdd/sentry-observability-v2 PR3 desktop).
 *
 * Per design #2463: localStorage-backed offline queue (cap 25 FIFO),
 * dismissed set (eventId idempotence), and lastEventId round-trip for the
 * next-launch prompt path. The transport is injected so this module is
 * testable in isolation.
 *
 * PII policy: bookTitle/chapterLabel only ever leave the device through a
 * feedback-event capture (the scrubber strips them on non-feedback events).
 */
import * as Sentry from '@sentry/browser';

const QUEUE_KEY = 'np.feedback.queue';
const DISMISSED_KEY = 'np.feedback.dismissed';
const LAST_EVENT_ID_KEY = 'np.feedback.lastEventId';
const QUEUE_CAP = 25;

export interface BookContext {
  bookId: string;
  chapterIndex: number;
  page: number;
  title: string; // ≤100 chars
  chapterLabel: string; // ≤80 chars
}

export interface QueuedFeedback {
  eventId: string | null;
  message: string;
  /** Book context; this is the only channel through which bookTitle/chapterLabel are allowed egress. */
  contexts: { book: BookContext };
  enqueuedAt: number; // epoch ms
}

export interface FlushTransport {
  send: (entry: QueuedFeedback) => Promise<boolean>;
}

/** Default transport — sends via Sentry.captureFeedback. The browser
 *  Sentry SDK does not accept a `contexts` field on captureFeedback, so the
 *  book context travels via the `QueuedFeedback.contexts.book` payload and
 *  is injected by callers that need it (see AppModals transport — it uses
 *  `scope.setContext('book', entry.contexts.book)`).
 */
const defaultTransport: FlushTransport = {
  send: async (entry) => {
    try {
      await Sentry.captureFeedback({
        message: entry.message,
        associatedEventId: entry.eventId ?? undefined,
      });
      return true;
    } catch {
      return false;
    }
  },
};

function readJSON<T>(key: string, fallback: T): T {
  if (typeof localStorage === 'undefined') return fallback;
  const raw = localStorage.getItem(key);
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function writeJSON(key: string, value: unknown): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // quota or privacy mode: drop quietly; egress is best-effort
  }
}

/** Read the last captured Sentry event id (or null). */
export function readLastEventId(): string | null {
  return readJSON<string | null>(LAST_EVENT_ID_KEY, null);
}

/** Persist a Sentry eventId for the next-launch prompt path. */
export function recordLastEventId(id: string | null): void {
  writeJSON(LAST_EVENT_ID_KEY, id);
}

export function isDismissed(eventId: string): boolean {
  const set = readJSON<string[]>(DISMISSED_KEY, []);
  return set.includes(eventId);
}

export function markDismissed(eventId: string): void {
  const set = readJSON<string[]>(DISMISSED_KEY, []);
  if (!set.includes(eventId)) {
    set.push(eventId);
    writeJSON(DISMISSED_KEY, set);
  }
}

export function clearDismissed(): void {
  if (typeof localStorage !== 'undefined') localStorage.removeItem(DISMISSED_KEY);
}

export function enqueueFeedback(entry: QueuedFeedback): void {
  const queue = readJSON<QueuedFeedback[]>(QUEUE_KEY, []);
  queue.push(entry);
  // FIFO cap: drop oldest beyond QUEUE_CAP
  while (queue.length > QUEUE_CAP) queue.shift();
  writeJSON(QUEUE_KEY, queue);
}

export function readFeedbackQueue(): QueuedFeedback[] {
  return readJSON<QueuedFeedback[]>(QUEUE_KEY, []);
}

export function clearFeedbackQueue(): void {
  if (typeof localStorage !== 'undefined') localStorage.removeItem(QUEUE_KEY);
}

/** Attempt to flush the queue using the provided transport. Stops on first failure. */
export async function flushFeedbackQueue(
  transport: FlushTransport = defaultTransport
): Promise<{ sent: number; failed: QueuedFeedback[] }> {
  const queue = readFeedbackQueue();
  if (queue.length === 0) return { sent: 0, failed: [] };
  const sent: QueuedFeedback[] = [];
  const failed: QueuedFeedback[] = [];
  for (const entry of queue) {
    const ok = await transport.send(entry);
    if (ok) sent.push(entry);
    else failed.push(entry);
    if (!ok) break; // stop on first failure; remaining items are kept
  }
  if (sent.length > 0) {
    const remaining = queue.filter((e) => !sent.includes(e));
    writeJSON(QUEUE_KEY, remaining);
  }
  return { sent: sent.length, failed };
}

let autoFlushTimer: ReturnType<typeof setInterval> | null = null;

/** Start a 30s background flush cycle. Returns the stop function. */
export function startAutoFlush(transport: FlushTransport = defaultTransport): () => void {
  if (autoFlushTimer) return stopAutoFlush;
  autoFlushTimer = setInterval(() => {
    if (typeof navigator !== 'undefined' && !navigator.onLine) return;
    void flushFeedbackQueue(transport);
  }, 30_000);
  return stopAutoFlush;
}

export function stopAutoFlush(): void {
  if (autoFlushTimer) {
    clearInterval(autoFlushTimer);
    autoFlushTimer = null;
  }
}

/** Truncate a user message to the platform limit (helper for dialogs). */
export function truncateMessage(message: string, maxChars: number): string {
  if (message.length <= maxChars) return message;
  return message.slice(0, maxChars);
}

/** Build a BookContext with hard caps (100/80). */
export function buildBookContext(input: {
  bookId: string;
  chapterIndex: number;
  page: number;
  title?: string;
  chapterLabel?: string;
}): BookContext {
  return {
    bookId: input.bookId,
    chapterIndex: input.chapterIndex,
    page: input.page,
    title: (input.title ?? '').slice(0, 100),
    chapterLabel: (input.chapterLabel ?? '').slice(0, 80),
  };
}

export const FEEDBACK_QUEUE_CAP = QUEUE_CAP;
