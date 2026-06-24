# Selection Menu Flow

When the user selects text in a book, the reader shows two sequential menus:

## Primer Menú (New Selection)

Appears when the user makes a **new text selection** (long-press + drag).

**Actions:**
- **Palette** (YELLOW by default) — opens color picker to choose a highlight color
- **Copy** — copies the selected text to clipboard
- **Dictionary** — opens the definition input to add the word to the personal dictionary
- **Share** — shares the selected text via system share sheet
- **Annotate** — creates a YELLOW highlight and opens the note modal

**Component:** `TextSelectionMenu`

## Segundo Menú (Existing Highlight)

Appears when the user **taps an existing highlight** (a previously highlighted text span).

**Actions:**
- **Palette** (shows the highlight's actual color) — opens color picker to change it
- **Copy** — copies the highlight text to clipboard
- **Tag** — opens the tag input to categorize the highlight
- **Annotate** — opens the note modal for this highlight (pre-fills existing note)
- **Share** — shares the highlight text
- **Delete** — soft-deletes the highlight (shown in error color)

**Component:** `FloatingContextMenu`

## Flow Diagram

```
 Select text        Pick color           Tap highlight
┌──────────┐      ┌────────────┐        ┌──────────────┐
│  NEW     │ ───→ │ Highlight  │ ─────→ │  EXISTING    │
│ selection│       │ created    │         │  highlight   │
│ menu     │       │ menu closes│         │  menu        │
└──────────┘       └────────────┘        └──────────────┘
```

## Implementation Notes

- The decision between menus is driven by `ReaderSelectionState`:
  - `New` → `TextSelectionMenu`
  - `Existing` → `FloatingContextMenu`
- State is managed by `SelectionCoordinator` (internal state machine in `ReaderInteractionStateHolder`)
- The debounce window (2000ms) after highlight tap prevents the polling loop from overwriting the Existing state
- The ignore window (1500ms) after menu close prevents stale selection events

## Edge Cases & Fixes

### Decoration listener firing during new selection

Readium's `onDecorationActivated` can fire when the user long-presses on text that overlaps with an existing highlight decoration. This would set the state to `Existing` before the polling loop can set it to `New`.

**Fix:** The `matchingHighlight` text match in `onReadiumSelection()` only runs when the `SelectionCoordinator` is in `ExistingHighlight` state with an expired debounce. When the coordinator is `Idle` or `NewSelection`, it always goes to `New` — no text matching is performed. This prevents the polling loop from re-establishing an `Existing` state for fresh selections.

### `activeHighlightId` always null in ViewModel merge

After the refactor (commit `a46c814`), `ReaderInteractionState` no longer contains `activeHighlightId` — it lives in `SelectionCoordinator`. The ViewModel merge was pinning `uiState.activeHighlightId = null` every emission, which:
- Broke the highlight-matching fallback in `onReadiumSelection()` (the parameter was always null)
- Made `activeOverlayHighlightColor` always null, so `FloatingContextMenu` always showed YELLOW instead of the highlight's real color

**Fix:** `onReadiumSelection()` reads `activeHighlightId` directly from the internal `SelectionCoordinator`, bypassing the ViewModel state. The merge no longer pins the field.
