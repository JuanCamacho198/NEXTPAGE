import { render, screen } from "@testing-library/svelte";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import ReadingStatsPanel from "./ReadingStatsPanel.svelte";
import type { ReadingStatsSummaryDto } from "$lib/types";
import { getReadingStats } from "$lib/api/tauriClient";

const t = (key: string) => {
  const dictionary: Record<string, string> = {
    "stats.title": "Reading Stats",
    "stats.refresh": "Refresh",
    "stats.scope": "Scope",
    "stats.global": "Global",
    "stats.loading": "Loading stats...",
    "stats.unavailable": "Stats unavailable.",
    "stats.minutes": "Minutes",
    "stats.sessions": "Sessions",
    "stats.started": "Started",
    "stats.completed": "Completed",
    "stats.averageProgress": "Average progress",
  };

  return dictionary[key] ?? key;
};

vi.mock("../../tauriClient", () => ({
  getReadingStats: vi.fn(),
}));

const mockedGetReadingStats = vi.mocked(getReadingStats);

describe("ReadingStatsPanel", () => {
  it("renders global and per-book stats values from command response", async () => {
    const stats: ReadingStatsSummaryDto = {
      totalMinutesRead: 125,
      totalSessions: 8,
      booksStarted: 3,
      booksCompleted: 1,
      avgProgressPercentage: 62.4,
    };
    mockedGetReadingStats.mockResolvedValueOnce(stats);

    const response = await getReadingStats();
    const onRefresh = vi.fn();

    render(ReadingStatsPanel, {
      stats: response,
      isLoading: false,
      disabledReason: null,
      selectedBookTitle: "Book One",
      onRefresh,
      t,
    });

    expect(screen.getByText("125")).toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText(/62%/)).toBeInTheDocument();
    expect(screen.getByText(/Scope: Book One/)).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Refresh" }));
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });
});
