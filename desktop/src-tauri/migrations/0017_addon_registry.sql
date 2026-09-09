CREATE TABLE installed_addons (
    id TEXT PRIMARY KEY,
    url TEXT NOT NULL UNIQUE,
    manifest_json TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0, 1)),
    added_at INTEGER NOT NULL
);
