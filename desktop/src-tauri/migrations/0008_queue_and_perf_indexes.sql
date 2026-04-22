CREATE TABLE IF NOT EXISTS jobs (
  id TEXT PRIMARY KEY,
  job_type TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  state TEXT NOT NULL DEFAULT 'queued' CHECK (state IN ('queued', 'running', 'failed', 'done')),
  attempt INTEGER NOT NULL DEFAULT 0 CHECK (attempt >= 0),
  max_attempts INTEGER NOT NULL DEFAULT 3 CHECK (max_attempts > 0 AND max_attempts <= 25),
  next_run_at TEXT NOT NULL,
  lease_expires_at TEXT,
  dedupe_key TEXT,
  last_error TEXT,
  result_json TEXT,
  started_at TEXT,
  finished_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  CHECK (
    (state = 'running' AND lease_expires_at IS NOT NULL)
    OR (state != 'running')
  )
);

CREATE INDEX IF NOT EXISTS idx_jobs_state_next_run_at
  ON jobs (state, next_run_at);

CREATE INDEX IF NOT EXISTS idx_jobs_lease_expiration
  ON jobs (state, lease_expires_at);

CREATE INDEX IF NOT EXISTS idx_jobs_type_state
  ON jobs (job_type, state);

CREATE UNIQUE INDEX IF NOT EXISTS idx_jobs_active_dedupe_key
  ON jobs (dedupe_key)
  WHERE dedupe_key IS NOT NULL AND state IN ('queued', 'running');

CREATE INDEX IF NOT EXISTS idx_books_library_visibility_updated
  ON books (deleted_at, hidden_at, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_highlights_book_page
  ON highlights (book_id, page, rect_top)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_reading_progress_book_updated
  ON reading_progress (book_id, updated_at DESC)
  WHERE deleted_at IS NULL;
