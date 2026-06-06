/**
 * Shared reactive reader error state.
 * Eliminates prop drilling of readerError through component hierarchy.
 *
 * Usage:
 *   import { getReaderError, setReaderError, clearReaderError } from "$lib/stores/readerErrorState.svelte";
 */

let error = $state<string | null>(null);

export function setReaderError(msg: string | null): void {
  error = msg;
}

export function clearReaderError(): void {
  error = null;
}

export function getReaderError(): string | null {
  return error;
}
