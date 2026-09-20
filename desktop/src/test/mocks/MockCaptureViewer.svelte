<script module lang="ts">
  // A viewer double for the reader capture tests: it emits a whole selection
  // payload, including the EPUB evidence fields, so the parent's glue can be
  // driven without a real iframe. The payload lives in module scope so a test
  // can choose it before clicking the emit button.
  export type MockCaptureSelection = {
    text: string;
    bounds: { left: number; top: number; right: number; bottom: number };
    container: { left: number; top: number; width: number; height: number };
    placement: string;
    rects: Array<{ left: number; top: number; width: number; height: number }>;
    pageNumber: number;
    cfi: string | null;
    quote: string | null;
    chapterTitle: string | null;
  };

  function defaultSelection(): MockCaptureSelection {
    return {
      text: 'Abyss',
      bounds: { left: 20, top: 40, right: 180, bottom: 80 },
      container: { left: 100, top: 200, width: 320, height: 480 },
      placement: 'above',
      rects: [],
      pageNumber: 1,
      cfi: 'epubcfi(/6/4!/4/2/1:0)',
      quote: 'The abyss stared back at the diver.',
      chapterTitle: 'Chapter 3',
    };
  }

  let current: MockCaptureSelection = defaultSelection();

  export function setCaptureSelection(next: Partial<MockCaptureSelection> = {}): void {
    current = { ...defaultSelection(), ...next };
  }

  export function resetCaptureSelection(): void {
    current = defaultSelection();
  }
</script>

<script lang="ts">
  type Props = {
    onselection?: (event: MockCaptureSelection) => void;
    onToggleFullscreen?: () => void;
  };

  let { onselection, onToggleFullscreen }: Props = $props();
</script>

<div data-testid="mock-capture-viewer"></div>
<button type="button" data-testid="mock-capture-select" onclick={() => onselection?.(current)}>
  Emit Capture Selection
</button>
<button type="button" data-testid="mock-capture-toggle" onclick={() => onToggleFullscreen?.()}>
  Toggle Fullscreen
</button>
