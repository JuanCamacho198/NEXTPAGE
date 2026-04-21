import { writable } from "svelte/store";
import type { ErrorEvent } from "../events/ErrorEvent";

interface ErrorState {
  currentError: ErrorEvent | null;
  showToast: boolean;
  showFallback: boolean;
}

const initialState: ErrorState = {
  currentError: null,
  showToast: false,
  showFallback: false,
};

function createErrorStateStore() {
  const { subscribe, set, update } = writable<ErrorState>(initialState);

  return {
    subscribe,

    setError(error: ErrorEvent) {
      update((state) => ({
        ...state,
        currentError: error,
        showToast: error.recoverable,
        showFallback: !error.recoverable,
      }));
    },

    showErrorToast(error: ErrorEvent) {
      update((state) => ({
        ...state,
        currentError: error,
        showToast: true,
        showFallback: false,
      }));
    },

    showErrorFallback(error: ErrorEvent) {
      update((state) => ({
        ...state,
        currentError: error,
        showToast: false,
        showFallback: true,
      }));
    },

    clearError() {
      update((state) => ({
        ...state,
        currentError: null,
        showToast: false,
        showFallback: false,
      }));
    },

    dismissToast() {
      update((state) => ({
        ...state,
        showToast: false,
        currentError: state.showFallback ? state.currentError : null,
      }));
    },

    reset() {
      set(initialState);
    },
  };
}

export const errorState = createErrorStateStore();