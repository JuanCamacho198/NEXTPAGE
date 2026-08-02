import { getCurrentWebviewWindow } from '@tauri-apps/api/webviewWindow';

export function createTitlebarState() {
  let isMaximized = $state(false);
  /**
   * Whether the custom titlebar should render (Windows only). Set once by the
   * App.svelte platform gate; shared by App.svelte (render) and WelcomeScreen
   * (header adaptation). Defaults to false.
   *
   * Implemented as a closure accessor pair (not a bare exported `$state`)
   * because Svelte 5 cannot export state that is reassigned from another
   * module — the compiler only transforms runes within a single file.
   */
  let isCustomTitlebar = $state(false);
  let unlistenResize: (() => void) | null = null;

  async function init() {
    const appWindow = getCurrentWebviewWindow();
    isMaximized = await appWindow.isMaximized();
    unlistenResize = await appWindow.onResized(async () => {
      isMaximized = await appWindow.isMaximized();
    });
  }

  function destroy() {
    unlistenResize?.();
    unlistenResize = null;
  }

  async function handleMinimize() {
    await getCurrentWebviewWindow().minimize();
  }

  async function handleMaximize() {
    await getCurrentWebviewWindow().toggleMaximize();
  }

  async function handleClose() {
    await getCurrentWebviewWindow().close();
  }

  return {
    get isMaximized() { return isMaximized; },
    get isCustomTitlebar() { return isCustomTitlebar; },
    set isCustomTitlebar(value: boolean) { isCustomTitlebar = value; },
    init,
    destroy,
    handleMinimize,
    handleMaximize,
    handleClose,
  };
}

export const titlebarState = createTitlebarState();
