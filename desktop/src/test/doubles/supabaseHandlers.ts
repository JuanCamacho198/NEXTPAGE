import { http, HttpResponse } from 'msw';

export const TEST_SUPABASE_URL = 'http://127.0.0.1:43123';
export const TEST_SUPABASE_ANON_KEY = 'test-anon-key.msw-doubles';

export interface PostgrestBookRow {
  id: string;
  user_id: string;
  title: string;
  author: string | null;
  format: string;
  content_hash: string | null;
  file_path: string | null;
  cover_url: string | null;
  description: string | null;
  total_pages: number | null;
  source_device: string | null;
  imported_at: string;
  updated_at: string;
}

export function catalogFixtureRows(): PostgrestBookRow[] {
  return [
    {
      id: 'book-msw-1',
      user_id: 'user-1',
      title: 'MSW Fixture Book One',
      author: 'Fixture Author',
      format: 'epub',
      content_hash: 'sha256:fixture-1',
      file_path: null,
      cover_url: null,
      description: null,
      total_pages: 250,
      source_device: 'desktop',
      imported_at: '2026-01-01T00:00:00.000Z',
      updated_at: '2026-02-01T00:00:00.000Z',
    },
    {
      id: 'book-msw-2',
      user_id: 'user-1',
      title: 'MSW Fixture Book Two',
      author: null,
      format: 'pdf',
      content_hash: null,
      file_path: null,
      cover_url: null,
      description: null,
      total_pages: null,
      source_device: 'android',
      imported_at: '2026-01-15T00:00:00.000Z',
      updated_at: '2026-02-15T00:00:00.000Z',
    },
  ];
}

export function supabaseCatalogHandlers(rows: PostgrestBookRow[] = catalogFixtureRows()) {
  return [http.get(`${TEST_SUPABASE_URL}/rest/v1/user_books`, () => HttpResponse.json(rows))];
}

export function supabaseAuthHandlers() {
  return [
    http.get(`${TEST_SUPABASE_URL}/auth/v1/user`, () =>
      HttpResponse.json({ id: 'user-1', email: 'fixture@example.com' }),
    ),
  ];
}

export function supabaseAuthErrorHandlers() {
  return [
    http.get(`${TEST_SUPABASE_URL}/rest/v1/user_books`, () =>
      HttpResponse.json({ message: 'Auth 401: JWT expired', code: '401' }, { status: 401 }),
    ),
  ];
}
