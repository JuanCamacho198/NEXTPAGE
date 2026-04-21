import type { ErrorEvent } from "../events/ErrorEvent";

export interface LoggerSink {
  log(event: ErrorEvent): void;
}

class LoggerImpl {
  private sinks: LoggerSink[] = [];

  registerSink(sink: LoggerSink): void {
    this.sinks.push(sink);
  }

  error(event: ErrorEvent): void {
    this.broadcast(event);
  }

  warn(event: ErrorEvent): void {
    this.broadcast(event);
  }

  info(event: ErrorEvent): void {
    this.broadcast(event);
  }

  debug(event: ErrorEvent): void {
    this.broadcast(event);
  }

  private broadcast(event: ErrorEvent): void {
    for (const sink of this.sinks) {
      try {
        sink.log(event);
      } catch {
        // sink failures should not break the app
      }
    }
  }
}

export const logger = new LoggerImpl();