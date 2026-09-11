/**
 * Tauri deep-link event wiring.
 *
 * Lives in the Tauri adapter layer because it touches the Tauri event API.
 * Feature modules stay free of Tauri imports so they remain importable without a
 * running Tauri host; the caller decides what each URL means.
 */
import { listen } from '@tauri-apps/api/event';

const DEEP_LINK_INSTALL_EVENT = 'deep-link-install';

let listenersRegistered = false;

export type DeepLinkInstallHandler = (url: string) => void;

/**
 * Warm-start listener: the single-instance Rust callback emits
 * `deep-link-install` with the forwarded URL. Idempotent, so both the cold-start
 * wiring and an App mount can call it safely.
 */
export async function registerDeepLinkListener(onUrl: DeepLinkInstallHandler): Promise<void> {
  if (listenersRegistered) return;
  listenersRegistered = true;
  try {
    await listen<string>(DEEP_LINK_INSTALL_EVENT, (event) => {
      onUrl(event.payload);
    });
  } catch {
    listenersRegistered = false;
  }
}
