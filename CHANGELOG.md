# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- (future features go here)

### Changed
- (improvements go here)

### Fixed
- (bug fixes go here)

---

## [1.0.0] - YYYY-MM-DD

### Added
- Initial release
- EPUB reading support
- PDF reading support (basic)
- Library management (import, delete)
- Reading progress tracking
- Bookmarks and highlights
- Reading statistics (time per book, total time)
- Clean Architecture implementation
- Jetpack Compose UI
- Room database

### Features
- 📖 Import EPUB books from device storage
- 📄 Import PDF books from device storage
- 🔖 Create bookmarks and highlights
- 📊 Track reading time per book
- 🗑️ Delete books with confirmation
- 🔍 Go-to-page dialog for PDFs
- 📑 Page/chapter counter display

### Technical
- Kotlin + Jetpack Compose
- Material 3 design
- Room for local storage
- Coroutines + Flow for async
- Clean Architecture (domain/data/presentation)

---

## Upgrade Guide

### From 0.x to 1.0.0

1. Install the new APK
2. Your existing library data will be preserved in local storage
3. No migration needed for local-first approach

---

## Older Versions

No changelog entries for versions before 1.0.0.

---

## Release Cadence

We aim to release new versions:
- **Bug fixes**: As needed (weekly if critical)
- **Minor features**: Monthly
- **Major versions**: Quarterly

## Deprecation Policy

Before removing any feature, we will:
1. Mark as deprecated in release notes
2. Keep for at least one major version
3. Provide migration path

---

## Known Issues

- PDF text selection not yet supported
- Cloud sync requires Supabase configuration
- No dark mode yet

---

## Footnote

This changelog was created following standard OSS practices. If you notice any issues or have suggestions, please open an issue or PR.