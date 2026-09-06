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

## WS2 follow-up (release-please) — added in PR #2

PR #2 (`feat/devx-release-please`) adds the `Release Notes` workflow
(`.github/workflows/release-notes.yml`) that calls
`googleapis/release-please-action@v4`. The action needs a Personal Access Token
secret and one-time bootstrap tags before the first run produces a release PR.

### A. `RELEASE_PLEASE_TOKEN` — Personal Access Token

The default `GITHUB_TOKEN` does **not** carry the scopes release-please needs
to open PRs and manage labels. A PAT with explicit scopes is required.

**Required scopes** (Classic PAT, fine-grained PATs work too with matching
repository permissions):

- `contents: write` — commit version bumps + push tags
- `pull-requests: write` — open / update the release PR
- `issues: write` — manage the `autorelease: pending` / `autorelease: tagged`
  labels

**Steps — repo admin only, once per token rotation**:

1. Open <https://github.com/settings/tokens>.
2. Generate a new token (classic) with the three scopes above. Expiration:
   match your security policy; rotate before expiry.
3. In the repository, go to **Settings → Secrets and variables → Actions**.
4. Under **Repository secrets**, click **New repository secret**.
5. Name: `RELEASE_PLEASE_TOKEN`. Value: paste the PAT.
6. Save.

After this, the `Release Notes` workflow will pick up the token from
`${{ secrets.RELEASE_PLEASE_TOKEN }}`. Missing token fails the workflow
closed (non-zero exit, no silent skip) per the `release-please-action`
contract.

### B. Bootstrap tags — one-time, manual

`release-please` walks commits since the last tag to decide whether to bump.
Before the very first workflow run after PR #2 merges, seed tags at the
current `main` HEAD so the first conventional commit produces the right
increment.

```bash
# From a clean clone with the PR #2 merge commit checked out.
git checkout main
git pull --ff-only

# Tag the current tip with the bootstrap versions.
git tag desktop-v0.1.0  $(git rev-parse HEAD)
git tag android-v1.0.0  $(git rev-parse HEAD)

# Push both tags to origin in a single round-trip.
git push origin --tags
```

Both tags must point to a commit on `main` so release-please picks them up as
the "last released" anchor. The names follow release-please's
`<package>-v<version>` convention (see
`release-please-config.json` → `packages.desktop.path = desktop` and
`packages.android.path = android`).

### C. Sanity check — first release PR appears

After the PAT and bootstrap tags are in place:

1. Open a small `chore:` or `feat(desktop):` PR against `main`.
2. Merge it (squash or merge commit — release-please parses both).
3. Within ~5 minutes, the `Release Notes` workflow should open (or update) a
   release PR titled `chore(main): release <new-version>` with a populated
   `CHANGELOG.md` section and the version files bumped.
4. If no release PR appears: check the workflow run logs, confirm the token
   has the three scopes, and confirm the bootstrap tags are present on
   origin (`git ls-remote --tags origin | grep -E 'desktop-v|android-v'`).

Once the sanity check passes, repo admin can optionally enable branch
protection on `main` requiring the `Release Notes / release-please` job as
well (advisory at first — release PRs typically bypass required checks via
the `[skip ci]` pattern release-please adds).
