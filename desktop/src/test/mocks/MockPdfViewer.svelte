<script lang="ts">
  type Props = {
    isFullscreen?: boolean;
    onToggleFullscreen?: () => void;
    onselection?: (event: {
      text: string;
      bounds: { left: number; top: number; right: number; bottom: number };
      container: { left: number; top: number; width: number; height: number };
      placement: "above" | "below";
    }) => void;
  };

  let { isFullscreen = false, onToggleFullscreen, onselection }: Props = $props();
  let fullscreenState = $state(false);

  $effect(() => {
    fullscreenState = isFullscreen;
  });

  function emitSelection(): void {
    onselection?.({
      text: "Selection text",
      bounds: { left: 20, top: 40, right: 180, bottom: 80 },
      container: { left: 100, top: 200, width: 320, height: 480 },
      placement: "above",
    });
  }

  async function handleToggle(): Promise<void> {
    const isActive = !!document.fullscreenElement;
    if (!isActive) {
      await document.documentElement.requestFullscreen?.();
      Object.defineProperty(document, "fullscreenElement", {
        configurable: true,
        value: document.documentElement,
      });
      fullscreenState = true;
    } else {
      await document.exitFullscreen?.();
      Object.defineProperty(document, "fullscreenElement", {
        configurable: true,
        value: null,
      });
      fullscreenState = false;
    }

    document.dispatchEvent(new Event("fullscreenchange"));
    onToggleFullscreen?.();
  }
</script>

<div data-testid="mock-pdfviewer" data-fullscreen={String(fullscreenState)}></div>
<button type="button" data-testid="mock-pdfviewer-toggle" onclick={handleToggle}>
  Toggle Fullscreen
</button>
<button type="button" data-testid="mock-pdfviewer-select" onclick={emitSelection}>
  Emit Selection
</button>
