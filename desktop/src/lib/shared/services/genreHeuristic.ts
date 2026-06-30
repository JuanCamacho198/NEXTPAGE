/**
 * Pure keyword-based genre heuristic.
 *
 * Runs only when no embedded subject is available (EPUB <dc:subject> or PDF
 * info-dict `Subject` / `Keywords`). It scans the lowercased
 * `title + " " + author` for the first matching keyword in a frozen
 * Spanish+English table. The first table entry that matches wins, so the
 * order of `KEYWORD_TABLE` defines the precedence: 'Desarrollo personal'
 * beats 'Ficcion' if both needles match, etc.
 *
 * The function is exported alongside `KEYWORD_TABLE` and the `CanonicalGenre`
 * union so import-time wiring can call it directly and the stats screen can
 * reuse the same label set.
 */
export type CanonicalGenre =
  | 'Desarrollo personal'
  | 'Productividad'
  | 'Finanzas'
  | 'Ficcion'
  | 'Sin clasificar';

export type ConcreteCanonicalGenre = Exclude<CanonicalGenre, 'Sin clasificar'>;

export const UNCLASSIFIED_GENRE: CanonicalGenre = 'Sin clasificar';

export const KEYWORD_TABLE: ReadonlyArray<{
  genre: ConcreteCanonicalGenre;
  needles: readonly string[];
}> = [
  {
    genre: 'Desarrollo personal',
    needles: [
      'habitos',
      'hábitos',
      'habit',
      'mindset',
      'mejora',
      'autoayuda',
      'felicidad',
      'psicologia',
      'psicología',
      'self',
      'vida',
      'atomic',
    ],
  },
  {
    genre: 'Productividad',
    needles: [
      'deep work',
      'productividad',
      'productivity',
      'eficiencia',
      'eficacia',
      'metodologia',
      'organizacion',
      'focus',
      'workflow',
      'effectiveness',
    ],
  },
  {
    genre: 'Finanzas',
    needles: [
      'finanzas',
      'inversion',
      'inversión',
      'bolsa',
      'dinero',
      'riqueza',
      'finance',
      'investing',
      'money',
      'wealth',
      'trading',
    ],
  },
  {
    genre: 'Ficcion',
    needles: [
      'novela',
      'cuento',
      'ficcion',
      'ficción',
      'fantasia',
      'fantasía',
      'misterio',
      'fiction',
      'fantasy',
      'mystery',
      'sci-fi',
      'romance',
    ],
  },
];

export const inferGenreFromText = (input: {
  title: string;
  author?: string | null;
}): CanonicalGenre => {
  const text = `${input.title} ${input.author ?? ''}`.toLowerCase();
  for (const entry of KEYWORD_TABLE) {
    if (entry.needles.some((needle) => text.includes(needle))) {
      return entry.genre;
    }
  }
  return UNCLASSIFIED_GENRE;
};
