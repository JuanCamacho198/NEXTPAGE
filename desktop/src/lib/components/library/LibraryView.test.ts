import { render, screen } from "@testing-library/svelte";
import userEvent from "@testing-library/user-event";
import { readFile } from "@tauri-apps/plugin-fs";
import { describe, expect, it, vi } from "vitest";
import LibraryView from "./LibraryView.svelte";
import type { LibraryBookDto } from "$lib/types";
import { listLibraryBooks } from "$lib/api/tauriClient";

vi.mock("@tauri-apps/api/core", () => ({
  convertFileSrc: vi.fn((path: string) => `asset://localhost/${path}`),
}));

const t = (key: string, params?: Record<string, string | number>) => {
  if (key === "library.optionsFor") {
    return `Options for ${params?.title ?? ""}`;
  }

  const dictionary: Record<string, string> = {
    "library.title": "Library",
    "library.list": "List",
    "library.grid": "Grid",
    "library.loading": "Loading library...",
    "library.empty": "No books available.",
    "library.noCover": "No cover",
    "library.cover": "Cover",
    "library.hide": "Hide from library",
    "library.open": "Open",
    "library.updated": "Updated",
    "library.min": "min",
    "app.unknownAuthor": "Unknown author",
    "settings.unknownBook": "Unknown",
  };

  return dictionary[key] ?? key;
};

vi.mock("../../tauriClient", () => ({
  listLibraryBooks: vi.fn(),
}));

vi.mock("@tauri-apps/plugin-fs", () => ({
  readFile: vi.fn(),
}));

const mockedListLibraryBooks = vi.mocked(listLibraryBooks);
const mockedReadFile = vi.mocked(readFile);

describe("LibraryView", () => {
  it("renders backend metadata consistently in list and grid views", async () => {
    const books: LibraryBookDto[] = [
      {
        id: "book-1",
        title: "Book One",
        author: "Author One",
        format: "epub",
        currentPage: 12,
        totalPages: 240,
        progressPercentage: 50,
        coverPath: null,
        minutesRead: 34,
        updatedAt: new Date().toISOString(),
      },
    ];
    mockedListLibraryBooks.mockResolvedValueOnce(books);

    const response = await listLibraryBooks(1);
    const onToggleView = vi.fn();

    render(LibraryView, {
      books: response,
      selectedBookId: null,
      isLoading: false,
      disabledReason: null,
      viewMode: "list",
      onToggleView,
      t,
    });

    expect(screen.getByText("Book One")).toBeInTheDocument();
    expect(screen.getByText(/Author One/)).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Grid" }));
    expect(onToggleView).toHaveBeenCalledWith("grid");
  });

  it("exposes hide action from item menu", async () => {
    const onHide = vi.fn();
    const user = userEvent.setup();

    render(LibraryView, {
      books: [
        {
          id: "book-hide-1",
          title: "Book Hide",
          author: "Author Hide",
          format: "pdf",
          currentPage: 1,
          totalPages: 10,
          progressPercentage: 10,
          coverPath: null,
          minutesRead: 1,
          updatedAt: new Date().toISOString(),
        },
      ] satisfies LibraryBookDto[],
      selectedBookId: null,
      isLoading: false,
      disabledReason: null,
      viewMode: "list",
      onHide,
      t,
    });

    const bookRow = screen.getByText("Book Hide").closest("li");
    expect(bookRow).not.toBeNull();
    const menuButton = bookRow?.querySelector("button.rounded-md.border") as HTMLButtonElement;
    await user.click(menuButton);
    await user.click(screen.getByRole("button", { name: "Hide from library" }));

    expect(onHide).toHaveBeenCalledTimes(1);
    expect(onHide.mock.calls[0][0].id).toBe("book-hide-1");
  });

  it("falls back to placeholder when cover image fails to load", async () => {
    mockedReadFile.mockRejectedValueOnce(new Error("cover not found"));

    render(LibraryView, {
      books: [
        {
          id: "book-cover-error",
          title: "Libro con portada",
          author: "Autor",
          format: "pdf",
          currentPage: 3,
          totalPages: 10,
          progressPercentage: 30,
          coverPath: "C:/covers/book-cover-error.png",
          minutesRead: 8,
          updatedAt: new Date().toISOString(),
        },
      ] satisfies LibraryBookDto[],
      selectedBookId: null,
      isLoading: false,
      disabledReason: null,
      viewMode: "grid",
      t,
    });

    expect(await screen.findByText("No cover")).toBeInTheDocument();
  });

  it("keeps long spanish labels constrained inside card layout", () => {
    render(LibraryView, {
      books: [
        {
          id: "book-overflow-1",
          title: "Este es un titulo extremadamente largo para verificar truncado en tarjetas de biblioteca",
          author: "Autor con nombre considerablemente largo para stress test",
          format: "pdf",
          currentPage: 123,
          totalPages: 456,
          progressPercentage: 27,
          coverPath: null,
          minutesRead: 120,
          updatedAt: new Date().toISOString(),
        },
      ] satisfies LibraryBookDto[],
      selectedBookId: null,
      isLoading: false,
      disabledReason: null,
      viewMode: "list",
      t,
    });

    expect(screen.getByText(/extremadamente largo/i)).toHaveClass("line-clamp-2");
    expect(screen.getByText(/nombre considerablemente largo/i)).toHaveClass("truncate");
    expect(screen.getByText(/Updated/i)).toHaveClass("truncate");
  });
});
