# Architecture docs

The "why" tier. Architecture docs explain component shapes, data flows, and the key decisions that shaped the system. They are written for engineers who need to extend or change behavior without re-deriving intent from the code.

## Purpose

An architecture doc answers one question: **how does data or control move through the system, and why was it shaped that way?** Each doc covers one flow end-to-end and links every code path it touches to a Pencil node so design and code stay in lock-step.

The Auth flow is the canonical example. Read `data-flow.md` first; copy its skeleton for any new flow you document (e.g. sync, library import, reading progress).

## Template

The reference worked example is `data-flow.md` (in this folder). Lock in this structure for any new flow doc:

1. **Outcome-oriented title** — start with the verb and the path (e.g. "Trace the Auth flow from Compose to Google One Tap (Credential Manager) to session bootstrap").
2. **One-paragraph summary** — name the actors and the outcome in five lines or fewer.
3. **`## Quick path`** — three numbered steps the reader can execute to follow the flow in code.
4. **`## Details`** — at least two subsections: call chain (with a Mermaid `sequenceDiagram`) and UI states. For `data class`-based state holders (e.g., `AuthUiState(isLoading, errorMessage, currentSession)`), use a `flowchart` of flag transitions. Use `stateDiagram-v2` only for sealed-class state machines (none in the codebase today). Use tables for facts, not prose.
5. **`## Checklist`** — one checkbox per spec scenario the doc satisfies.
6. **`## Next step`** — link to a related doc.
7. **`## Design Traceability`** — table mapping each code path to its Pencil node ID. Domain and data layers have no node by design; the table is the contract.

## Checklist for a new architecture doc

- [ ] Title is outcome-oriented (verb + path).
- [ ] One-paragraph summary names every actor.
- [ ] `## Quick path` has ≥ 3 numbered steps.
- [ ] `## Details` has a `sequenceDiagram` AND a state representation (a Mermaid `flowchart` for `data class` state holders, or `stateDiagram-v2` for sealed-class state machines — or strong reason to omit one).
- [ ] `## Design Traceability` references every code path the doc touches.
- [ ] `## Next step` points to the next doc a reader should open.

## Next step

Open `data-flow.md` for the Auth worked example.
