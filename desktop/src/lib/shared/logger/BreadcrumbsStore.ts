import type { BreadcrumbEntry, BreadcrumbType } from "./breadcrumbTypes";

const MAX_BREADCRUMBS = 100;

class BreadcrumbsStoreImpl {
  private sessionId: string;
  private buffer: BreadcrumbEntry[] = [];

  constructor() {
    this.sessionId = crypto.randomUUID();
  }

  getSessionId(): string {
    return this.sessionId;
  }

  add(type: BreadcrumbType, label: string, data?: Record<string, unknown>): BreadcrumbEntry {
    const entry: BreadcrumbEntry = {
      id: crypto.randomUUID(),
      sessionId: this.sessionId,
      timestamp: new Date().toISOString(),
      type,
      label,
      data,
    };

    this.buffer.push(entry);

    if (this.buffer.length > MAX_BREADCRUMBS) {
      this.buffer.shift();
    }

    return entry;
  }

  getAll(): BreadcrumbEntry[] {
    return [...this.buffer];
  }

  getByType(type: BreadcrumbType): BreadcrumbEntry[] {
    return this.buffer.filter((entry) => entry.type === type);
  }

  flush(): BreadcrumbEntry[] {
    const entries = [...this.buffer];
    this.buffer = [];
    return entries;
  }

  clear(): void {
    this.buffer = [];
  }
}

export const breadcrumbsStore = new BreadcrumbsStoreImpl();

export const captureBreadcrumb = (
  type: BreadcrumbType,
  label: string,
  data?: Record<string, unknown>
): BreadcrumbEntry => {
  return breadcrumbsStore.add(type, label, data);
};