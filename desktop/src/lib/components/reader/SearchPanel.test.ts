import { fireEvent, render, screen } from "@testing-library/svelte";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import SearchPanel from "./SearchPanel.svelte";
import type { SearchBookTextResponse } from "../../types";
import { searchBookText } from "../../tauriClient";

const t = (key: string) => {
  const dictionary: Record<string, string> = {
    "search.title": "In-Book Search",
    "search.placeholder": "Search text in this book",
    "search.search": "Search",
    "search.searching": "Searching...",
    "search.locator": "Locator",
    "search.page": "Page",
    "search.matches": "matches",
    "search.prev": "Prev",
    "search.next": "Next",
    "search.noMatches": "No matches found for this query.",
  };

  return dictionary[key] ?? key;
};

vi.mock("../../tauriClient", () => ({
  searchBookText: vi.fn(),
}));

const mockedSearchBookText = vi.mocked(searchBookText);

describe("SearchPanel", () => {
  it("shows no-match state and emits search/jump callbacks", async () => {
    mockedSearchBookText.mockResolvedValueOnce({
      items: [],
      total: 0,
      page: 1,
      pageSize: 200,
    });

    const noMatches = await searchBookText({
      bookId: "book-1",
      query: "absent",
      page: 1,
      pageSize: 200,
    });

    const onSearch = vi.fn();
    const onJump = vi.fn();

    const rendered = render(SearchPanel, {
      bookId: "book-1",
      disabledReason: null,
      isSearching: false,
      response: noMatches,
      onSearch,
      onJump,
      t,
    });

    expect(screen.getByText("No matches found for this query.")).toBeInTheDocument();

    const user = userEvent.setup();
    const input = screen.getByPlaceholderText("Search text in this book");
    await user.type(input, "needle");
    await fireEvent.submit(input.closest("form") as HTMLFormElement);
    expect(onSearch).toHaveBeenCalledWith("needle", 1);

    const withMatches: SearchBookTextResponse = {
      items: [
        {
          chunkId: "chunk-1",
          bookId: "book-1",
          locator: "epubcfi(/6/2)",
          snippet: "...needle...",
          rank: 0.2,
        },
      ],
      total: 1,
      page: 1,
      pageSize: 200,
    };

    rendered.unmount();
    render(SearchPanel, {
      bookId: "book-1",
      disabledReason: null,
      isSearching: false,
      response: withMatches,
      onSearch,
      onJump,
      t,
    });
    await user.click(screen.getByRole("button", { name: /needle/ }));

    expect(onJump).toHaveBeenCalledTimes(1);
    expect(onJump.mock.calls[0][0]).toMatchObject({
      locator: "epubcfi(/6/2)",
    });
  });
});
