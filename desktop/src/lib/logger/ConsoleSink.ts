import type { LoggerSink } from "./Logger";
import type { ErrorEvent } from "../events/ErrorEvent";

export class ConsoleSink implements LoggerSink {
  log(event: ErrorEvent): void {
    const level = event.severity === "critical" || event.severity === "high" ? "error" :
                  event.severity === "medium" ? "warn" : "log";

    const prefix = `[${event.category}]${event.recoverable ? "" : " [FATAL]"}`;

    console[level](
      prefix,
      `[${event.code}]`,
      event.message,
      event.context
    );
  }
}

export const consoleSink = new ConsoleSink();