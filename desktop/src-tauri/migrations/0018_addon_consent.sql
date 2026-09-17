-- 0018_addon_consent: per-addon network-consent markers for addon resolve.
-- Fail-closed by design: an absent row means denied. Uninstall revokes the
-- row atomically (deleteInstalledAddon deletes it on the same connection),
-- so a reinstall can never inherit consent. No backfill: existing installs
-- start denied until the user grants consent in the Addons screen.
CREATE TABLE IF NOT EXISTS addon_consent (
  addon_id TEXT PRIMARY KEY,
  granted INTEGER NOT NULL CHECK(granted IN (0, 1)),
  updated_at INTEGER NOT NULL
);
