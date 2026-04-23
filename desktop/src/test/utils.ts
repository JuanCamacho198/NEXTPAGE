// Test utilities for vitest 4.x compatibility
import { vi, describe, expect, it, beforeEach, afterEach, beforeAll, afterAll } from "vitest";

// Since vi.mocked is not available in vitest 4.x with globals: true,
// we use a type cast workaround
type MockFn = ReturnType<typeof vi.fn>;

// Re-export commonly used vitest utilities
export { describe, expect, it, beforeEach, afterEach, vi, beforeAll, afterAll };

// Mock helper - works around missing vi.mocked in vitest 4.x
export function mockFn<T>(fn: unknown): MockFn {
  return vi.fn() as MockFn;
}

// Type-safe mock for imported functions
export function mockImport<T>(module: { default?: T; [key: string]: T }): T {
  return module as T;
}