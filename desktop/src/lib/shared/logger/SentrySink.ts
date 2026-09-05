import * as Sentry from '@sentry/browser';
import type { LoggerSink } from './Logger';
import type { ErrorEvent } from '../events/ErrorEvent';
import type { SentrySettings } from './sentryConfig';
import { scrubEvent, type SentryLikeEvent } from './sentryPiiScrubber';
import { captureBreadcrumb } from './BreadcrumbsStore';
import { BREADCRUMB_LABELS } from './breadcrumbTypes';
import { routeAlert } from './AlertRouter';

type SentrySeverityLevel = 'fatal' | 'error' | 'warning' | 'info' | 'debug';

const HIGH_SEVERITY = ['high', 'critical'];

export class SentrySink implements LoggerSink {
  private isEnabled: boolean = false;

  constructor(private settings?: SentrySettings) {
    this.isEnabled = this.shouldEnable();
    if (this.isEnabled) {
      this.initSentry();
    }
  }

  private shouldEnable(): boolean {
    if (this.settings?.enabled === false) {
      return false;
    }
    return !!(this.settings?.dsn && this.settings.dsn.length > 0);
  }

  private initSentry(): void {
    if (!this.settings?.dsn) {
      return;
    }

    try {
      Sentry.init({
        dsn: this.settings.dsn,
        release: this.settings.release,
        environment: this.settings.environment,
        tracesSampleRate: this.settings.tracesSampleRate ?? 0.1,
        sendDefaultPii: this.settings.sendDefaultPii ?? false,
        replaysSessionSampleRate: this.settings.replaysSessionSampleRate ?? 0,
        replaysOnErrorSampleRate: this.settings.replaysOnErrorSampleRate ?? 0.1,
        // `maskAllText` / `maskAllInputs` are replay-integration options, not
        // top-level init options in `@sentry/browser` v10. See
        // https://docs.sentry.io/platforms/javascript/session-replay/configuration/
        integrations: [
          Sentry.browserTracingIntegration(),
          Sentry.replayIntegration({
            maskAllText: this.settings.maskAllText ?? true,
            maskAllInputs: this.settings.maskAllInputs ?? true,
          }),
        ],
        beforeSend: (event) => scrubEvent(event as unknown as SentryLikeEvent) as unknown as Sentry.ErrorEvent,
      });
    } catch (err) {
      // Init failure must NOT crash the app — fall back to no-op. The
      // outer `Logger.broadcast` try/catch would also catch this, but the
      // sink itself must be defensive so other sinks still fire.
      console.warn('Sentry init failed', err);
      this.isEnabled = false;
    }
  }

  log(event: ErrorEvent): void {
    if (HIGH_SEVERITY.includes(event.severity)) {
      captureBreadcrumb('error', this.mapCodeToLabel(event.code), {
        message: event.message,
        code: event.code,
        source: event.source,
        severity: event.severity,
      });
      routeAlert(event);
    }

    if (!this.isEnabled) {
      return;
    }

    const level = this.mapSeverity(event.severity);

    Sentry.withScope((scope: Sentry.Scope) => {
      scope.setLevel(level);
      scope.setExtra('category', event.category);
      scope.setExtra('code', event.code);
      scope.setExtra('source', event.source);
      scope.setExtra('recoverable', event.recoverable);
      scope.setExtra('correlationId', event.correlationId);

      if (event.context && Object.keys(event.context).length > 0) {
        for (const [key, value] of Object.entries(event.context)) {
          scope.setExtra(key, value);
        }
      }

      if (level === 'error' || level === 'fatal') {
        const error = new Error(event.message);
        error.name = event.code;
        Sentry.captureException(error);
      } else {
        Sentry.captureMessage(event.message, level);
      }
    });
  }

  private mapCodeToLabel(code: string): string {
    if (code.includes('IMPORT')) return BREADCRUMB_LABELS.IMPORT_FAIL;
    if (code.includes('SYNC')) return 'sync_fail';
    if (code.includes('READER')) return 'reader_fail';
    return 'error_generic';
  }

  private mapSeverity(severity: ErrorEvent['severity']): SentrySeverityLevel {
    switch (severity) {
      case 'critical':
        return 'fatal';
      case 'high':
        return 'error';
      case 'medium':
        return 'warning';
      case 'low':
        return 'info';
      default:
        return 'info';
    }
  }
}

export const createSentrySink = (settings?: SentrySettings): LoggerSink => {
  return new SentrySink(settings);
};
