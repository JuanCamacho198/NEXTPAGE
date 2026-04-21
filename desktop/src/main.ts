import "./styles.css";
import App from "./App.svelte";
import { mount } from "svelte";
import { onOpenUrl } from "@tauri-apps/plugin-deep-link";
import { supabase } from "./lib/api/supabase";
import { logger } from "./lib/logger/Logger";
import { consoleSink } from "./lib/logger/ConsoleSink";
import { tauriSink } from "./lib/logger/TauriSink";
import { SentrySink } from "./lib/logger/SentrySink";
import { getSentrySettings } from "./lib/logger/sentryConfig";
import { createErrorEvent, type ErrorEvent } from "./lib/events/ErrorEvent";

let handlersRegistered = false;

const initLogger = async () => {
  logger.registerSink(consoleSink);
  logger.registerSink(tauriSink);

  const sentrySettings = await getSentrySettings();
  if (sentrySettings.dsn) {
    const sentrySink = new SentrySink(sentrySettings);
    logger.registerSink(sentrySink);
  }
};

const generateCorrelationId = (): string => {
  return crypto.randomUUID();
};

const handleGlobalError = (event: ErrorEvent) => {
  const correlationId = generateCorrelationId();

  const errorEvent = createErrorEvent({
    severity: "high",
    category: "runtime",
    code: "UNCAUGHT_ERROR",
    message: event.message,
    context: {
      filename: (event as unknown as { filename?: string }).filename,
      lineno: (event as unknown as { lineno?: number }).lineno,
      colno: (event as unknown as { colno?: number }).colno,
    },
    source: "app_shell",
    recoverable: false,
  });

  logger.error(errorEvent);
};

const handleUnhandledRejection = (event: PromiseRejectionEvent) => {
  const correlationId = generateCorrelationId();

  const errorMessage = event.reason instanceof Error
    ? event.reason.message
    : String(event.reason);

  const errorEvent = createErrorEvent({
    severity: "high",
    category: "promise_rejection",
    code: "UNHANDLED_REJECTION",
    message: errorMessage,
    context: {
      reason: event.reason instanceof Error
        ? { name: event.reason.name, stack: event.reason.stack }
        : String(event.reason),
    },
    source: "app_shell",
    recoverable: false,
  });

  logger.error(errorEvent);
};

const registerGlobalHandlers = async () => {
  if (handlersRegistered) {
    return;
  }

  await initLogger();

  window.onerror = (message, source, lineno, colno, error) => {
    const event: ErrorEvent = {
      timestamp: new Date().toISOString(),
      severity: "high",
      category: "runtime",
      code: "UNCAUGHT_ERROR",
      message: typeof message === "string" ? message : "Unknown error",
      context: { source, lineno, colno, error: error?.stack },
      correlationId: generateCorrelationId(),
      source: "app_shell",
      recoverable: false,
    };
    handleGlobalError(event);
    return false;
  };

  window.onunhandledrejection = (event) => {
    handleUnhandledRejection(event);
  };

  handlersRegistered = true;
};

onOpenUrl((urls) => {
  console.log("Deep links received:", urls);
  for (const url of urls) {
    if (url.includes("auth-callback")) {
      console.log("Handling auth-callback...");
      const hash = url.split("#")[1];
      if (hash) {
        const params = new URLSearchParams(hash);
        const accessToken = params.get("access_token");
        const refreshToken = params.get("refresh_token");

        if (accessToken && refreshToken) {
          supabase.auth.setSession({
            access_token: accessToken,
            refresh_token: refreshToken,
          }).then(({ data, error }) => {
            if (error) console.error("Error completing session:", error.message);
            else console.log("Session completed for user:", data.user?.email);
          });
        }
      }
    }
  }
});

const app = mount(App, {
  target: document.getElementById("app") as HTMLElement
});

registerGlobalHandlers();

export default app;