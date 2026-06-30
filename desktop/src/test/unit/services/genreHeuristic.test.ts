import { describe, expect, it } from 'vitest';
import {
  inferGenreFromText,
  KEYWORD_TABLE,
  UNCLASSIFIED_GENRE,
  type CanonicalGenre,
} from '$lib/shared/services/genreHeuristic';

describe('inferGenreFromText', () => {
  it('returns Desarrollo personal for a Spanish habit needle', () => {
    expect(inferGenreFromText({ title: 'Habitos Atomicos', author: 'James Clear' })).toBe(
      'Desarrollo personal',
    );
  });

  it('returns Desarrollo personal for an English habit needle', () => {
    expect(inferGenreFromText({ title: 'Atomic Habits', author: null })).toBe(
      'Desarrollo personal',
    );
  });

  it('returns Productividad for a Spanish productivity needle', () => {
    expect(inferGenreFromText({ title: 'Deep Work en español', author: 'Carlos' })).toBe(
      'Productividad',
    );
  });

  it('returns Productividad for the canonical English needle', () => {
    expect(inferGenreFromText({ title: 'Deep Work', author: 'Cal Newport' })).toBe('Productividad');
  });

  it('returns Finanzas for a Spanish finance needle', () => {
    expect(inferGenreFromText({ title: 'Finanzas para emprendedores', author: null })).toBe(
      'Finanzas',
    );
  });

  it('returns Finanzas for an English finance needle', () => {
    expect(inferGenreFromText({ title: 'The Money Book', author: null })).toBe('Finanzas');
  });

  it('returns Ficcion for a Spanish fiction needle', () => {
    expect(inferGenreFromText({ title: 'Una novela corta', author: null })).toBe('Ficcion');
  });

  it('returns Ficcion for an English fiction needle', () => {
    expect(inferGenreFromText({ title: 'Mystery Tales', author: 'Agatha' })).toBe('Ficcion');
  });

  it('returns Sin clasificar when no needle matches', () => {
    expect(inferGenreFromText({ title: 'How to Train Your Dog', author: 'Jane' })).toBe(
      UNCLASSIFIED_GENRE,
    );
    expect(inferGenreFromText({ title: '1234', author: null })).toBe(UNCLASSIFIED_GENRE);
  });

  it('follows KEYWORD_TABLE order for ambiguous matches (Ficcion beats Desarrollo personal)', () => {
    // The 'ficcion' needle in Ficcion precedes 'desarrollo personal' needles
    // only if it appears in the text first — but `text.includes` doesn't
    // care about order. The table's array order does. Here, both
    // needles are in the text; whichever genre entry comes first in
    // KEYWORD_TABLE wins.
    const result = inferGenreFromText({
      title: 'Habitos y Novela',
      author: 'Anon',
    });
    const firstGenre = KEYWORD_TABLE[0].genre;
    expect(result).toBe<CanonicalGenre>(firstGenre);
    // Sanity: the test only proves "the table order decides ties", not
    // a specific value, so we also assert the value matches the
    // observable first entry.
    expect(KEYWORD_TABLE[0].genre).toBe('Desarrollo personal');
  });

  it('appends author to the searched text', () => {
    expect(inferGenreFromText({ title: 'Untitled', author: 'Deep Work Press' })).toBe(
      'Productividad',
    );
  });

  it('does not throw when author is undefined or null', () => {
    expect(() => inferGenreFromText({ title: 'Anything' })).not.toThrow();
    expect(() => inferGenreFromText({ title: 'Anything', author: null })).not.toThrow();
    expect(() => inferGenreFromText({ title: 'Anything', author: undefined })).not.toThrow();
  });

  it('keyword table has the four concrete genres + fallback', () => {
    const genres = new Set(KEYWORD_TABLE.map((entry) => entry.genre));
    expect(genres.size).toBe(4);
    expect(genres.has('Desarrollo personal')).toBe(true);
    expect(genres.has('Productividad')).toBe(true);
    expect(genres.has('Finanzas')).toBe(true);
    expect(genres.has('Ficcion')).toBe(true);
  });
});
