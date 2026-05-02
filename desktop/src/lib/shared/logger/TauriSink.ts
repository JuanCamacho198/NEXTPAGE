import { invoke } from "@tauri-apps/api/core";
import type { LoggerSink } from "./Logger";
import type { ErrorEvent } from "../events/ErrorEvent";

export class TauriSink implements LoggerSink {
  async log(event: ErrorEvent): Promise<void> {
    try {
      const dto = {
        timestamp: event.timestamp,
        severity: event.severity,
        category: event.category,
        code: event.code,
        message: event.message,
        context: event.context,
        correlationId: event.correlationId,
        source: event.source,
        recoverable: event.recoverable,
      };

      await invoke("reportErrorEvent", { event: dto });
    } catch {
      // logging should never break the app
    }
  }
}

export const tauriSink = new TauriSink();