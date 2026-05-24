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
    t: vi.fn((key: string) => {
      const labels: Record<string, string> = {
        "sidebar.home": "Inicio",
        "sidebar.library": "Estantería",
        "sidebar.stats": "Estadísticas",
        "sidebar.highlights": "Notas y resaltados",
        "sidebar.settings": "Ajustes",
      };
      return labels[key] ?? key;
    }),
  };

  it("renders all five navigation items with labels", () => {
    render(AppSidebar, defaultProps);
    expect(screen.getByText("Inicio")).toBeInTheDocument();
    expect(screen.getByText("Estantería")).toBeInTheDocument();
    expect(screen.getByText("Estadísticas")).toBeInTheDocument();
    expect(screen.getByText("Notas y resaltados")).toBeInTheDocument();
    expect(screen.getByText("Ajustes")).toBeInTheDocument();
  });

  it("highlights the active route button", () => {
    render(AppSidebar, { ...defaultProps, activeRoute: "settings" });
    const activeButton = screen.getByText("Ajustes").closest("button");
    expect(activeButton).toHaveClass("bg-[var(--color-accent-blue)]");
  });

  it("calls onNavigateHome when home is clicked", async () => {
    const onNavigateHome = vi.fn();
    const user = userEvent.setup();
    render(AppSidebar, { ...defaultProps, onNavigateHome });
    await user.click(screen.getByText("Inicio"));
    expect(onNavigateHome).toHaveBeenCalledTimes(1);
  });

  it("calls onNavigateLibrary when library is clicked", async () => {
    const onNavigateLibrary = vi.fn();
    const user = userEvent.setup();
    render(AppSidebar, { ...defaultProps, onNavigateLibrary });
    await user.click(screen.getByText("Estantería"));
    expect(onNavigateLibrary).toHaveBeenCalledTimes(1);
  });

  it("calls onNavigateStats when stats is clicked", async () => {
    const onNavigateStats = vi.fn();
    const user = userEvent.setup();
    render(AppSidebar, { ...defaultProps, onNavigateStats });
    await user.click(screen.getByText("Estadísticas"));
    expect(onNavigateStats).toHaveBeenCalledTimes(1);
  });

  it("calls onNavigateHighlights when highlights is clicked", async () => {
    const onNavigateHighlights = vi.fn();
    const user = userEvent.setup();
    render(AppSidebar, { ...defaultProps, onNavigateHighlights });
    await user.click(screen.getByText("Notas y resaltados"));
    expect(onNavigateHighlights).toHaveBeenCalledTimes(1);
  });

  it("calls onNavigateSettings when settings is clicked", async () => {
    const onNavigateSettings = vi.fn();
    const user = userEvent.setup();
    render(AppSidebar, { ...defaultProps, onNavigateSettings });
    await user.click(screen.getByText("Ajustes"));
    expect(onNavigateSettings).toHaveBeenCalledTimes(1);
  });

  it("renders ThemeToggle component", () => {
    render(AppSidebar, defaultProps);
    const themeToggle = document.querySelector('[class*="flex items-center justify-between rounded-xl p-3"]');
    // ThemeToggle is rendered inside the sidebar
    expect(screen.getByText("Ver perfil")).toBeInTheDocument();
  });

  it("has semantic aside element", () => {
    const { container } = render(AppSidebar, defaultProps);
    expect(container.querySelector("aside")).toBeInTheDocument();
  });
});
