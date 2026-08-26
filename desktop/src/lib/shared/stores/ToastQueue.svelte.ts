/**
 * Global toast queue (REQ-04/05/06) — Svelte 5 runes store.
 *
 * Module-level `$state` queue so any module (SettingsPanel, AppState) can push
 * toasts that survive navigation: ToastHost renders the queue from AppModals,
 * which lives outside AppRouter (it stays mounted across route changes,
 * including the welcome branch).
 */
export type ToastType = 'success' | 'info' | 'error';

export interface ToastItem {
  id: number;
  type: ToastType;
  message: string;
}

// ─── Reactive State ───────────────────────────────────────────────────

let queue: ToastItem[] = $state([]);
let nextId = 1;

// ─── Public API ───────────────────────────────────────────────────────

export function pushToast(type: ToastType, message: string): void {
  queue.push({ id: nextId++, type, message });
}

export function dismiss(id: number): void {
  const index = queue.findIndex((item) => item.id === id);
  if (index >= 0) {
    queue.splice(index, 1);
  }
}

export const toastQueue = {
  get items(): ToastItem[] {
    return queue;
  },
};
