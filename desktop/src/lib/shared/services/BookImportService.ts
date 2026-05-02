import { invoke } from "@tauri-apps/api/core";
import { i18n } from "../i18n";

export type BookImportInput = {
  sourcePath: string;
  title?: string;
  author?: string;
  format: string;
};

export type BookDto = {
  id: string;
  title: string;
  author: string;
  filePath: string;
  format: string;
  syncStatus: string;
  currentPage: number;
  totalPages: number;
  createdAt: string;
  updatedAt: string;
};

export type ImportProgress = {
  status: "reading" | "importing" | "complete" | "error";
  message: string;
  percentage?: number;
};

const normalizeSourcePath = (value: string) => {
  const trimmed = value.trim();
  if (trimmed.startsWith("file://")) {
    try {
      return decodeURIComponent(trimmed.replace(/^file:\/\//, ""));
    } catch {
      return trimmed.replace(/^file:\/\//, "");
    }
  }

  return trimmed;
};

const readImportErrorMessage = (error: unknown): string => {
  if (error instanceof Error && error.message) {
    try {
      const parsed = JSON.parse(error.message) as {
        message?: unknown;
      };
      if (typeof parsed.message === "string" && parsed.message.length > 0) {
        return parsed.message;
      }
    } catch {
      return error.message;
    }

    return error.message;
  }

  if (typeof error === "string" && error.length > 0) {
    return error;
  }

  if (typeof error === "object" && error !== null) {
    const candidate = (error as { message?: unknown }).message;
    if (typeof candidate === "string" && candidate.length > 0) {
      return candidate;
    }
  }

  return i18n.t("en", "errors.importCommandFailed");
};

export async function importBook(
  input: BookImportInput,
  onProgress?: (progress: ImportProgress) => void
): Promise<BookDto> {
  const locale = i18n.toSupportedLocale((globalThis.localStorage?.getItem("nextpage.ui.locale") ?? "").trim()) ?? "es";
  onProgress?.({
    status: "reading",
    message: i18n.t(locale, "import.reading"),
  });

  try {
    const sourcePath = normalizeSourcePath(input.sourcePath);
    if (!sourcePath) {
      throw new Error(i18n.t(locale, "import.emptyPath"));
    }

    const book = await invoke<BookDto>("importBook", {
      input: {
        sourcePath,
        title: input.title,
        author: input.author,
        format: input.format,
      },
    });

    onProgress?.({
      status: "importing",
      message: i18n.t(locale, "import.importing"),
      percentage: 50,
    });

    onProgress?.({
      status: "complete",
      message: i18n.t(locale, "import.complete"),
      percentage: 100,
    });

    return book;
  } catch (error) {
    onProgress?.({
      status: "error",
      message: readImportErrorMessage(error),
    });

    throw new Error(readImportErrorMessage(error));
  }
}

export async function getFileBytes(filePath: string): Promise<Uint8Array> {
  const bytes = await invoke<number[]>("getFileBytes", { filePath });
  return new Uint8Array(bytes);
}
