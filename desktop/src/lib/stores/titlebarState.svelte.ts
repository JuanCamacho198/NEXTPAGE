import { getCurrentWebviewWindow } from '@tauri-apps/api/webviewWindow';

export interface TitlebarState {
  get isMaximized(): boolean;
  get isCustomTitlebar(): boolean;
  set isCustomTitlebar(value: boolean);
  init(): Promise<void>;
  destroy(): void;
  handleMinimize(): Promise<void>;
  handleMaximize(): Promise<void>;
  handleClose(): Promise<void>;
}

export function createTitlebarState(): TitlebarState {
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

  async function init(): Promise<void> {
    const appWindow = getCurrentWebviewWindow();
    isMaximized = await appWindow.isMaximized();
    unlistenResize = await appWindow.onResized(async () => {
      isMaximized = await appWindow.isMaximized();
    });
  }

  function destroy(): void {
    unlistenResize?.();
    unlistenResize = null;
  }

  async function handleMinimize(): Promise<void> {
    await getCurrentWebviewWindow().minimize();
  }

  async function handleMaximize(): Promise<void> {
    await getCurrentWebviewWindow().toggleMaximize();
  }

  async function handleClose(): Promise<void> {
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
