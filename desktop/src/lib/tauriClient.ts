import { invoke } from "@tauri-apps/api/core";
import type { BookDto, ReadingProgressDto, SaveProgressInput } from "./types";

export const listBooks = async (): Promise<BookDto[]> => {
  return invoke<BookDto[]>("listBooks");
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

export const upsertProgress = async (progress: ReadingProgressDto): Promise<void> => {
  await invoke("upsertProgress", { progress });
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
