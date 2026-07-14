import { getCurrentWebviewWindow } from '@tauri-apps/api/webviewWindow';

export function createTitlebarState() {
  let isMaximized = $state(false);
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
    init,
    destroy,
    handleMinimize,
    handleMaximize,
    handleClose,
  };
}

export const titlebarState = createTitlebarState();
