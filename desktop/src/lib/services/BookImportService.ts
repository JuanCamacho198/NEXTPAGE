import { invoke } from "@tauri-apps/api/core";

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

export async function importBook(
  input: BookImportInput,
  onProgress?: (progress: ImportProgress) => void
): Promise<BookDto> {
  onProgress?.({
    status: "reading",
    message: "Reading file...",
  });

  try {
    const book = await invoke<BookDto>("importBook", {
      input: {
        sourcePath: input.sourcePath,
        title: input.title,
        author: input.author,
        format: input.format,
      },
    });

    onProgress?.({
      status: "importing",
      message: "Importing to library...",
      percentage: 50,
    });

    onProgress?.({
      status: "complete",
      message: "Import complete",
      percentage: 100,
    });

    return book;
  } catch (error) {
    onProgress?.({
      status: "error",
      message: error instanceof Error ? error.message : "Import failed",
    });
    throw error;
  }
}

export async function getFileBytes(filePath: string): Promise<Uint8Array> {
  const bytes = await invoke<number[]>("getFileBytes", { filePath });
  return new Uint8Array(bytes);
}