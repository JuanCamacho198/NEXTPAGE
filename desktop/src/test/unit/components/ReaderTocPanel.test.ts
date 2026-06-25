import { render, screen } from "@testing-library/svelte";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import ReaderTocPanel from "$lib/features/reader/chrome/ReaderTocPanel.svelte";

const t = (key: string, _params?: Record<string, string | number>) => {
  const dictionary: Record<string, string> = {
    "reader.tabla_contenidos": "Table of Contents",
    "reader.toc_empty": "No chapters yet",
  };

  return dictionary[key] ?? key;
};

describe("ReaderTocPanel", () => {
  const defaultEntries = [
    { id: "ch1", title: "Chapter 1", depth: 0 },
    { id: "ch1-1", title: "Section 1.1", depth: 1 },
    { id: "ch2", title: "Chapter 2", depth: 0 },
    { id: "ch2-1", title: "Section 2.1", depth: 1, active: true },
  ];

  const defaultProps = {
    open: true,
    entries: defaultEntries,
    t,
    onNavigate: vi.fn(),
    onClose: vi.fn(),
  };

  it("renders TOC heading when open", () => {
    render(ReaderTocPanel, defaultProps);
    expect(screen.getByText("Table of Contents")).toBeInTheDocument();
  });

  it("renders all TOC entries", () => {
    render(ReaderTocPanel, defaultProps);
    expect(screen.getByText("Chapter 1")).toBeInTheDocument();
    expect(screen.getByText("Chapter 2")).toBeInTheDocument();
    expect(screen.getByText("Section 1.1")).toBeInTheDocument();
    expect(screen.getByText("Section 2.1")).toBeInTheDocument();
  });

  it("shows empty state when no entries", () => {
    render(ReaderTocPanel, { ...defaultProps, entries: [] });
    expect(screen.getByText("No chapters yet")).toBeInTheDocument();
  });

  it("does not render when closed", () => {
    render(ReaderTocPanel, { ...defaultProps, open: false });
    expect(screen.queryByText("Table of Contents")).not.toBeInTheDocument();
  });

  it("calls onNavigate when a chapter is clicked", async () => {
    const onNavigate = vi.fn();
    const user = userEvent.setup();
    render(ReaderTocPanel, { ...defaultProps, onNavigate });
    await user.click(screen.getByText("Chapter 2"));
    expect(onNavigate).toHaveBeenCalledTimes(1);
    expect(onNavigate).toHaveBeenCalledWith(
      expect.objectContaining({ id: "ch2", title: "Chapter 2" })
    );
  });

  it("calls onClose when backdrop is clicked", async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(ReaderTocPanel, { ...defaultProps, onClose });
    const backdrop = screen.getByRole("presentation");
    await user.click(backdrop);
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
