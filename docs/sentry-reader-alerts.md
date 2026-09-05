# Sentry Reader Error Alerts

Setup guide for reader failure alerts across the Nextpage Sentry projects.
D.1/D.2 (saved searches, alert rule) are manual steps in the sentry.io UI;
this document records the exact queries, thresholds, and rollback procedure
so any maintainer can reproduce them.

## Projects

| Project | Platform | Purpose |
|---------|----------|---------|
| `nextpage-desktop` | Tauri (desktop renderer) | Reader errors routed via `handleError(err, 'reader')` with `source: reader` tag and context extras (PR1) |
| `nextpage-android` | Android | Typed reader events via `captureTypedEvent` with `source=reader` + `event` tags and structured extras (PR2) |

Organization: `nextpage-android`. Create the saved searches and alert rule in
**both** projects unless noted otherwise.

## Tag contract

Both platforms set the same tag keys so one query shape works everywhere:

- `source` = `reader` (set via `setTag` in PR1/PR2)
- `event` = typed event name (set via `setTag` in PR1/PR2)

If a query returns no results, verify in the Sentry event payload
(Tags section) that `source`/`event` are indexed tags rather than extras.
Adjust the field prefix (`tags[source,...]` vs `source:...`) to match what
Sentry actually indexes for the project SDK.

## Saved searches

Create each under **Issues > Search > Save Search** (one per project).
Names and queries must match exactly:

### 1. Reader — skipped highlights

Name: `Reader — skipped highlights`

```text
source:reader event:highlight_skipped
```

Covers highlight skips (e.g. out-of-viewport, unmapped CFI).

### 2. Reader — sync failures

Name: `Reader — sync failures`

```text
source:reader event:sync_outbox_failed
```

Covers highlight/bookmark sync outbox failures.

### 3. Reader — highlight load failures

Name: `Reader — highlight load failures`

```text
source:reader message:"Failed to load highlights"
```

Covers desktop highlight-load failures surfaced by message text
(where no dedicated `event` tag exists).

## Alert rule

Name: `Reader failure rate above threshold`

- Go to **Projects > [project] > Alerts > Create Alert > Issues**.
- Condition: issue count matching **any** of the 3 saved searches
  grows by **more than 10 events in 1 hour**.
- Environment: production (add staging only if noise is acceptable).
- Action: notify the default reader-alerts channel/recipients.
- Repeat for the second project (same name, same threshold).

Threshold rationale: reader failures are bursty (a bad release can skip
hundreds of highlights); 10/hour separates real regressions from isolated
user-local corruption.

## Structured extras reference

Use these keys when triaging alert events in Sentry
(Context/Additional Data section):

PR1 (desktop, via `handleError` context):

- `bookId`, `highlightId`, `cfi`, `format`

PR2 (Android, via `captureTypedEvent` extras):

- `highlightId`, `cfi`, `reason` (highlight skips)
- `entityType`, `entityId` (sync outbox failures)

PII note: user-typed content (note text, tag names, highlight text) is
scrubbed to `[Redacted]` before egress; only IDs, locators, and lengths
are stored.

## Rollback

- Alert/search changes: edit or delete the saved search or alert rule in
  the sentry.io UI (**Projects > Alerts** or **Issues > Saved Searches**).
  No code deploy required.
- This document: revert via `git revert <commit-sha>`.
