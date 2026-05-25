import { invoke as tauriInvoke } from "@tauri-apps/api/core";
import { metricsStore, METRIC_NAMES } from "$lib/logger/MetricsStore";

/**
 * Wrapper around Tauri's invoke that measures the duration of every IPC call
 * and records it as a metric. This provides observability into frontend-to-backend
 * communication performance.
 */
export async function invoke<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  const start = performance.now();

  try {
    const result = await tauriInvoke<T>(cmd, args);
    const durationMs = performance.now() - start;
    metricsStore.record({
      name: METRIC_NAMES.IPC_CALL,
      durationMs: Math.round(durationMs),
      feature: cmd,
      count: 1,
      success: true,
    });
    return result;
  } catch (error) {
    const durationMs = performance.now() - start;
    metricsStore.record({
      name: METRIC_NAMES.IPC_CALL,
      durationMs: Math.round(durationMs),
      feature: cmd,
      count: 1,
      success: false,
      errorCode: error instanceof Error ? error.message : String(error),
    });
    throw error;
  }
}
