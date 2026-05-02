import type { ErrorEvent, ErrorSeverity } from "../events/ErrorEvent";
import { logger } from "./Logger";

export type AlertSeverity = "info" | "warning" | "critical";

export interface AlertRule {
  id: string;
  name: string;
  severity: AlertSeverity;
  condition: (event: ErrorEvent) => boolean;
}

export interface AlertContext {
  event: ErrorEvent;
  timestamp: Date;
  count: number;
}

const CRITICAL_THRESHOLD = 3;

const defaultRules: AlertRule[] = [
  {
    id: "startup_failure",
    name: "Startup Failure",
    severity: "critical",
    condition: (event: ErrorEvent) =>
      event.source === "app_shell" &&
      event.category === "runtime" &&
      event.code === "UNCAUGHT_ERROR" &&
      !event.recoverable,
  },
  {
    id: "db_migration_failure",
    name: "Database Migration Failure",
    severity: "critical",
    condition: (event: ErrorEvent) =>
      event.category === "command" &&
      event.code.includes("MIGRATION") &&
      event.severity === "high",
  },
  {
    id: "repeated_failures",
    name: "Repeated Failures",
    severity: "critical",
    condition: (event: ErrorEvent) => {
      return event.severity === "critical";
    },
  },
];

class AlertRouterImpl {
  private rules: AlertRule[] = defaultRules;
  private errorCounts: Map<string, number> = new Map();
  private lastErrorTimes: Map<string, number> = new Map();

  registerRule(rule: AlertRule): void {
    this.rules.push(rule);
  }

  route(event: ErrorEvent): void {
    const matchingRule = this.rules.find((rule) => rule.condition(event));

    if (!matchingRule) {
      return;
    }

    if (matchingRule.severity === "critical") {
      this.incrementCount(event.code);
    }

    this.sendAlert(matchingRule, event);
  }

  private incrementCount(code: string): void {
    const now = Date.now();
    const lastTime = this.lastErrorTimes.get(code) ?? 0;

    if (now - lastTime > 60000) {
      this.errorCounts.set(code, 1);
    } else {
      const count = this.errorCounts.get(code) ?? 0;
      this.errorCounts.set(code, count + 1);
    }

    this.lastErrorTimes.set(code, now);
  }

  getCount(code: string): number {
    return this.errorCounts.get(code) ?? 0;
  }

  isThresholdExceeded(code: string): boolean {
    return this.getCount(code) >= CRITICAL_THRESHOLD;
  }

  private sendAlert(rule: AlertRule, event: ErrorEvent): void {
    const count = this.getCount(event.code);
    const context: AlertContext = {
      event,
      timestamp: new Date(),
      count,
    };

    logger.error({
      timestamp: new Date().toISOString(),
      severity: this.mapSeverity(rule.severity),
      category: "alert",
      code: `ALERT_${rule.id}`,
      message: `[${rule.name}] ${event.message}`,
      context: {
        alertRule: rule.id,
        alertName: rule.name,
        alertSeverity: rule.severity,
        errorCount: count,
        thresholdExceeded: this.isThresholdExceeded(event.code),
      },
      correlationId: event.correlationId,
      source: event.source,
      recoverable: true,
    });
  }

  private mapSeverity(alertSeverity: AlertSeverity): ErrorSeverity {
    switch (alertSeverity) {
      case "critical":
        return "critical";
      case "warning":
        return "high";
      case "info":
        return "medium";
      default:
        return "medium";
    }
  }

  resetCounts(): void {
    this.errorCounts.clear();
    this.lastErrorTimes.clear();
  }
}

export const alertRouter = new AlertRouterImpl();

export const routeAlert = (event: ErrorEvent): void => {
  alertRouter.route(event);
};