import * as Sentry from '@sentry/browser';
import type { MetricEvent } from './metricTypes';
import type { SentrySettings } from './sentryConfig';

/**
 * Pure mapper from a `MetricEvent` to the Sentry metrics API. No-op when the
 * DSN is absent or `enabled` is false — the enabled state is consulted
 * PER-EMIT so a mid-session disable (privacy notice) stops egress immediately.
 *
 * Bucketing happens at the instrumentation site (`metricBuckets.ts`); the
 * sink only forwards `bucketedDurationMs` when present.
 */
export class SentryMetricsSink {
  constructor(private settings?: SentrySettings) {}

  private shouldEmit(): boolean {
    if (this.settings?.enabled === false) return false;
    return !!(this.settings?.dsn && this.settings.dsn.length > 0);
  }

  private static tagsOf(event: MetricEvent): Record<string, string> {
    const tags: Record<string, string> = {};
    for (const [key, value] of Object.entries(event.tags ?? {})) {
      if (typeof value === 'string') tags[key] = value;
    }
    if (event.feature) tags['feature'] = event.feature;
    return tags;
  }

  handle(event: MetricEvent): void {
    if (!this.shouldEmit()) return;

    const tags = SentryMetricsSink.tagsOf(event);

    try {
      if (event.bucketedDurationMs != null) {
        Sentry.metrics.distribution(event.name, event.bucketedDurationMs, { tags });
      } else {
        Sentry.metrics.increment(event.name, event.count, { tags });
      }

      if (event.success === false) {
        const errorCode = (event.errorCode ?? 'UNKNOWN').slice(0, 8);
        Sentry.metrics.increment(`${event.name}_error`, 1, {
          tags: { ...tags, error_code: errorCode },
        });
      }
    } catch {
      // Metrics egress must never crash the app.
    }
  }
}
