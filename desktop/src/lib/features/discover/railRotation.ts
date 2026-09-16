import type { MessageKey } from '$lib/shared/i18n/messages.en';
import { CHIP_KEYWORDS, TRENDING_CHIPS } from './discoverChips';

/** One day's thematic rail: the i18n title key plus its Gutendex search term. */
export interface ThematicRotationEntry {
  titleKey: MessageKey;
  term: string;
}

/**
 * Spanish trending-chip label → rail i18n key. Rail titles need their own keys
 * because `TRENDING_CHIPS` holds plain literals and chip behavior must not change.
 */
const THEMATIC_TITLE_KEYS: Record<string, MessageKey> = {
  Ficción: 'discover.rail.thematic.fiction',
  Clásicos: 'discover.rail.thematic.classic',
  Aventura: 'discover.rail.thematic.adventure',
  Misterio: 'discover.rail.thematic.mystery',
  Romance: 'discover.rail.thematic.romance',
  'Ciencia ficción': 'discover.rail.thematic.science',
  Historia: 'discover.rail.thematic.history',
};

/**
 * The single tunable rotation constant. Labels are the existing `TRENDING_CHIPS`
 * entries in their existing order and each term is the FIRST keyword already
 * mapped for that label in `CHIP_KEYWORDS` — the mapping is reused, never forked.
 *
 * Pinned set: Ficción→fiction, Clásicos→classic, Aventura→adventure,
 * Misterio→mystery, Romance→romance, Ciencia ficción→science, Historia→history.
 */
export const THEMATIC_ROTATION: readonly ThematicRotationEntry[] = TRENDING_CHIPS.map((label) => {
  const titleKey = THEMATIC_TITLE_KEYS[label];
  const term = CHIP_KEYWORDS[label]?.[0];
  if (!titleKey || !term) {
    throw new Error(`thematic rotation label outside the trending-chip taxonomy: ${label}`);
  }
  return { titleKey, term };
});

/** 1-based local day-of-year; UTC arithmetic over local parts keeps it DST-proof. */
export function dayOfYear(date: Date): number {
  const startOfYear = Date.UTC(date.getFullYear(), 0, 1);
  const today = Date.UTC(date.getFullYear(), date.getMonth(), date.getDate());
  return Math.floor((today - startOfYear) / 86_400_000) + 1;
}

/** Spec formula: the rotation index is `dayOfYear mod listLength`. */
export function thematicIndexFor(
  dayIndex: number,
  length: number = THEMATIC_ROTATION.length,
): number {
  if (!Number.isInteger(length) || length < 1) {
    throw new Error(`invalid thematic rotation length: ${length}`);
  }
  return ((Math.floor(dayIndex) % length) + length) % length;
}

/** Same local calendar day ⇒ same entry: no re-roll across renders or retries. */
export function thematicEntryFor(date: Date = new Date()): ThematicRotationEntry {
  const entry = THEMATIC_ROTATION[thematicIndexFor(dayOfYear(date))];
  if (!entry) throw new Error('thematic rotation list is empty');
  return entry;
}
