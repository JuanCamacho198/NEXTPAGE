import { invoke } from "@tauri-apps/api/core";
import type {
  AppSettingDto,
  CommandErrorDto,
  BookDto,
  BookmarkDto,
  HighlightDto,
  LibraryBookDto,
  ReadingProgressDto,
  ReadingSessionInput,
  ReadingStatsSummaryDto,
  SaveHighlightInput,
  SaveProgressInput,
  SaveBookmarkInput,
  SearchBookTextInput,
  SearchBookTextResponse,
} from "./types";

type MaybeCommandError = Error & { commandError?: CommandErrorDto };

const parseCommandError = (raw: unknown): CommandErrorDto | null => {
  if (typeof raw !== "string") {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as CommandErrorDto;
    if (
      parsed &&
      typeof parsed.code === "string" &&
      typeof parsed.message === "string" &&
      typeof parsed.recoverable === "boolean"
    ) {
      return parsed;
    }
  } catch {
    return null;
  }

  return null;
};

const attachCommandError = (error: unknown): never => {
  const fallbackMessage = error instanceof Error ? error.message : "Unexpected command failure";
  const commandError = parseCommandError(fallbackMessage);

  const wrapped = new Error(commandError?.message ?? fallbackMessage) as MaybeCommandError;
  wrapped.commandError = commandError ?? undefined;
  throw wrapped;
};

export const listBooks = async (): Promise<BookDto[]> => {
  return invoke<BookDto[]>("listBooks");
};

export const listLibraryBooks = async (responseVersion = 1): Promise<LibraryBookDto[]> => {
  try {
    return await invoke<LibraryBookDto[]>("listLibraryBooks", {
      payload: {
        responseVersion,
      },
    });
  } catch (error) {
    return attachCommandError(error);
  }
};

export const upsertBook = async (book: {
  id: string;
  title: string;
  author: string;
  filePath?: string;
  format?: string;
  syncStatus?: string;
  currentPage?: number;
  totalPages?: number;
}): Promise<void> => {
  await invoke("upsertBook", { book });
};

export const getProgress = async (bookId: string): Promise<ReadingProgressDto | null> => {
  return invoke<ReadingProgressDto | null>("getProgress", { bookId });
};

export const saveProgress = async (payload: SaveProgressInput): Promise<void> => {
  await invoke("saveProgress", { payload });
};

export const saveReadingSession = async (payload: ReadingSessionInput): Promise<void> => {
  await invoke("saveReadingSession", { payload });
};

export const upsertProgress = async (progress: ReadingProgressDto): Promise<void> => {
  await invoke("upsertProgress", { progress });
};

export const getReadingStats = async (bookId?: string): Promise<ReadingStatsSummaryDto> => {
  try {
    return await invoke<ReadingStatsSummaryDto>("getReadingStats", {
      bookId,
    });
  } catch (error) {
    return attachCommandError(error);
  }
};

export const getSettings = async (): Promise<AppSettingDto[]> => {
  try {
    return await invoke<AppSettingDto[]>("getSettings");
  } catch (error) {
    return attachCommandError(error);
  }
};

export const upsertSettings = async (settings: AppSettingDto[]): Promise<void> => {
  try {
    await invoke("upsertSettings", { settings });
  } catch (error) {
    attachCommandError(error);
  }
};

export const indexBookText = async (payload: {
  bookId: string;
  chunks: Array<{ locator: string; chunkIndex: number; textContent: string }>;
}): Promise<void> => {
  try {
    await invoke("indexBookText", { payload });
  } catch (error) {
    attachCommandError(error);
  }
};

export const searchBookText = async (
  payload: SearchBookTextInput,
): Promise<SearchBookTextResponse> => {
  try {
    return await invoke<SearchBookTextResponse>("searchBookText", { payload });
  } catch (error) {
    return attachCommandError(error);
  }
};

export const importBook = async (input: {
  title: string;
  author: string;
  filePath: string;
  format: string;
}): Promise<{ id: string }> => {
  return invoke<{ id: string }>("importBook", { input });
};

export const getFileBytes = async (filePath: string): Promise<number[]> => {
  return invoke<number[]>("getFileBytes", { filePath });
};

export const updateBookProgress = async (bookId: string, currentPage: number): Promise<void> => {
  await invoke("updateBookProgress", { bookId, currentPage });
};

export const fileExists = async (path: string): Promise<boolean> => {
  return invoke<boolean>("fileExists", { path });
};

export const saveBookFile = async (id: string, data: number[]): Promise<void> => {
  await invoke("saveBookFile", { id, data });
};

export const listHighlights = async (bookId?: string): Promise<HighlightDto[]> => {
  return invoke<HighlightDto[]>("listHighlights", { bookId });
};

export const saveHighlight = async (highlight: SaveHighlightInput): Promise<void> => {
  await invoke("saveHighlight", { highlight });
};

export const deleteHighlight = async (id: string): Promise<void> => {
  await invoke("deleteHighlight", { id });
};

export const listBookmarks = async (bookId?: string): Promise<BookmarkDto[]> => {
  return invoke<BookmarkDto[]>("listBookmarks", { bookId });
};

export const saveBookmark = async (bookmark: SaveBookmarkInput): Promise<void> => {
  await invoke("saveBookmark", { bookmark });
};

export const deleteBookmark = async (id: string): Promise<void> => {
  await invoke("deleteBookmark", { id });
};
