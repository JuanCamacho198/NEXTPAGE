-- 0016_discover_cache: isolated TTL cache for public-domain catalog pages/details.
-- Separate table by design: catalog traffic must never touch user_books/outbox.
-- Keys: p:{provider}:{query}:{page} (24h) for search pages,
--       d:{provider}:{id} (7d) for details. Payload is opaque JSON.
CREATE TABLE IF NOT EXISTS discover_cache (
  key TEXT PRIMARY KEY,
  payload TEXT NOT NULL,
  fetched_at INTEGER NOT NULL,
  ttl_s INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_discover_cache_fetched_at ON discover_cache(fetched_at);
