import { render, screen } from "@testing-library/svelte";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import LibraryView from "./LibraryView.svelte";
import type { LibraryBookDto } from "../../types";
import { listLibraryBooks } from "../../tauriClient";

vi.mock("../../tauriClient", () => ({
  listLibraryBooks: vi.fn(),
}));

const mockedListLibraryBooks = vi.mocked(listLibraryBooks);

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
    });

    expect(screen.getByText("Book One")).toBeInTheDocument();
    expect(screen.getByText(/Author One/)).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Grid" }));
    expect(onToggleView).toHaveBeenCalledWith("grid");
  });
});
