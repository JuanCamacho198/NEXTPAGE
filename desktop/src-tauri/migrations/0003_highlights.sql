CREATE TABLE IF NOT EXISTS highlights (
  id TEXT PRIMARY KEY,
  book_id TEXT NOT NULL,
  color TEXT NOT NULL,
  text TEXT NOT NULL,
  page INTEGER NOT NULL,
  rect_left REAL NOT NULL,
  rect_right REAL NOT NULL,
  rect_top REAL NOT NULL,
  rect_bottom REAL NOT NULL,
  cfi TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  version INTEGER NOT NULL DEFAULT 1,
  FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bookmarks (
  id TEXT PRIMARY KEY,
  book_id TEXT NOT NULL,
  page INTEGER NOT NULL,
  position REAL NOT NULL,
  title TEXT,
  created_at TEXT NOT NULL,
  deleted_at TEXT,
  version INTEGER NOT NULL DEFAULT 1,
  FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_highlights_book_id ON highlights(book_id);
CREATE INDEX IF NOT EXISTS idx_bookmarks_book_id ON bookmarks(book_id);