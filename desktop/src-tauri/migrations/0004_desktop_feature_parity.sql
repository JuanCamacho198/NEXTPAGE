CREATE TABLE IF NOT EXISTS app_settings (
  key TEXT PRIMARY KEY,
  value_json TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS book_covers (
  id TEXT PRIMARY KEY,
  book_id TEXT NOT NULL,
  storage_path TEXT NOT NULL,
  mime_type TEXT NOT NULL,
  width INTEGER,
  height INTEGER,
  byte_size INTEGER NOT NULL,
  checksum TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  version INTEGER NOT NULL DEFAULT 1,
  FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_book_covers_book_id_active
  ON book_covers(book_id)
  WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS reading_sessions (
  id TEXT PRIMARY KEY,
  book_id TEXT NOT NULL,
  started_at TEXT NOT NULL,
  ended_at TEXT,
  duration_seconds INTEGER NOT NULL DEFAULT 0,
  start_percentage REAL,
  end_percentage REAL,
  created_at TEXT NOT NULL,
  FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reading_sessions_book_id
  ON reading_sessions(book_id);

CREATE TABLE IF NOT EXISTS book_text_chunks (
  id TEXT PRIMARY KEY,
  book_id TEXT NOT NULL,
  locator TEXT NOT NULL,
  chunk_index INTEGER NOT NULL,
  text_content TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_book_text_chunks_book_id
  ON book_text_chunks(book_id);

CREATE VIRTUAL TABLE IF NOT EXISTS book_text_fts
USING fts5(
  chunk_id UNINDEXED,
  book_id UNINDEXED,
  locator UNINDEXED,
  text_content,
  tokenize = 'unicode61 remove_diacritics 2'
);
