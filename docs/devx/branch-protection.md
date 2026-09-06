# Branch Protection — `main`

This document captures the **one-time, out-of-band admin steps** required to
turn the new CI workflows into real merge gates. Workflow files in `.github/`
trigger the jobs, but the gate itself lives in GitHub's repository settings.

## Prerequisites

- The two CI workflows from PR #1 (`feat/devx-ci-gates`) are merged into
  `main`.
- At least one PR has run the new jobs to completion with a **green** result
  on each, so the status-check names show up in the search dropdown.

## Steps — repo admin only

1. Open the repository on GitHub.
2. Go to **Settings → Branches**.
3. Under **Branch protection rules**, edit (or create) the rule targeting
   **`main`**.
4. Enable **Require status checks to pass before merging**.
5. In the status-checks search box, add both of the following checks
   (the slash separates the workflow name from the job name):
   - `Desktop CI / desktop-checks`
   - `Android CI / android-checks`
6. Leave the rest of the rule at project defaults unless a stronger policy
   is needed (e.g. require linear history, restrict to admins, etc.).
7. Save changes.

After this, any push to `main` that fails either job is blocked at merge time
— exactly the gate that would have caught the sentry-observability-v2 PR3
file-not-found drift.

## WS2 follow-up (release-please) — NOT in this PR

PR #2 (`feat/devx-release-notes`) adds a third workflow that needs a Personal
Access Token secret. The PAT setup steps (`RELEASE_PLEASE_TOKEN`, scope:
`contents: write` + `pull-requests: write`) and bootstrap-tag creation are
documented in that PR's companion `docs/devx/branch-protection.md` update,
**not** here.
