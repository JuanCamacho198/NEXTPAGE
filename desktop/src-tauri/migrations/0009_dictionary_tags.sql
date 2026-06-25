CREATE TABLE IF NOT EXISTS dictionary_words (
  id TEXT PRIMARY KEY,
  word TEXT NOT NULL,
  normalized_word TEXT NOT NULL UNIQUE,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tags (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  normalized_name TEXT NOT NULL UNIQUE,
  color TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS highlight_tags (
  highlight_id TEXT NOT NULL,
  tag_id TEXT NOT NULL,
  created_at TEXT NOT NULL,
  PRIMARY KEY (highlight_id, tag_id),
  FOREIGN KEY (highlight_id) REFERENCES highlights(id) ON DELETE CASCADE,
  FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_dictionary_words_normalized ON dictionary_words(normalized_word);
CREATE INDEX IF NOT EXISTS idx_tags_normalized ON tags(normalized_name);
CREATE INDEX IF NOT EXISTS idx_highlight_tags_tag ON highlight_tags(tag_id);
