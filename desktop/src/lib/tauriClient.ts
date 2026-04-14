import { invoke } from "@tauri-apps/api/core";
import type { BookDto, ReadingProgressDto, SaveProgressInput } from "./types";

export const listBooks = async (): Promise<BookDto[]> => {
  return invoke<BookDto[]>("listBooks");
};

export const upsertBook = async (book: BookDto): Promise<void> => {
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
