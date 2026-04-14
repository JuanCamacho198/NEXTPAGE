# Proposal: Desktop Auth and Google Drive Sync

## Intent

Implement real Google OAuth login with Tauri 2 and Google Drive integration for cross-device synchronization. This enables users to keep their library metadata and reading progress in sync between the Android and Desktop versions of NextPage using Supabase for identity and metadata, and Google Drive for optional book file storage.

## Scope

### In Scope
- Configure Tauri 2 Deep Link plugin for OAuth redirects from the browser.
- Implement Supabase Auth with Google provider in the desktop app (Svelte 5).
- Create a sync service in the desktop app that mirrors the Android `SupabaseSyncService` for book metadata and progress.
- Introduce an optional "Google Drive Provider" for storing book files, ensuring compatibility with the existing Android file structure.
- Persistence of Supabase sessions and Google Drive tokens.

### Out of Scope
- Full Google Drive file browser (only sync specific folders).
- Manual file merging UI (automated sync only).
- iOS support.
- Direct file uploads to Supabase Storage from Desktop (using GDrive instead for files if enabled).

## Approach

1. **Auth**: Use Supabase Auth with the Google provider. On Desktop, this requires handling a custom protocol (Deep Link) via a Tauri plugin to capture the OAuth code/token after the user signs in via their default browser.
2. **Metadata Sync**: Port logic from Android's sync service to Svelte 5 stores/services. This will sync the SQLite database metadata with Supabase tables.
3. **Storage**: Implement a `GoogleDriveStorageProvider` that uses the Google Drive API. It will look for a specific folder (e.g., `NextPage/Books`) to mirror the Android structure.
4. **Integration**: The sync service will be abstracted to support both local-only and cloud-synced modes, triggered by the user's auth state.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `desktop/src-tauri/Cargo.toml` | Modified | Add `tauri-plugin-deep-link` dependency. |
| `desktop/src-tauri/src/main.rs` | Modified | Initialize deep-link plugin and handle protocol events. |
| `desktop/src/lib/services/auth.ts` | New | Supabase Auth client and session management. |
| `desktop/src/lib/services/sync.ts` | New | Metadata sync service mirroring Android logic. |
| `desktop/src/lib/services/storage/gdrive.ts` | New | Google Drive API integration for file sync. |
| `desktop/package.json` | Modified | Add `@supabase/supabase-js` and Google API client libraries. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Deep link reliability | Medium | Provide a "Copy-Paste Code" fallback in case the protocol isn't registered correctly. |
| Sync conflicts | Low | Use timestamps and "last writer wins" for metadata; book files are immutable once uploaded. |
| Google API Quotas | Low | Use standard app-specific folder access to minimize permissions and impact. |

## Rollback Plan

- Disable the sync service feature flag in settings.
- Revert changes to `Cargo.toml` and `package.json` to remove new plugins and libraries.
- The app will continue to work in local-only mode using the existing SQLite database.

## Dependencies

- Supabase Project with Google Auth enabled.
- Google Cloud Console project with Drive API enabled and OAuth credentials.
- Tauri 2 Deep Link plugin.

## Success Criteria

- [ ] User can sign in using Google via an external browser and return to the app automatically.
- [ ] Book metadata (titles, authors) created on Android appears on Desktop after sync.
- [ ] Reading progress (last page read) syncs between devices.
- [ ] Books stored in Google Drive by Android can be downloaded and opened by Desktop.
- [ ] Session persists after app restart.
