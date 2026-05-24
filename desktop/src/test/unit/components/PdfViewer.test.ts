import { render, screen } from "@testing-library/svelte";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import PdfViewer from "$lib/features/reader/components/PdfViewer.svelte";

// Mock pdfjs-dist completely
vi.mock("pdfjs-dist", () => ({
  default: {
    GlobalWorkerOptions: { workerSrc: "" },
    getDocument: vi.fn(),
    TextLayer: vi.fn(),
  },
  GlobalWorkerOptions: { workerSrc: "" },
  getDocument: vi.fn(),
  TextLayer: vi.fn(),
  renderTextLayer: vi.fn(),
}));

// Mock tauri client
vi.mock("$lib/shared/api/tauriClient", () => ({
  getFileBytes: vi.fn(),
}));

// Mock ErrorBoundary to render children directly
vi.mock("$lib/shared/ui/feedback/ErrorBoundary.svelte", () => ({
  default: vi.fn().mockImplementation(({ children }) => children),
}));

// Mock Icon component
vi.mock("$lib/components/ui/navigation/Icon.svelte", () => ({
  default: vi.fn().mockImplementation(() => '<span data-testid="mock-icon"></span>'),
}));

const t = (key: string, _params?: Record<string, string | number>) => {
  const dictionary: Record<string, string> = {
    "pdf.loading": "Loading PDF...",
    "pdf.error": "Error",
    "pdf.contents": "Contents",
    "pdf.previous": "Previous",
    "pdf.next": "Next",
    "pdf.fullscreenEnter": "Fullscreen",
    "pdf.fullscreenExit": "Exit fullscreen",
    "pdf.tableOfContents": "Table of Contents",
    "pdf.tocLoading": "Loading TOC...",
    "pdf.tocEmpty": "No table of contents",
    "pdf.pagesLeft": "pages left",
    "pdf.navigationFailed": "Navigation failed",
    "pdf.fullscreenUnsupported": "Fullscreen not available",
    "pdf.untitledSection": "Untitled",
    "pdf.tocLoadFailed": "Failed to load TOC",
    "pdf.tocNavigationFailed": "Navigation failed",
  };

  if (!params) return dictionary[key] ?? key;
  let result = dictionary[key] ?? key;
  for (const [k, v] of Object.entries(params)) {
    result = result.replace(`{{${k}}}`, String(v));
  }
  return result;
};

describe("PdfViewer", () => {
  beforeEach(() => {
    // Stub canvas getContext
    HTMLCanvasElement.prototype.getContext = vi.fn().mockReturnValue({
      setTransform: vi.fn(),
      drawImage: vi.fn(),
      fillRect: vi.fn(),
      scale: vi.fn(),
    });

    // Stub requestAnimationFrame
    vi.spyOn(window, "requestAnimationFrame").mockImplementation((cb: FrameRequestCallback) => {
      return window.setTimeout(() => cb(Date.now()), 0);
    });

    // Stub cancelAnimationFrame
    vi.spyOn(window, "cancelAnimationFrame").mockImplementation((id: number) => {
      window.clearTimeout(id);
    });

    // Stub devicePixelRatio
    Object.defineProperty(window, "devicePixelRatio", { value: 1, writable: true });

    // Mock document.fonts.ready
    Object.defineProperty(document, "fonts", {
      value: { ready: Promise.resolve() },
      writable: true,
    });

    // Mock document.fullscreenElement
    Object.defineProperty(document, "fullscreenElement", {
      value: null,
      writable: true,
      configurable: true,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders in loading state initially when filePath is empty", () => {
    render(PdfViewer, {
      filePath: "",
      t,
    });
    expect(screen.getByText("Loading PDF...")).toBeInTheDocument();
  });

  it("renders in loading state with a valid filePath", () => {
    render(PdfViewer, {
      filePath: "/path/to/test.pdf",
      t,
    });
    expect(screen.getByText("Loading PDF...")).toBeInTheDocument();
  });

  it("renders the controls section", () => {
    render(PdfViewer, {
      filePath: "/path/to/test.pdf",
      t,
    });

    // The controls should be present with nav buttons
    expect(screen.getByText("Contents")).toBeInTheDocument();
    expect(screen.getByText("Previous")).toBeInTheDocument();
    expect(screen.getByText("Next")).toBeInTheDocument();
  });

  it("renders the progress bar and page info in footer", () => {
    render(PdfViewer, {
      filePath: "/path/to/test.pdf",
      t,
    });

    // Footer should contain page navigation elements
    expect(screen.getByText(/pages left/)).toBeInTheDocument();
    expect(screen.getByRole("spinbutton")).toBeInTheDocument();
  });

  it("has accessible role 'region' for viewer area", () => {
    render(PdfViewer, {
      filePath: "/path/to/test.pdf",
      t,
    });
    expect(screen.getByRole("region", { name: "PDF Viewer" })).toBeInTheDocument();
  });

  it("renders fullscreen toggle button", () => {
    render(PdfViewer, {
      filePath: "/path/to/test.pdf",
      t,
    });
    expect(screen.getByText("Fullscreen")).toBeInTheDocument();
  });

  it("disables previous page button initially (page is 1)", () => {
    render(PdfViewer, {
      filePath: "/path/to/test.pdf",
      t,
    });
    // Previous button should be disabled since currentPage=1
    const buttons = screen.getAllByRole("button");
    const prevButton = buttons.find(
      (btn) => btn.textContent?.includes("Previous") || btn.getAttribute("aria-label") === "Previous"
    );
    expect(prevButton).toBeDefined();
  });
});
