<!--
  Crash Feedback Dialog — desktop modal (sdd/sentry-observability-v2 PR3, spec D1).
  Pixel-faithful to the HYNft Pencil frame (engram #2460, 560x600 over dimmed
  reader .65 black). ES copy verbatim from $lib/shared/feedback/feedbackDesign.

  Triggers (spec D3): ErrorFallback dispatches `np:open-feedback`; AppModals
  also opens this on next-launch when a lastEventId was persisted.

  State machine: idle -> editing -> sending -> sent | error. The dialog is
  host-aware: Sentry is the only egress, and we never block on it.
-->
<script lang="ts">
  import {
    FEEDBACK_EYEBROW,
    FEEDBACK_TITLE,
    FEEDBACK_SUBTITLE,
    FEEDBACK_CONTEXT_HEADER,
    FEEDBACK_PILLS,
    FEEDBACK_INPUT_LABEL,
    FEEDBACK_INPUT_HINT,
    FEEDBACK_PRIVACY,
    FEEDBACK_RESTART_LABEL,
    FEEDBACK_SEND_LABEL,
    FEEDBACK_MAX_CHARS,
    FEEDBACK_COVER_GRADIENT,
    FEEDBACK_SAMPLE_BOOK,
  } from '$lib/shared/feedback/feedbackDesign';
  import {
    enqueueFeedback,
    flushFeedbackQueue,
    isDismissed,
    markDismissed,
    truncateMessage,
    buildBookContext,
    type BookContext,
  } from '$lib/shared/feedback/feedbackStore';
  import * as Sentry from '@sentry/browser';

  type DialogState = 'idle' | 'editing' | 'sending' | 'sent' | 'error';

  interface Props {
    open: boolean;
    eventId: string | null;
    onDismiss: (eventId: string | null) => void;
  }

  let { open = $bindable(false), eventId, onDismiss }: Props = $props();

  let message = $state('');
  let dialogState: DialogState = $state('idle');
  let context: BookContext = $state(buildBookContext({
    bookId: 'sample',
    chapterIndex: 0,
    page: FEEDBACK_SAMPLE_BOOK.page,
    title: FEEDBACK_SAMPLE_BOOK.title,
    chapterLabel: FEEDBACK_SAMPLE_BOOK.chapter,
  }));

  $effect(() => {
    if (!open) return;
    if (eventId && isDismissed(eventId)) {
      // Same-event dismiss-once: spec D3 — never re-nag.
      open = false;
      return;
    }
    dialogState = 'editing';
    message = '';
  });

  const counter = $derived(`${message.length} / ${FEEDBACK_MAX_CHARS}`);
  const canSend = $derived(message.length > 0 && message.length <= FEEDBACK_MAX_CHARS);

  function handleInput(event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    message = truncateMessage(value, FEEDBACK_MAX_CHARS);
  }

  function handleSend(): void {
    if (!canSend) return;
    dialogState = 'sending';
    void doSend();
  }

  async function doSend(): Promise<void> {
    const entry = { eventId, message, contexts: { book: context }, enqueuedAt: Date.now() };
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      enqueueFeedback(entry);
      finish('sent', true);
      return;
    }
    try {
      // The browser Sentry SDK does not accept `contexts` on captureFeedback,
      // so the book context is attached via withScope → setContext (same
      // pattern AppModals transport uses). captureFeedback only supports
      // message + associatedEventId at the call site.
      Sentry.withScope((scope) => {
        scope.setContext('book', entry.contexts.book as unknown as Record<string, unknown>);
        Sentry.captureFeedback({
          message: entry.message,
          associatedEventId: eventId ?? undefined,
        });
      });
      finish('sent', true);
    } catch {
      // Offline mid-flight: queue and resolve as sent (eventually delivered).
      enqueueFeedback(entry);
      finish('error', true);
    }
  }

  function finish(next: DialogState, dismissed: boolean): void {
    dialogState = next;
    if (dismissed && eventId) markDismissed(eventId);
    if (typeof navigator !== 'undefined' && navigator.onLine) {
      // Best-effort: drain any prior queued items now.
      void flushFeedbackQueue();
    }
    onDismiss(eventId);
    open = false;
  }

  function handleClose(): void {
    if (eventId) markDismissed(eventId);
    onDismiss(eventId);
    open = false;
  }

  function handleRestart(): void {
    if (eventId) markDismissed(eventId);
    onDismiss(eventId);
    open = false;
    if (typeof window !== 'undefined') window.location.reload();
  }
</script>

{#if open}
  <div
    class="fixed inset-0 z-50 flex items-center justify-center"
    role="dialog"
    aria-modal="true"
    aria-labelledby="feedback-title"
  >
    <div class="absolute inset-0 bg-black/65" aria-hidden="true"></div>

    <div
      class="relative z-10 flex w-[560px] max-w-[95vw] flex-col gap-5 rounded-2xl border border-(--color-border) bg-(--color-panel) p-8 font-[Manrope,Inter,system-ui,sans-serif] text-(--color-text-primary) shadow-2xl"
    >
      <!-- Header: icon + eyebrow + title + close -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div
            class="flex h-11 w-11 items-center justify-center rounded-xl bg-(--color-error-soft)"
            aria-hidden="true"
          >
            <svg
              class="h-5 w-5 text-(--color-error)"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
            </svg>
          </div>
          <div>
            <p
              class="text-[10px] font-bold uppercase tracking-[0.15em] text-(--color-error)"
            >
              {FEEDBACK_EYEBROW}
            </p>
            <h2 id="feedback-title" class="text-lg font-bold">{FEEDBACK_TITLE}</h2>
          </div>
        </div>
        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-lg text-(--color-text-secondary) hover:bg-(--color-border)/40"
          aria-label="Cerrar"
          onclick={handleClose}
        >
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 6L6 18M6 6l12 12" />
          </svg>
        </button>
      </div>

      <p class="text-sm leading-[1.55] text-(--color-text-secondary)">
        {FEEDBACK_SUBTITLE}
      </p>

      <!-- Context card -->
      <div
        class="flex flex-col gap-3 rounded-xl border border-(--color-border) bg-(--color-panel-accent) p-4"
      >
        <p
          class="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-[0.15em] text-(--color-accent)"
        >
          <svg
            class="h-3 w-3"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="16" x2="12" y2="12" />
            <line x1="12" y1="8" x2="12.01" y2="8" />
          </svg>
          {FEEDBACK_CONTEXT_HEADER}
        </p>
        <div class="flex items-center gap-3">
          <div
            class="h-14 w-10 rounded"
            style="background: {FEEDBACK_COVER_GRADIENT};"
            aria-hidden="true"
          ></div>
          <div class="flex flex-1 flex-col">
            <p class="text-sm font-bold">{context.title}</p>
            <p class="text-[11px] text-(--color-text-muted)">
              {FEEDBACK_SAMPLE_BOOK.author} · {context.chapterLabel} · p. {context.page} / {FEEDBACK_SAMPLE_BOOK.totalPages}
            </p>
          </div>
        </div>
        <!-- Stat pills (desktop-only per HYNft) -->
        <div class="flex flex-wrap gap-2">
          {#each FEEDBACK_PILLS as pill (pill.label)}
            <span
              class="flex items-center gap-1 rounded-full border border-(--color-border) bg-(--color-panel) px-2 py-0.5 text-[11px] font-semibold text-(--color-text-secondary)"
            >
              {pill.label}
            </span>
          {/each}
        </div>
      </div>

      <!-- Input section -->
      <div class="flex flex-col gap-2">
        <div class="flex items-center justify-between">
          <label for="feedback-text" class="text-[13px] font-semibold">
            {FEEDBACK_INPUT_LABEL}
          </label>
          <span class="text-[11px] text-(--color-text-muted)">{FEEDBACK_INPUT_HINT}</span>
        </div>
        <div
          class="flex min-h-[110px] flex-col gap-1.5 rounded-lg border border-(--color-border) bg-(--color-bg) p-3"
        >
          <textarea
            id="feedback-text"
            class="min-h-[64px] flex-1 resize-none bg-transparent text-[13px] leading-[1.5] text-(--color-text-primary) outline-none placeholder:text-(--color-text-muted)"
            placeholder=""
            value={message}
            oninput={handleInput}
            maxlength={FEEDBACK_MAX_CHARS}
            disabled={dialogState === 'sending' || dialogState === 'sent'}
          ></textarea>
          <span
            class="self-end text-[11px] {message.length > FEEDBACK_MAX_CHARS
              ? 'text-(--color-error)'
              : 'text-(--color-text-muted)'}"
          >
            {counter}
          </span>
        </div>
      </div>

      <!-- Footer: privacy + restart + send -->
      <div class="flex items-center justify-between pt-2">
        <p class="flex items-center gap-1.5 text-[11px] text-(--color-text-muted)">
          <svg
            class="h-3 w-3"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            aria-hidden="true"
          >
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
          </svg>
          {FEEDBACK_PRIVACY}
        </p>
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="flex items-center gap-1.5 rounded-lg border border-(--color-border) bg-transparent px-3 py-2 text-[13px] font-semibold text-(--color-text-primary) hover:bg-(--color-border)/30"
            onclick={handleRestart}
          >
            <svg
              class="h-3.5 w-3.5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              aria-hidden="true"
            >
              <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
              <path d="M3 3v5h5" />
            </svg>
            {FEEDBACK_RESTART_LABEL}
          </button>
          <button
            type="button"
            class="flex items-center gap-1.5 rounded-lg bg-(--color-primary) px-4 py-2 text-[13px] font-bold text-white hover:opacity-90 disabled:opacity-50"
            onclick={handleSend}
            disabled={!canSend || dialogState === 'sending' || dialogState === 'sent'}
          >
            {dialogState === 'sending' ? 'Enviando…' : FEEDBACK_SEND_LABEL}
            <svg
              class="h-3.5 w-3.5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              aria-hidden="true"
            >
              <line x1="5" y1="12" x2="19" y2="12" />
              <polyline points="12 5 19 12 12 19" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
{/if}
