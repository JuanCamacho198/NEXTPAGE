# Onboarding

The "how-to-start" tier. Onboarding docs are for a contributor who has never opened the project. They cover the smallest set of steps that get them from a fresh clone to a first successful contribution.

## Purpose

An onboarding doc answers one question: **what is the minimum I must do to be productive?** Each doc covers a single onboarding scenario (first build, first deploy, first contribution) and points to the next doc the reader should open. Nothing here is reference material — that lives in `../architecture/` or in source KDoc.

## Template

The reference structure for any new onboarding doc:

1. **Outcome-oriented title** — the scenario and what success looks like (e.g. "Build the debug APK from a fresh clone").
2. **One-paragraph summary** — what the doc covers and the time budget a contributor should plan for.
3. **`## Quick path`** — three numbered steps that take the reader from zero to a verified result. Each step ends with a visible outcome (a command output, a screen, a file).
4. **`## Details`** — at least two subsections: prerequisites (table) and common pitfalls (table). Use tables; avoid prose walls.
5. **`## Checklist`** — what the contributor should be able to do after the doc.
6. **`## Next step`** — link to the next onboarding doc or to the architecture entry point.

## Checklist for a new onboarding doc

- [ ] Title is scenario-oriented (e.g. "First build", "First contribution").
- [ ] `## Quick path` has ≥ 3 steps, each with a visible outcome.
- [ ] `## Details` lists prerequisites and common pitfalls as tables.
- [ ] `## Checklist` matches what a successful reader can do.
- [ ] `## Next step` points to the next doc the reader should open.

## Next step

Until per-scenario docs are authored, the architecture entry point is `../architecture/data-flow.md` (the Auth worked example) and the Pencil ↔ code map is `../design-traceability.md`.
