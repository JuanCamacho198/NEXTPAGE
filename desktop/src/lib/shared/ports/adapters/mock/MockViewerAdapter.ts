import type { ViewerPort, UpdateHighlightInput } from '$lib/shared/ports/ViewerPort';
import type { BookmarkDto, CommandErrorDto, HighlightDto, ReadingProgressDto, SaveBookmarkInput, SaveHighlightInput, SaveProgressInput, SearchBookTextInput, SearchBookTextResponse, TagDto, RemoteHighlightRow, RemoteReadingSessionRow, UpsertRemoteSummary } from '$lib/shared/types';

type Err = Error & { commandError?: CommandErrorDto };
const err = (c: string, m: string, r: boolean): Err => { const e = new Error(m) as Err; e.commandError = { code: c, message: m, recoverable: r }; return e; };

export class MockViewerAdapter implements ViewerPort {
  #h = new Map<string, HighlightDto>();
  #b = new Map<string, BookmarkDto>();
  #p = new Map<string, ReadingProgressDto>();
  #t = new Map<string, TagDto>();
  #ht = new Map<string, Set<string>>();
  #rh: RemoteHighlightRow[] = [];
  #rs: RemoteReadingSessionRow[] = [];
  #nextTag = 1;
  async listHighlights(id?: string): Promise<HighlightDto[]> { const a = [...this.#h.values()]; return id ? a.filter((x) => x.bookId === id) : a; }
  async saveHighlight(i: SaveHighlightInput): Promise<void> { const d: HighlightDto = { id: i.id, bookId: i.bookId, text: i.text, color: i.color, pageNumber: i.pageNumber ?? i.page ?? 0, note: i.note ?? null, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), cfi: i.cfi ?? null }; this.#h.set(d.id, d); }
  async deleteHighlight(id: string): Promise<void> { if (!this.#h.has(id)) throw err('NOT_FOUND', `Highlight ${id} not found`, false); this.#h.delete(id); this.#ht.delete(id); }
  async updateHighlight(i: UpdateHighlightInput): Promise<HighlightDto> { const e = this.#h.get(i.id); if (!e) throw err('NOT_FOUND', `Highlight ${i.id} not found`, false); const pn = i.pageNumber ?? i.page ?? e.pageNumber; const u = { ...e, color: i.color ?? e.color, note: i.note ?? e.note, pageNumber: pn, updatedAt: new Date().toISOString() }; this.#h.set(i.id, u); return u; }
  async listTags(): Promise<TagDto[]> { return [...this.#t.values()]; }
  async listTagsForHighlight(id: string): Promise<TagDto[]> { const s = this.#ht.get(id); return s ? [...s].map((x) => this.#t.get(x)!).filter(Boolean) : []; }
  async createTag(name: string, color?: string): Promise<TagDto> { const t: TagDto = { id: `tag-${this.#nextTag++}`, name, color: color ?? null, createdAt: new Date().toISOString() }; this.#t.set(t.id, t); return t; }
  async listBookmarks(id?: string): Promise<BookmarkDto[]> { const a = [...this.#b.values()]; return id ? a.filter((x) => x.bookId === id) : a; }
  async saveBookmark(i: SaveBookmarkInput): Promise<void> { this.#b.set(i.id, { id: i.id, bookId: i.bookId, pageNumber: i.pageNumber, title: i.title, createdAt: i.createdAt ?? new Date().toISOString() }); }
  async deleteBookmark(id: string): Promise<void> { if (!this.#b.has(id)) throw err('NOT_FOUND', `Bookmark ${id} not found`, false); this.#b.delete(id); }
  async getProgress(id: string): Promise<ReadingProgressDto | null> { return this.#p.get(id) ?? null; }
  async saveProgress(p: SaveProgressInput): Promise<void> { this.#p.set(p.bookId, { id: p.bookId, bookId: p.bookId, cfiLocation: p.cfiLocation, percentage: p.percentage, updatedAt: new Date().toISOString() }); }
  async searchBookText(p: SearchBookTextInput): Promise<SearchBookTextResponse> { return { items: [], total: 0, page: p.page, pageSize: p.pageSize }; }
  async upsertRemoteHighlights(rows: RemoteHighlightRow[]): Promise<UpsertRemoteSummary> { let a = 0; for (const r of rows) { this.#rh.push(r); const e = this.#h.get(r.id); if (!e) this.#h.set(r.id, { id: r.id, bookId: r.bookId, text: r.textContent, color: r.color, pageNumber: r.page ?? 0, note: r.note ?? null, createdAt: new Date(r.updatedAtEpochMillis).toISOString(), updatedAt: new Date(r.updatedAtEpochMillis).toISOString(), cfi: r.cfiRange ?? null }); else if (r.updatedAtEpochMillis >= new Date(e.updatedAt).getTime()) this.#h.set(r.id, { ...e, text: r.textContent, color: r.color, pageNumber: r.page ?? e.pageNumber, note: r.note ?? e.note, cfi: r.cfiRange ?? e.cfi, updatedAt: new Date(r.updatedAtEpochMillis).toISOString() }); a++; } return { applied: a, skippedUnknownBook: 0, skippedInvalid: 0 }; }
  async upsertRemoteReadingSessions(rows: RemoteReadingSessionRow[]): Promise<number> { for (const r of rows) this.#rs.push(r); return rows.length; }
  seedHighlights(h: HighlightDto[]): void { for (const x of h) this.#h.set(x.id, x); }
  getRemoteHighlights(): RemoteHighlightRow[] { return [...this.#rh]; }
}
