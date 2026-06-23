# UI docs

The "what" tier. UI docs describe each screen's user-visible behavior, states, and inputs. They mirror the package layout under `presentation/screen/` so a screen and its docs sit side by side in concept.

## Purpose

A UI doc answers one question: **what does the user see, what can they do, and what does each state look like?** It is the bridge between the Pencil design (source of visual truth) and the Compose code (source of behavior truth). Per-screen docs go in this folder, one file per screen, named after the screen.

Until the per-screen docs are written, the canonical Pencil node ↔ screen index lives at `../design-traceability.md` (use that as the starting list when you author a new screen doc).

## Template

The reference structure for any new screen doc:

1. **Outcome-oriented title** — the screen name plus what it does (e.g. "AuthScreen — sign in or sign up with email and Google").
2. **One-paragraph summary** — purpose, primary user goal, and the Pencil node ID.
3. **`## Quick path`** — three steps a reader can take in code or design to verify the doc matches reality.
4. **`## Details`** — at least two subsections: visual states (with a Mermaid `stateDiagram-v2` mirroring the ViewModel state) and inputs/handlers (table mapping each user action to a callback or ViewModel method).
5. **`## Checklist`** — visual states covered, design node referenced, callbacks documented.
6. **`## Next step`** — link to the next screen in the user journey, or to the architecture doc that explains the data flow.
7. **`## Design Traceability`** — Pencil node ID and the Compose files that implement the screen.

## Checklist for a new UI doc

- [ ] Title includes the screen name and its purpose.
- [ ] Pencil node ID is quoted (e.g. `W29xCr` for AuthScreen).
- [ ] `## Quick path` has ≥ 3 numbered steps.
- [ ] `## Details` covers all user-visible states from the ViewModel.
- [ ] `## Design Traceability` lists the screen file and the ViewModel that backs it.
- [ ] Pointer to the next screen in the user flow.

## Next step

Use `../design-traceability.md` to find the next screen to document. Start with AuthScreen (`W29xCr`) using `data-flow.md` as the architecture reference.
