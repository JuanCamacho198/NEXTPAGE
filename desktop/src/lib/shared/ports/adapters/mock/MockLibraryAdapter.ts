import type { LibraryPort, UpsertBookInput, PerBookSize } from '$lib/shared/ports/LibraryPort';
import type { BookDto, CollectionDto, CreateCollectionInput, BookCollectionInput, LibraryBookDto, UpsertBookCoverInput, ScanFolderResult, CommandErrorDto } from '$lib/shared/types';

type Err = Error & { commandError?: CommandErrorDto };
const err = (c: string, m: string, r: boolean): Err => { const e = new Error(m) as Err; e.commandError = { code: c, message: m, recoverable: r }; return e; };
const toLib = (i: UpsertBookInput): LibraryBookDto => ({ id: i.id, title: i.title, author: i.author, format: i.format ?? 'epub', currentPage: i.currentPage ?? 0, totalPages: i.totalPages ?? 0, progressPercentage: 0, coverPath: null, minutesRead: 0, updatedAt: i.updatedAt, createdAt: i.createdAt, genre: i.genre ?? null });
const toBook = (l: LibraryBookDto): BookDto => ({ id: l.id, title: l.title, author: l.author, filePath: '', format: l.format, syncStatus: 'idle', currentPage: l.currentPage, totalPages: l.totalPages, createdAt: l.createdAt, updatedAt: l.updatedAt, genre: l.genre });

export class MockLibraryAdapter implements LibraryPort {
  #books = new Map<string, LibraryBookDto>();
  #cols = new Map<number, CollectionDto>();
  #bookCols = new Map<string, Set<number>>();
  #covers = new Map<string, UpsertBookCoverInput>();
  #nextId = 1;
  async listLibraryBooks(): Promise<LibraryBookDto[]> { return [...this.#books.values()]; }
  async listBooks(): Promise<BookDto[]> { return [...this.#books.values()].map(toBook); }
  async scanFolder(): Promise<ScanFolderResult> { return { files: [], skippedUnsupportedCount: 0, skippedUnreadableCount: 0 }; }
  async upsertBook(b: UpsertBookInput): Promise<void> { this.#books.set(b.id, toLib(b)); }
  async hideBook(id: string): Promise<void> { if (!this.#books.has(id)) throw err('NOT_FOUND', `Book ${id} not found`, false); this.#books.delete(id); }
  async setReadingStatus(id: string, s: string | null): Promise<void> { const b = this.#books.get(id); if (!b) throw err('NOT_FOUND', `Book ${id} not found`, false); this.#books.set(id, { ...b, readingStatus: s as LibraryBookDto['readingStatus'] }); }
  async listCollections(): Promise<CollectionDto[]> { return [...this.#cols.values()]; }
  async createCollection(p: CreateCollectionInput): Promise<CollectionDto> { const d: CollectionDto = { id: this.#nextId++, name: p.name, color: p.color ?? null, isSystem: false, createdAt: new Date().toISOString() }; this.#cols.set(d.id, d); return d; }
  async deleteCollection(id: number): Promise<void> { if (!this.#cols.has(id)) throw err('NOT_FOUND', `Collection ${id} not found`, false); this.#cols.delete(id); for (const s of this.#bookCols.values()) s.delete(id); }
  async addBookToCollection(p: BookCollectionInput): Promise<void> { if (!this.#books.has(p.bookId)) throw err('NOT_FOUND', `Book ${p.bookId} not found`, false); if (!this.#cols.has(p.collectionId)) throw err('NOT_FOUND', `Collection ${p.collectionId} not found`, false); const s = this.#bookCols.get(p.bookId) ?? new Set<number>(); s.add(p.collectionId); this.#bookCols.set(p.bookId, s); }
  async removeBookFromCollection(p: BookCollectionInput): Promise<void> { const s = this.#bookCols.get(p.bookId); if (!s || !s.has(p.collectionId)) throw err('NOT_FOUND', 'Book not in collection', false); s.delete(p.collectionId); }
  async getBookCollections(id: string): Promise<CollectionDto[]> { const ids = this.#bookCols.get(id); return ids ? [...ids].map((x) => this.#cols.get(x)!).filter(Boolean) : []; }
  async upsertBookCover(p: UpsertBookCoverInput): Promise<void> { this.#covers.set(p.bookId, p); }
  async deleteBookCover(id: string): Promise<void> { this.#covers.delete(id); }
  async extractEpubCover(): Promise<boolean> { return true; }
  async getPerBookSizes(): Promise<PerBookSize[]> { return [...this.#books.values()].map((b) => ({ id: b.id, title: b.title, bytes: 0 })); }
  seedBooks(b: LibraryBookDto[]): void { for (const x of b) this.#books.set(x.id, x); }
}
