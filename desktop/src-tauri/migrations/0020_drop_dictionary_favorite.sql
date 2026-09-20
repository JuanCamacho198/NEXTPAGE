-- 0020_drop_dictionary_favorite: remove the dead `is_favorite` column from
-- `dictionary_words`. No code path ever read it to change behaviour, so the
-- column only travelled on every sync and export.
--
-- SQLite's ALTER TABLE ... DROP COLUMN (bundled SQLite 3.46, well above the
-- 3.35 floor) refuses to drop a column while an index references it, so the
-- partial index from 0015 goes first. Both statements are idempotent: a
-- replayed migration drops nothing twice.
DROP INDEX IF EXISTS idx_dict_user_fav;
ALTER TABLE dictionary_words DROP COLUMN is_favorite;
