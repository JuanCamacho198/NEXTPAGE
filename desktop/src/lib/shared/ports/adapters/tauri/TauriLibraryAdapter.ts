import type { LibraryPort, UpsertBookInput, PerBookSize } from '$lib/shared/ports/LibraryPort';
import type {
  BookDto,
  CollectionDto,
  CreateCollectionInput,
  BookCollectionInput,
  LibraryBookDto,
  UpsertBookCoverInput,
  ScanFolderResult
} from '$lib/shared/types';
import * as tauriClient from '$lib/shared/api/tauriClient';

export class TauriLibraryAdapter implements LibraryPort {
  listLibraryBooks(responseVersion?: number): Promise<LibraryBookDto[]> {
    return tauriClient.listLibraryBooks(responseVersion);
  }

  listBooks(): Promise<BookDto[]> {
    return tauriClient.listBooks();
  }

  scanFolder(path: string): Promise<ScanFolderResult> {
    return tauriClient.scanFolder(path);
  }

  upsertBook(book: UpsertBookInput): Promise<void> {
    return tauriClient.upsertBook(book);
  }

  hideBook(bookId: string): Promise<void> {
    return tauriClient.hideBookFromLibrary(bookId);
  }

  setReadingStatus(bookId: string, status: string | null): Promise<void> {
    return tauriClient.setReadingStatus(bookId, status);
  }

  listCollections(): Promise<CollectionDto[]> {
    return tauriClient.listCollections();
  }

  createCollection(payload: CreateCollectionInput): Promise<CollectionDto> {
    return tauriClient.createCollection(payload);
  }

  deleteCollection(id: number): Promise<void> {
    return tauriClient.deleteCollection(id);
  }

  addBookToCollection(payload: BookCollectionInput): Promise<void> {
    return tauriClient.addBookToCollection(payload);
  }

  removeBookFromCollection(payload: BookCollectionInput): Promise<void> {
    return tauriClient.removeBookFromCollection(payload);
  }

  getBookCollections(bookId: string): Promise<CollectionDto[]> {
    return tauriClient.getBookCollections(bookId);
  }

  upsertBookCover(payload: UpsertBookCoverInput): Promise<void> {
    return tauriClient.upsertBookCover(payload);
  }

  deleteBookCover(bookId: string): Promise<void> {
    return tauriClient.deleteBookCover(bookId);
  }

  extractEpubCover(bookId: string, filePath: string): Promise<boolean> {
    return tauriClient.extractEpubCover(bookId, filePath);
  }

  getPerBookSizes(): Promise<PerBookSize[]> {
    return tauriClient.getPerBookSizes();
  }
}
