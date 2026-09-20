import type { DictionaryWordDto } from '$lib/shared/types';
import type { MessageKey } from '$lib/shared/i18n';

export type Translator = (key: MessageKey, params?: Record<string, string | number>) => string;

export type CompletenessInput = Pick<
  DictionaryWordDto,
  'definition' | 'partOfSpeech' | 'phonetic' | 'example' | 'quote'
>;

function isNonBlank(value: string | null | undefined): boolean {
  return typeof value === 'string' && value.trim().length > 0;
}

/**
 * The single completeness predicate (Decision 16). Its only consumer is the
 * per-row badge; completeness is deliberately not aggregated into a KPI.
 *
 * An entry is complete when all four user-authored fields carry non-blank text
 * and a captured `quote` exists (REQ-DRE-009).
 */
export function isComplete(entry: CompletenessInput): boolean {
  return (
    isNonBlank(entry.definition) &&
    isNonBlank(entry.partOfSpeech) &&
    isNonBlank(entry.phonetic) &&
    isNonBlank(entry.example) &&
    entry.quote != null
  );
}

/** The frame's seven avatar colours, in rotation order (frame ZPZb7). */
const AVATAR_TOKEN_COUNT = 7;

/** Zero-based rotation over the measured avatar palette, as a CSS token reference. */
export function avatarColorVariable(index: number): string {
  const slot = ((index % AVATAR_TOKEN_COUNT) + AVATAR_TOKEN_COUNT) % AVATAR_TOKEN_COUNT;
  return `var(--color-avatar-${slot + 1})`;
}

/** The row's letter badge: the term's first character, uppercased. */
export function entryInitial(word: string): string {
  return word.trim().charAt(0).toUpperCase();
}

/**
 * The detail panel's phonetic line. The frame draws the value wrapped in
 * presentation slashes; the stored value carries none, so the wrapper is added
 * here and any slashes already stored are not doubled.
 */
export function formatPhonetic(value: string): string {
  const core = value.trim().replace(/^\/+|\/+$/g, '');
  return core.length > 0 ? `/${core}/` : '';
}

/** The book cover's initials: the leading letter of the first two words. */
export function bookInitials(title: string): string {
  return title
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
}

/**
 * The reference card's third line, `author · chapter`. Whichever half is blank
 * is omitted, so the separator never dangles.
 */
export function bookReferenceLine(author?: string | null, chapter?: string | null): string {
  return [author, chapter]
    .map((value) => (typeof value === 'string' ? value.trim() : ''))
    .filter(Boolean)
    .join(' · ');
}

/**
 * Evidence presence for the detail panel. The reference card renders exactly
 * title / author / chapter, so those three decide whether it exists at all;
 * the reader captures them as a set with `sourceBookId` and `sourceLocator`.
 */
export function hasBookReference(
  entry: Pick<DictionaryWordDto, 'sourceBookTitle' | 'sourceBookAuthor' | 'sourceChapter'>,
): boolean {
  return [entry.sourceBookTitle, entry.sourceBookAuthor, entry.sourceChapter].some(isNonBlank);
}

/** Whether the entry carries a captured quote (REQ-DRE-007 evidence). */
export function hasQuote(entry: Pick<DictionaryWordDto, 'quote'>): boolean {
  return isNonBlank(entry.quote);
}

/**
 * Whether the detail panel has anything to show past the term itself. Drives
 * 4C.3's empty state; a false value means the entry has no field at all.
 */
export function hasAnyDetail(
  entry: Pick<
    DictionaryWordDto,
    | 'definition'
    | 'partOfSpeech'
    | 'phonetic'
    | 'example'
    | 'quote'
    | 'sourceBookTitle'
    | 'sourceBookAuthor'
    | 'sourceChapter'
  >,
): boolean {
  return (
    isNonBlank(entry.definition) ||
    isNonBlank(entry.partOfSpeech) ||
    isNonBlank(entry.phonetic) ||
    isNonBlank(entry.example) ||
    hasQuote(entry) ||
    hasBookReference(entry)
  );
}

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * The row's date column. The frame draws relative dates ("Hoy", "Ayer") but its
 * own strings are sample data, so only the anatomy is binding: a short relative
 * label. Future or unparseable dates degrade to today / empty rather than throw.
 */
export function formatEntryDate(iso: string, now: Date, t: Translator): string {
  const created = Date.parse(iso);
  if (!Number.isFinite(created)) return '';

  const diffDays = Math.floor((now.getTime() - created) / DAY_MS);
  if (diffDays <= 0) return t('dictionary.dateToday');
  if (diffDays === 1) return t('dictionary.dateYesterday');
  if (diffDays < 7) return t('dictionary.dateDaysAgo', { n: diffDays });
  if (diffDays < 30) return t('dictionary.dateWeeksAgo', { n: Math.floor(diffDays / 7) });
  if (diffDays < 365) return t('dictionary.dateMonthsAgo', { n: Math.floor(diffDays / 30) });
  return t('dictionary.dateYearsAgo', { n: Math.floor(diffDays / 365) });
}
