import { invoke } from "@tauri-apps/api/core";
import type {
  AppSettingDto,
  BookCollectionInput,
  BookDto,
  BookmarkDto,
  ScanFolderResult,
  CommandErrorDto,
  CreateCollectionInput,
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
  UpsertBookCoverInput,
  UiLocale,
  CollectionDto,
  ReaderSettings,
  ReaderThemeMode,
} from "$lib/types";
import {
  UI_LOCALE_SETTING_KEY,
  READER_THEME_MODE_SETTING_KEY,
  READER_BRIGHTNESS_SETTING_KEY,
  READER_CONTRAST_SETTING_KEY,
  READER_EPUB_FONT_SIZE_SETTING_KEY,
  READER_EPUB_FONT_FAMILY_SETTING_KEY,
} from "$lib/types";

type MaybeCommandError = Error & { commandError?: CommandErrorDto };

type RawHighlightDto = {
  id: string;
  bookId: string;
  text: string;
  color: string;
  pageNumber?: number;
  page?: number;
  note?: string | null;
  createdAt: string;
};

const normalizeMessage = (error: unknown): string => {
  if (typeof error === "string") {
    return error;
  }

  if (error instanceof Error) {
    return error.message;
  }

  if (typeof error === "object" && error !== null) {
    const candidate = (error as { message?: unknown; error?: unknown }).message;
    if (typeof candidate === "string" && candidate.length > 0) {
      return candidate;
    }

    const nestedError = (error as { error?: unknown }).error;
    if (typeof nestedError === "string" && nestedError.length > 0) {
      return nestedError;
    }
  }

  return "Command invocation failed";
};

const parseCommandError = (raw: unknown): CommandErrorDto | null => {
  const text = normalizeMessage(raw);
  if (text.length === 0) {
    return null;
  }

  try {
    const parsed = JSON.parse(text) as CommandErrorDto;
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
  const fallbackMessage = normalizeMessage(error);
  const commandError = parseCommandError(error);

  const wrapped = new Error(commandError?.message ?? fallbackMessage) as MaybeCommandError;
  wrapped.commandError = commandError ?? undefined;
  throw wrapped;
};

const normalizePageNumber = (
  pageNumberValue: unknown,
  legacyPageValue: unknown,
): number => {
  const hasCanonical = typeof pageNumberValue !== "undefined";
  const hasLegacy = typeof legacyPageValue !== "undefined";

  if (!hasCanonical && !hasLegacy) {
    throw new Error("Highlight payload requires pageNumber (or legacy page)");
  }

  const canonical =
    typeof pageNumberValue === "number" && Number.isFinite(pageNumberValue)
      ? pageNumberValue
      : null;
  const legacy =
    typeof legacyPageValue === "number" && Number.isFinite(legacyPageValue)
      ? legacyPageValue
      : null;

  if (hasCanonical && canonical === null) {
    throw new Error("Highlight payload pageNumber must be a finite number");
  }

  if (hasLegacy && legacy === null) {
    throw new Error("Highlight payload page must be a finite number");
  }

  if (canonical !== null && legacy !== null && canonical !== legacy) {
    throw new Error(
      `Highlight payload has conflicting page fields: pageNumber=${canonical}, page=${legacy}`,
    );
  }

  const resolved = canonical ?? legacy;
  if (resolved === null || !Number.isInteger(resolved) || resolved <= 0) {
    throw new Error("Highlight payload page number must be a positive integer");
  }

  return resolved;
};

const normalizeHighlightDto = (payload: RawHighlightDto): HighlightDto => {
  const pageNumber = normalizePageNumber(payload.pageNumber, payload.page);
  return {
    id: payload.id,
    bookId: payload.bookId,
    text: payload.text,
    color: payload.color,
    pageNumber,
    note: payload.note ?? null,
    createdAt: payload.createdAt,
  };
};

export const listBooks = async (): Promise<BookDto[]> => {
  return invoke<BookDto[]>("listBooks");
};

export const scanFolder = async (path: string): Promise<ScanFolderResult> => {
  try {
    return await invoke<ScanFolderResult>("scanFolder", { path });
  } catch (error) {
    return attachCommandError(error);
  }
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

const readSettingValue = (settings: AppSettingDto[], key: string): unknown => {
  const item = settings.find((entry) => entry.key === key);
  if (!item) {
    return null;
  }

  try {
    return JSON.parse(item.valueJson) as unknown;
  } catch {
    return null;
  }
};

const READER_THEME_MODES: ReadonlyArray<ReaderThemeMode> = ["paper", "sepia", "night"];
const DEFAULT_READER_SETTINGS: ReaderSettings = {
  themeMode: "paper",
  brightness: 100,
  contrast: 100,
  epub: {
    fontSize: 100,
    fontFamily: "serif",
  },
};

const clampInteger = (value: number, min: number, max: number): number => {
  return Math.min(max, Math.max(min, Math.round(value)));
};

const sanitizeThemeMode = (value: unknown): ReaderThemeMode => {
  if (typeof value !== "string") {
    return DEFAULT_READER_SETTINGS.themeMode;
  }

  const normalized = value.trim().toLowerCase() as ReaderThemeMode;
  if (!READER_THEME_MODES.includes(normalized)) {
    return DEFAULT_READER_SETTINGS.themeMode;
  }

  return normalized;
};

const sanitizeRangedNumber = (
  value: unknown,
  min: number,
  max: number,
  fallback: number,
): number => {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return fallback;
  }

  return clampInteger(value, min, max);
};

const sanitizeFontFamily = (value: unknown): string => {
  if (typeof value !== "string") {
    return DEFAULT_READER_SETTINGS.epub.fontFamily;
  }

  const normalized = value.trim();
  if (normalized.length === 0) {
    return DEFAULT_READER_SETTINGS.epub.fontFamily;
  }

  return normalized;
};

export const sanitizeReaderSettings = (
  input?: Partial<ReaderSettings> | null,
): ReaderSettings => {
  const next = input ?? {};

  return {
    themeMode: sanitizeThemeMode(next.themeMode),
    brightness: sanitizeRangedNumber(
      next.brightness,
      50,
      150,
      DEFAULT_READER_SETTINGS.brightness,
    ),
    contrast: sanitizeRangedNumber(next.contrast, 50, 150, DEFAULT_READER_SETTINGS.contrast),
    epub: {
      fontSize: sanitizeRangedNumber(
        next.epub?.fontSize,
        80,
        200,
        DEFAULT_READER_SETTINGS.epub.fontSize,
      ),
      fontFamily: sanitizeFontFamily(next.epub?.fontFamily),
    },
  };
};

const buildReaderSettingsPayload = (settings: ReaderSettings): AppSettingDto[] => {
  const now = new Date().toISOString();
  return [
    {
      key: READER_THEME_MODE_SETTING_KEY,
      valueJson: JSON.stringify(settings.themeMode),
      updatedAt: now,
    },
    {
      key: READER_BRIGHTNESS_SETTING_KEY,
      valueJson: JSON.stringify(settings.brightness),
      updatedAt: now,
    },
    {
      key: READER_CONTRAST_SETTING_KEY,
      valueJson: JSON.stringify(settings.contrast),
      updatedAt: now,
    },
    {
      key: READER_EPUB_FONT_SIZE_SETTING_KEY,
      valueJson: JSON.stringify(settings.epub.fontSize),
      updatedAt: now,
    },
    {
      key: READER_EPUB_FONT_FAMILY_SETTING_KEY,
      valueJson: JSON.stringify(settings.epub.fontFamily),
      updatedAt: now,
    },
  ];
};

export const getDefaultReaderSettings = (): ReaderSettings => {
  return sanitizeReaderSettings(DEFAULT_READER_SETTINGS);
};

export const getLocaleSetting = async (): Promise<string | null> => {
  const settings = await getSettings();
  const rawLocale = readSettingValue(settings, UI_LOCALE_SETTING_KEY);
  if (typeof rawLocale !== "string" || rawLocale.trim().length === 0) {
    return null;
  }

  return rawLocale.trim().toLowerCase();
};

export const upsertLocaleSetting = async (locale: UiLocale): Promise<void> => {
  await upsertSettings([
    {
      key: UI_LOCALE_SETTING_KEY,
      valueJson: JSON.stringify(locale),
      updatedAt: new Date().toISOString(),
    },
  ]);
};

export const getReaderSettings = async (): Promise<ReaderSettings> => {
  const settings = await getSettings();

  return sanitizeReaderSettings({
    themeMode: readSettingValue(settings, READER_THEME_MODE_SETTING_KEY) as ReaderThemeMode,
    brightness: readSettingValue(settings, READER_BRIGHTNESS_SETTING_KEY) as number,
    contrast: readSettingValue(settings, READER_CONTRAST_SETTING_KEY) as number,
    epub: {
      fontSize: readSettingValue(settings, READER_EPUB_FONT_SIZE_SETTING_KEY) as number,
      fontFamily: readSettingValue(settings, READER_EPUB_FONT_FAMILY_SETTING_KEY) as string,
    },
  });
};

export const upsertReaderSettings = async (settings: Partial<ReaderSettings>): Promise<ReaderSettings> => {
  const sanitized = sanitizeReaderSettings(settings);
  await upsertSettings(buildReaderSettingsPayload(sanitized));
  return sanitized;
};

export const resetReaderSettingsToDefaults = async (): Promise<ReaderSettings> => {
  const defaults = getDefaultReaderSettings();
  await upsertSettings(buildReaderSettingsPayload(defaults));
  return defaults;
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
  try {
    return await invoke<number[]>("getFileBytes", { filePath });
  } catch (error) {
    return attachCommandError(error);
  }
};

export const hideBookFromLibrary = async (bookId: string): Promise<void> => {
  try {
    await invoke("hideBookFromLibrary", {
      payload: {
        bookId,
      },
    });
  } catch (error) {
    attachCommandError(error);
  }
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

export const upsertBookCover = async (payload: UpsertBookCoverInput): Promise<void> => {
  try {
    await invoke("upsertBookCover", { payload });
  } catch (error) {
    attachCommandError(error);
  }
};

export const listHighlights = async (bookId?: string): Promise<HighlightDto[]> => {
  try {
    const rows = await invoke<RawHighlightDto[]>("listHighlights", { bookId: bookId ?? null });
    return rows.map(normalizeHighlightDto);
  } catch (error) {
    return attachCommandError(error);
  }
};

export const saveHighlight = async (highlight: SaveHighlightInput): Promise<void> => {
  const pageNumber = normalizePageNumber(highlight.pageNumber, highlight.page);
  const payload: SaveHighlightInput = {
    ...highlight,
    pageNumber,
    page: pageNumber,
    note: highlight.note ?? null,
  };

  try {
    await invoke("saveHighlight", { highlight: payload });
  } catch (error) {
    attachCommandError(error);
  }
};

export const deleteHighlight = async (id: string): Promise<void> => {
  await invoke("deleteHighlight", { id });
};

export const listBookmarks = async (bookId?: string): Promise<BookmarkDto[]> => {
  return invoke<BookmarkDto[]>("listBookmarks", { bookId: bookId ?? null });
};

export const saveBookmark = async (bookmark: SaveBookmarkInput): Promise<void> => {
  await invoke("saveBookmark", { bookmark });
};

export const deleteBookmark = async (id: string): Promise<void> => {
  await invoke("deleteBookmark", { id });
};

export const createCollection = async (payload: CreateCollectionInput): Promise<CollectionDto> => {
  try {
    return await invoke<CollectionDto>("createCollection", { payload });
  } catch (error) {
    return attachCommandError(error);
  }
};

export const deleteCollection = async (id: number): Promise<void> => {
  try {
    await invoke("deleteCollection", { id });
  } catch (error) {
    attachCommandError(error);
  }
};

export const listCollections = async (): Promise<CollectionDto[]> => {
  try {
    return await invoke<CollectionDto[]>("listCollections");
  } catch (error) {
    return attachCommandError(error);
  }
};

export const addBookToCollection = async (payload: BookCollectionInput): Promise<void> => {
  try {
    await invoke("addBookToCollection", { payload });
  } catch (error) {
    attachCommandError(error);
  }
};

export const removeBookFromCollection = async (payload: BookCollectionInput): Promise<void> => {
  try {
    await invoke("removeBookFromCollection", { payload });
  } catch (error) {
    attachCommandError(error);
  }
};

export const getBookCollections = async (bookId: string): Promise<CollectionDto[]> => {
  try {
    return await invoke<CollectionDto[]>("getBookCollections", { bookId });
  } catch (error) {
    return attachCommandError(error);
  }
};
