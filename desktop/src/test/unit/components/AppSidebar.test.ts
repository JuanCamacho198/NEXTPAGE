import { render, screen } from "@testing-library/svelte";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import AppSidebar from "$lib/components/layout/AppSidebar.svelte";

describe("AppSidebar", () => {
  const defaultProps = {
    activeRoute: "home" as const,
    onNavigateHome: vi.fn(),
    onNavigateLibrary: vi.fn(),
    onNavigateStats: vi.fn(),
    onNavigateHighlights: vi.fn(),
    onNavigateSettings: vi.fn(),
    t: fakeT,
  });

  function findNavButton(name: string): HTMLButtonElement | null {
    const buttons = screen.getAllByRole("button");
    return buttons.find((btn) => btn.textContent?.trim().includes(name)) ?? null;
  }

  it("renders all five navigation items with labels", () => {
    render(AppSidebar, defaultProps);
    // Items appear both as visible text and in tooltips — check presence via getAllByText
    expect(screen.getAllByText("Inicio").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Estantería").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Estadísticas").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Notas y resaltados").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Ajustes").length).toBeGreaterThanOrEqual(1);
  });

  it("highlights the active route button", () => {
    render(AppSidebar, { ...defaultProps, activeRoute: "settings" });
    const activeButton = findNavButton("Ajustes");
    expect(activeButton).not.toBeNull();
    expect(activeButton!.className).toContain("accent");
  });

  it("calls onNavigateHome when home is clicked", async () => {
    const onNavigateHome = vi.fn();
    const user = userEvent.setup();
    render(AppSidebar, { ...defaultProps, onNavigateHome });
    const btn = findNavButton("Inicio");
    expect(btn).not.toBeNull();
    await user.click(btn!);
    expect(onNavigateHome).toHaveBeenCalledTimes(1);
  });

  it("calls onNavigateLibrary when library is clicked", async () => {
    const onNavigateLibrary = vi.fn();
    const user = userEvent.setup();
    render(AppSidebar, { ...defaultProps, onNavigateLibrary });
    const btn = findNavButton("Estantería");
    expect(btn).not.toBeNull();
    await user.click(btn!);
    expect(onNavigateLibrary).toHaveBeenCalledTimes(1);
  });

  it("calls onNavigateStats when stats is clicked", async () => {
    const onNavigateStats = vi.fn();
    const user = userEvent.setup();
    render(AppSidebar, { ...defaultProps, onNavigateStats });
    const btn = findNavButton("Estadísticas");
    expect(btn).not.toBeNull();
    await user.click(btn!);
    expect(onNavigateStats).toHaveBeenCalledTimes(1);
  });

  it("calls onNavigateHighlights when highlights is clicked", async () => {
    const onNavigateHighlights = vi.fn();
    const user = userEvent.setup();
    render(AppSidebar, { ...defaultProps, onNavigateHighlights });
    const btn = findNavButton("Notas y resaltados");
    expect(btn).not.toBeNull();
    await user.click(btn!);
    expect(onNavigateHighlights).toHaveBeenCalledTimes(1);
  });

  it("calls onNavigateSettings when settings is clicked", async () => {
    const onNavigateSettings = vi.fn();
    const user = userEvent.setup();
    render(AppSidebar, { ...defaultProps, onNavigateSettings });
    const btn = findNavButton("Ajustes");
    expect(btn).not.toBeNull();
    await user.click(btn!);
    expect(onNavigateSettings).toHaveBeenCalledTimes(1);
  });

  it("has semantic aside element", () => {
    const { container } = render(AppSidebar, defaultProps);
    expect(container.querySelector("aside")).toBeInTheDocument();
  });
});
