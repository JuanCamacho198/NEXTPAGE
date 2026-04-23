import { describe, expect, it } from "vitest";
import {
  createShelfQueryState,
  getSafeProgressPercentage,
  getShelfQueryWarnings,
  isBookInProgress,
  parseShelfSmartQuery,
  partitionHomeBooks,
  promoteBookForReading,
  reconcileHomeState,
  selectShelfBooks,
  type HomeStateSnapshot,
} from "$lib/stores/homeState";

type TestBook = {
  id: string;
  title: string;
  author?: string;
  progressPercentage?: number | null;
  updatedAt?: string;
  createdAt?: string;
  lastReadAt?: string;
  fileSizeBytes?: number;
  isFavorite?: boolean;
  toRead?: boolean;
  completed?: boolean;
};

const makeState = (state: Partial<HomeStateSnapshot>): HomeStateSnapshot => {
  return {
    route: "home",
    previewBookId: null,
    activeReadingBookId: null,
    shelfDetailsBookId: null,
    ...state,
  };
};

describe("homeState progress guards", () => {
  it("normalizes null/undefined/non-finite progress values", () => {
    expect(getSafeProgressPercentage({ progressPercentage: undefined })).toBe(0);
    expect(getSafeProgressPercentage({ progressPercentage: null })).toBe(0);
    expect(getSafeProgressPercentage({ progressPercentage: Number.NaN })).toBe(0);
    expect(getSafeProgressPercentage({ progressPercentage: Number.POSITIVE_INFINITY })).toBe(0);
  });

  it("clamps percentage to the valid range", () => {
    expect(getSafeProgressPercentage({ progressPercentage: -5 })).toBe(0);
    expect(getSafeProgressPercentage({ progressPercentage: 37.5 })).toBe(37.5);
    expect(getSafeProgressPercentage({ progressPercentage: 150 })).toBe(100);
  });

  it("detects in-progress books deterministically", () => {
    expect(isBookInProgress({ progressPercentage: 0 })).toBe(false);
    expect(isBookInProgress({ progressPercentage: 0.1 })).toBe(true);
    expect(isBookInProgress({ progressPercentage: 99.9 })).toBe(true);
    expect(isBookInProgress({ progressPercentage: 100 })).toBe(false);
  });
});

describe("homeState section membership", () => {
  it("partitions books without duplication and filters duplicate ids", () => {
    const books: TestBook[] = [
      { id: "a", title: "A", progressPercentage: 0 },
      { id: "b", title: "B", progressPercentage: 10 },
      { id: "c", title: "C", progressPercentage: 100 },
      { id: "b", title: "B duplicate", progressPercentage: 30 },
    ];

    const { continueReadingBooks, myShelfBooks } = partitionHomeBooks(books);

    expect(continueReadingBooks.map((book) => book.id)).toEqual(["b"]);
    expect(myShelfBooks.map((book) => book.id)).toEqual(["a", "c"]);
    expect(new Set([...continueReadingBooks, ...myShelfBooks].map((book) => book.id)).size).toBe(3);
  });
});

describe("homeState read promotion", () => {
  it("promotes zero-progress books idempotently", () => {
    const books: TestBook[] = [{ id: "a", title: "A", progressPercentage: 0 }];

    const once = promoteBookForReading(books, "a");
    const twice = promoteBookForReading(once, "a");

    expect(once[0].progressPercentage).toBe(1);
    expect(twice[0].progressPercentage).toBe(1);
  });

  it("does not alter in-progress/completed books", () => {
    const inProgress: TestBook[] = [{ id: "a", title: "A", progressPercentage: 5 }];
    const completed: TestBook[] = [{ id: "a", title: "A", progressPercentage: 100 }];

    expect(promoteBookForReading(inProgress, "a")).toBe(inProgress);
    expect(promoteBookForReading(completed, "a")).toBe(completed);
  });
});

describe("homeState reconciliation", () => {
  it("clears stale ids after reload and falls back to first preview book", () => {
    const books: TestBook[] = [{ id: "book-1", title: "Book 1", progressPercentage: 0 }];
    const result = reconcileHomeState(
      books,
      makeState({
        route: "reader",
        previewBookId: "missing-preview",
        activeReadingBookId: "missing-active",
        shelfDetailsBookId: "missing-shelf",
      }),
    );

    expect(result.route).toBe("home");
    expect(result.previewBookId).toBe("book-1");
    expect(result.activeReadingBookId).toBeNull();
    expect(result.shelfDetailsBookId).toBeNull();
  });

  it("closes shelf modal if selected book moved to continue reading", () => {
    const books: TestBook[] = [{ id: "book-1", title: "Book 1", progressPercentage: 10 }];
    const result = reconcileHomeState(
      books,
      makeState({
        route: "home",
        previewBookId: "book-1",
        activeReadingBookId: null,
        shelfDetailsBookId: "book-1",
      }),
    );

    expect(result.previewBookId).toBe("book-1");
    expect(result.shelfDetailsBookId).toBeNull();
  });

  it("keeps valid shelf modal selection in home mode", () => {
    const books: TestBook[] = [{ id: "book-1", title: "Book 1", progressPercentage: 0 }];
    const result = reconcileHomeState(
      books,
      makeState({
        route: "home",
        previewBookId: "book-1",
        activeReadingBookId: null,
        shelfDetailsBookId: "book-1",
      }),
    );

    expect(result.shelfDetailsBookId).toBe("book-1");
  });

  it("preserves non-reader routes when no active reading book exists", () => {
    const books: TestBook[] = [{ id: "book-1", title: "Book 1", progressPercentage: 0 }];

    const highlightsResult = reconcileHomeState(
      books,
      makeState({
        route: "highlights",
        previewBookId: "book-1",
      }),
    );

    const settingsResult = reconcileHomeState(
      books,
      makeState({
        route: "settings",
        previewBookId: "book-1",
      }),
    );

    expect(highlightsResult.route).toBe("highlights");
    expect(settingsResult.route).toBe("settings");
  });
});

describe("homeState smart query parser", () => {
  it("parses valid smart tokens and keeps free text", () => {
    const parsed = parseShelfSmartQuery("status:favoritos author:Borges title:Ficciones sort:autor lectura");

    expect(parsed.freeText).toBe("lectura");
    expect(parsed.tokens).toEqual([
      {
        field: "status",
        value: "favoritos",
        normalizedValue: "favorites",
        raw: "status:favoritos",
      },
      {
        field: "author",
        value: "Borges",
        normalizedValue: "borges",
        raw: "author:Borges",
      },
      {
        field: "title",
        value: "Ficciones",
        normalizedValue: "ficciones",
        raw: "title:Ficciones",
      },
      {
        field: "sort",
        value: "autor",
        normalizedValue: "author",
        raw: "sort:autor",
      },
    ]);
    expect(parsed.invalidTokens).toEqual([]);
  });

  it("captures invalid and unknown tokens without crashing", () => {
    const parsed = parseShelfSmartQuery("author:asimov foo:bar status:maybe title:");

    expect(parsed.tokens).toEqual([
      {
        field: "author",
        value: "asimov",
        normalizedValue: "asimov",
        raw: "author:asimov",
      },
    ]);
    expect(parsed.invalidTokens).toEqual([
      {
        raw: "foo:bar",
        field: "foo",
        reason: "unknown_field",
      },
      {
        raw: "status:maybe",
        field: "status",
        reason: "invalid_value",
      },
      {
        raw: "title:",
        field: "title",
        reason: "missing_value",
      },
    ]);
  });

  it("exposes warnings from invalid tokens", () => {
    const state = createShelfQueryState("foo:bar status:favorites");
    expect(getShelfQueryWarnings(state)).toEqual(["foo:bar"]);
  });
});

describe("homeState shelf selector pipeline", () => {
  const shelfBooks: TestBook[] = [
    {
      id: "1",
      title: "Ficciones",
      author: "Jorge Luis Borges",
      progressPercentage: 100,
      completed: true,
      updatedAt: "2026-03-05T12:00:00.000Z",
      createdAt: "2026-01-01T00:00:00.000Z",
      lastReadAt: "2026-03-05T12:00:00.000Z",
      fileSizeBytes: 400,
    },
    {
      id: "2",
      title: "Fundacion",
      author: "Isaac Asimov",
      progressPercentage: 20,
      isFavorite: true,
      updatedAt: "2026-03-04T12:00:00.000Z",
      createdAt: "2026-01-02T00:00:00.000Z",
      lastReadAt: "2026-03-04T12:00:00.000Z",
      fileSizeBytes: 900,
    },
    {
      id: "3",
      title: "Dune",
      author: "Frank Herbert",
      progressPercentage: 0,
      toRead: true,
      updatedAt: "2026-03-03T12:00:00.000Z",
      createdAt: "2026-01-03T00:00:00.000Z",
      lastReadAt: "2026-03-02T12:00:00.000Z",
      fileSizeBytes: 700,
    },
    {
      id: "4",
      title: "A Story",
      author: "Author B",
      progressPercentage: 20,
      updatedAt: "2026-03-02T12:00:00.000Z",
      createdAt: "2026-01-04T00:00:00.000Z",
      lastReadAt: "2026-03-01T12:00:00.000Z",
      fileSizeBytes: 900,
    },
    {
      id: "5",
      title: "A Story",
      author: "Author A",
      progressPercentage: 20,
      updatedAt: "2026-03-02T12:00:00.000Z",
      createdAt: "2026-01-05T00:00:00.000Z",
      lastReadAt: "2026-03-01T12:00:00.000Z",
      fileSizeBytes: 900,
    },
  ];

  it("uses Todos tab by default and keeps all books eligible", () => {
    const state = createShelfQueryState("");
    const result = selectShelfBooks(shelfBooks, state);

    expect(state.tab).toBe("all");
    expect(result).toHaveLength(5);
  });

  it("combines tab and free-text search", () => {
    const state = createShelfQueryState("asimov", { tab: "favorites" });
    const result = selectShelfBooks(shelfBooks, state);

    expect(result.map((book) => book.id)).toEqual(["2"]);
  });

  it("applies smart query tokens for status and author", () => {
    const state = createShelfQueryState("status:favoritos author:asimov", { tab: "all" });
    const result = selectShelfBooks(shelfBooks, state);

    expect(result.map((book) => book.id)).toEqual(["2"]);
  });

  it("keeps valid filters when query contains invalid tokens", () => {
    const state = createShelfQueryState("author:asimov foo:bar status:maybe");
    const result = selectShelfBooks(shelfBooks, state);

    expect(result.map((book) => book.id)).toEqual(["2"]);
    expect(state.invalidTokens).toHaveLength(2);
  });

  it("sorts deterministically with explicit tie-breakers", () => {
    const state = createShelfQueryState("", { sortKey: "progress" });
    const result = selectShelfBooks(shelfBooks, state);

    expect(result.map((book) => book.id)).toEqual(["1", "5", "4", "2", "3"]);
  });

  it("allows smart sort token to override chosen sort", () => {
    const state = createShelfQueryState("sort:file_size", { sortKey: "title" });
    const result = selectShelfBooks(shelfBooks, state);

    expect(result.map((book) => book.id)).toEqual(["5", "4", "2", "3", "1"]);
  });
});
