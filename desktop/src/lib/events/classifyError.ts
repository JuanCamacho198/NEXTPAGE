import type { ErrorSeverity } from "../events/ErrorEvent";

export interface ErrorClassification {
  recoverable: boolean;
  severity: ErrorSeverity;
}

const FATAL_ERROR_CODES = new Set([
  "UNCAUGHT_ERROR",
  "UNHANDLED_REJECTION",
  "APP_SHELL_CRASH",
  "BOOTSTRAP_ERROR",
  "RUNTIME_ERROR_FATAL",
]);

const RECOVERABLE_ERROR_CODES = new Set([
  "NETWORK_ERROR",
  "TIMEOUT",
  "PARTIAL_FAILURE",
  "OPTIMISTIC_UPDATE_FAILED",
]);

export const classifyError = (
  error: unknown
): ErrorClassification => {
  let code = "UNKNOWN";
  let message = "Unknown error";
  let category = "unknown";

  if (error && typeof error === "object") {
    const err = error as Record<string, unknown>;

    if (err.code && typeof err.code === "string") {
      code = err.code;
    }

    if (err.message && typeof err.message === "string") {
      message = err.message;
    }

    if (err.category && typeof err.category === "string") {
      category = err.category;
    }

    if (typeof err.recoverable === "boolean") {
      return {
        recoverable: err.recoverable,
        severity: err.recoverable ? "medium" : "high",
      };
    }
  }

  if (FATAL_ERROR_CODES.has(code)) {
    return {
      recoverable: false,
      severity: "critical",
    };
  }

  if (RECOVERABLE_ERROR_CODES.has(code)) {
    return {
      recoverable: true,
      severity: "low",
    };
  }

  if (category === "network" || message.toLowerCase().includes("network")) {
    return {
      recoverable: true,
      severity: "medium",
    };
  }

  if (message.toLowerCase().includes("timeout")) {
    return {
      recoverable: true,
      severity: "low",
    };
  }

  return {
    recoverable: false,
    severity: "high",
  };
};