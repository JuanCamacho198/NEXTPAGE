import './styles.css';
import App from './App.svelte';
import { mount } from 'svelte';
import { onOpenUrl } from '@tauri-apps/plugin-deep-link';
import { registerSupabaseCallbackHandler } from './lib/shared/services';
import { handleDeepLinkUrls, registerDeepLinkListener } from './lib/features/addons/installDeepLink';
import { logger } from './lib/shared/logger/Logger';
import { consoleSink } from './lib/shared/logger/ConsoleSink';
import { tauriSink } from './lib/shared/logger/TauriSink';
import { SentrySink } from './lib/shared/logger/SentrySink';
import { getSentrySettings } from './lib/shared/logger/sentryConfig';
import { createErrorEvent, type ErrorEvent } from './lib/shared/events/ErrorEvent';

let handlersRegistered = false;

const initLogger = async (): Promise<void> => {
  logger.registerSink(consoleSink);
  logger.registerSink(tauriSink);

  const sentrySettings = await getSentrySettings();
  if (sentrySettings.dsn) {
    const sentrySink = new SentrySink(sentrySettings);
    logger.registerSink(sentrySink);
  }
};

const handleGlobalError = (event: ErrorEvent): void => {
  const errorEvent = createErrorEvent({
    severity: 'high',
    category: 'runtime',
    code: 'UNCAUGHT_ERROR',
    message: event.message,
    context: {
      filename: (event as unknown as { filename?: string }).filename,
      lineno: (event as unknown as { lineno?: number }).lineno,
      colno: (event as unknown as { colno?: number }).colno,
    },
    source: 'app_shell',
    recoverable: false,
  });

  logger.error(errorEvent);
};

const handleUnhandledRejection = (event: PromiseRejectionEvent): void => {
  const errorMessage = event.reason instanceof Error ? event.reason.message : String(event.reason);

  const errorEvent = createErrorEvent({
    severity: 'high',
    category: 'promise_rejection',
    code: 'UNHANDLED_REJECTION',
    message: errorMessage,
    context: {
      reason:
        event.reason instanceof Error
          ? { name: event.reason.name, stack: event.reason.stack }
          : String(event.reason),
    },
    source: 'app_shell',
    recoverable: false,
  });

  logger.error(errorEvent);
};

const registerGlobalHandlers = async (): Promise<void> => {
  if (handlersRegistered) {
    return;
  }

  await initLogger();

  window.onerror = (message, source, lineno, colno, error) => {
    const errorEvent = createErrorEvent({
      severity: 'high',
      category: 'runtime',
      code: 'UNCAUGHT_ERROR',
      message: typeof message === 'string' ? message : 'Unknown error',
      context: { source, lineno, colno, error: error?.stack },
      source: 'app_shell',
      recoverable: false,
    });
    handleGlobalError(errorEvent);
    return false;
  };

  window.onunhandledrejection = (event) => {
    handleUnhandledRejection(event);
  };

  handlersRegistered = true;
};

onOpenUrl((urls) => {
  // sdd/addon-deeplink-v1: cold-start routing. handleDeepLinkUrls is the
  // single entry point shared with the warm-start single-instance event —
  // non-install URLs stay ignored (REQ-7: deep-link reserved, OAuth loopback).
  void handleDeepLinkUrls(urls);
});

// Warm start: single-instance plugin forwards nextpage:// argv via this event.
void registerDeepLinkListener();

// Supabase OAuth wiring: listen for OAuth callback on loopback URL.
registerSupabaseCallbackHandler();

const app = mount(App, {
  target: document.getElementById('app') as HTMLElement,
});

registerGlobalHandlers();

export default app;
