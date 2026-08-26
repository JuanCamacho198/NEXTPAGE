import type { SettingsPort } from '$lib/shared/ports/SettingsPort';
import type { ReaderSettings, UiLocale } from '$lib/shared/types';

const defaults: ReaderSettings = { themeMode: 'paper', brightness: 100, contrast: 100, selectionColor: '#3388ff', epub: { fontSize: 100, fontFamily: 'serif' }, lineHeight: 1.8, letterSpacing: 0, paragraphSpacing: 1, textAlign: 'left', direction: 'ltr', hyphenation: false, verticalScrolling: false, margins: { top: 1.5, bottom: 1.5, left: 2, right: 2 }, showHeader: true, showFooter: true, showPageNumbers: true, progressIndicator: 'percentage' };

export class MockSettingsAdapter implements SettingsPort {
  #s: ReaderSettings = { ...defaults, epub: { ...defaults.epub }, margins: { ...defaults.margins } };
  #locale: string | null = null;
  #goals = new Map<string, number>();
  #minutes = new Map<string, number>();
  async getReaderSettings(): Promise<ReaderSettings> { return { ...this.#s, epub: { ...this.#s.epub }, margins: { ...this.#s.margins } }; }
  async upsertReaderSettings(p: Partial<ReaderSettings>): Promise<ReaderSettings> { this.#s = { ...this.#s, ...p, epub: { ...this.#s.epub, ...p.epub }, margins: { ...this.#s.margins, ...p.margins } } as ReaderSettings; return this.getReaderSettings(); }
  async resetReaderSettings(): Promise<ReaderSettings> { this.#s = { ...defaults, epub: { ...defaults.epub }, margins: { ...defaults.margins } }; return this.getReaderSettings(); }
  async getLocale(): Promise<string | null> { return this.#locale; }
  async upsertLocale(l: UiLocale): Promise<void> { this.#locale = l; }
  async getDailyGoal(u?: string): Promise<number> { return u ? (this.#goals.get(u) ?? 20) : 20; }
  async saveDailyGoal(m: number, u?: string): Promise<void> { if (!u) return; this.#goals.set(u, m); }
  async getTodayMinutes(u: string, b?: string): Promise<number> { return this.#minutes.get(b ? `${u}:${b}` : u) ?? 0; }
  seedTodayMinutes(u: string, m: number, b?: string): void { this.#minutes.set(b ? `${u}:${b}` : u, m); }
}
