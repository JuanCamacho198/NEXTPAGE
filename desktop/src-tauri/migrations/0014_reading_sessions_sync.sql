-- User-scoped reading sessions sync (desktop <-> Android via Supabase).
-- Additive: legacy uuidv4 rows survive with user_id='' (never synced, still
-- counted locally via `OR user_id = ''`), updated_at_epoch_millis=0, date NULL.
ALTER TABLE reading_sessions ADD COLUMN user_id TEXT NOT NULL DEFAULT '';
ALTER TABLE reading_sessions ADD COLUMN date TEXT;
ALTER TABLE reading_sessions ADD COLUMN updated_at_epoch_millis INTEGER NOT NULL DEFAULT 0;
