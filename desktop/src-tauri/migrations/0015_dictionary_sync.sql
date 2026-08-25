-- 0015_dictionary_sync: add per-user sync columns to dictionary_words and migrate UNIQUE
-- Additive columns with defaults to preserve existing rows.
ALTER TABLE dictionary_words ADD COLUMN user_id TEXT NOT NULL DEFAULT '';
ALTER TABLE dictionary_words ADD COLUMN tags_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE dictionary_words ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0;
ALTER TABLE dictionary_words ADD COLUMN srs_stage INTEGER NOT NULL DEFAULT 0 CHECK (srs_stage BETWEEN 0 AND 5);
ALTER TABLE dictionary_words ADD COLUMN updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'));
ALTER TABLE dictionary_words ADD COLUMN deleted_at TEXT;
ALTER TABLE dictionary_words ADD COLUMN synced_at TEXT;

-- Backfill updated_at from created_at where still default
UPDATE dictionary_words SET updated_at = created_at WHERE updated_at IS NULL OR updated_at = '';

-- New per-user uniqueness and LWW index
CREATE UNIQUE INDEX IF NOT EXISTS idx_dict_user_norm ON dictionary_words(user_id, normalized_word);
CREATE INDEX IF NOT EXISTS idx_dict_updated ON dictionary_words(updated_at);
CREATE INDEX IF NOT EXISTS idx_dict_user_fav ON dictionary_words(user_id) WHERE is_favorite = 1;
