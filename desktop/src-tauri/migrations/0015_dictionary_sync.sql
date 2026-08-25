-- 0015_dictionary_sync: add per-user sync columns and migrate UNIQUE to per-user
-- Step 1: additive columns (existing rows keep defaults)
ALTER TABLE dictionary_words ADD COLUMN user_id TEXT NOT NULL DEFAULT '';
ALTER TABLE dictionary_words ADD COLUMN tags_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE dictionary_words ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0;
ALTER TABLE dictionary_words ADD COLUMN srs_stage INTEGER NOT NULL DEFAULT 0 CHECK (srs_stage BETWEEN 0 AND 5);
ALTER TABLE dictionary_words ADD COLUMN updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'));
ALTER TABLE dictionary_words ADD COLUMN deleted_at TEXT;
ALTER TABLE dictionary_words ADD COLUMN synced_at TEXT;

UPDATE dictionary_words SET updated_at = created_at WHERE updated_at IS NULL OR updated_at = '';

-- Step 2: recreate table to drop legacy global UNIQUE(normalized_word) and keep per-user UNIQUE
-- This is safe for both fresh and existing DBs; data is preserved via INSERT OR REPLACE.
DROP TABLE IF EXISTS dictionary_words_new;
CREATE TABLE dictionary_words_new (
  id TEXT PRIMARY KEY,
  word TEXT NOT NULL,
  normalized_word TEXT NOT NULL,
  user_id TEXT NOT NULL DEFAULT '',
  tags_json TEXT NOT NULL DEFAULT '[]',
  is_favorite INTEGER NOT NULL DEFAULT 0,
  srs_stage INTEGER NOT NULL DEFAULT 0 CHECK (srs_stage BETWEEN 0 AND 5),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
  deleted_at TEXT,
  synced_at TEXT
);
INSERT OR IGNORE INTO dictionary_words_new (id, word, normalized_word, user_id, tags_json, is_favorite, srs_stage, created_at, updated_at, deleted_at, synced_at)
  SELECT id, word, normalized_word, COALESCE(user_id,''), COALESCE(tags_json,'[]'), COALESCE(is_favorite,0), COALESCE(srs_stage,0), created_at, COALESCE(updated_at, created_at), deleted_at, synced_at FROM dictionary_words;
DROP TABLE dictionary_words;
ALTER TABLE dictionary_words_new RENAME TO dictionary_words;

CREATE UNIQUE INDEX IF NOT EXISTS idx_dict_user_norm ON dictionary_words(user_id, normalized_word);
CREATE INDEX IF NOT EXISTS idx_dict_updated ON dictionary_words(updated_at);
CREATE INDEX IF NOT EXISTS idx_dict_user_fav ON dictionary_words(user_id) WHERE is_favorite = 1;
CREATE INDEX IF NOT EXISTS idx_dictionary_words_normalized ON dictionary_words(normalized_word);
