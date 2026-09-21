import { invoke } from '@tauri-apps/api/core';
import { afterEach, beforeEach, vi, type Mock } from 'vitest';

export type InvokeStubResponse = unknown;
export type InvokeStubHandler = (
  args?: Record<string, unknown>,
) => InvokeStubResponse | Promise<InvokeStubResponse>;

const stubs = new Map<string, InvokeStubResponse | InvokeStubHandler>();

function invokeMock(): Mock {
  return invoke as unknown as Mock;
}

function router(command: string, args?: Record<string, unknown>): Promise<unknown> {
  if (!stubs.has(command)) return Promise.resolve(undefined);
  const stub = stubs.get(command) as InvokeStubResponse | InvokeStubHandler;
  const value = typeof stub === 'function' ? stub(args) : stub;
  return Promise.resolve(value);
}

export function stubInvoke(
  channel: string,
  response: InvokeStubResponse | InvokeStubHandler,
): void {
  if (stubs.size === 0 && invokeMock().getMockImplementation() !== router) {
    invokeMock().mockImplementation(router);
  }
  stubs.set(channel, response);
}

export function unstubInvoke(channel: string): void {
  stubs.delete(channel);
}

export function resetInvokeStubs(): void {
  stubs.clear();
  invokeMock().mockClear();
  invokeMock().mockImplementation(router);
}

export function invokeStubCalls(channel?: string): Array<[string, unknown?]> {
  const calls = invokeMock().mock.calls as Array<[string, unknown?]>;
  return channel ? calls.filter(([cmd]) => cmd === channel) : calls;
}

export function useInvokeStubs(): void {
  beforeEach(() => resetInvokeStubs());
  afterEach(() => resetInvokeStubs());
}
