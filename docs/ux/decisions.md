# UX decisions log

Running log of UX decisions and rationale across tickets (delivery-conventions §6). Append dated entries; never split into per-ticket files.

## 2026-10-27 — BOOKSHELF-0031 (Add a book manually)

- **Decision:** a dirty draft (any field changed, or an image picked) shows a "Discard this book?" confirm dialog on Cancel / ✕ / back / scrim tap; a clean draft dismisses immediately with no confirm.
  **Alternatives considered:** always confirm regardless of dirty state (rejected — needless friction on a title-only, barely-started draft); never confirm, rely on `SavedStateHandle` to survive accidental dismissal (rejected — process death/rotation survival doesn't help an *intentional* back-tap that the user didn't mean to be destructive, and an in-flight picked image file still needs explicit cleanup messaging).
  **Reason:** Nielsen heuristic #5 (error prevention) weighed against not punishing a near-empty draft; the state already tracks `isDirty` per the architect's plan (A9), so either answer was buildable — this one matches user expectation for forms with real data entered.
  **Who agreed:** ux-specialist (spec author). Flagged to SA/FE for confirmation that scrim-tap routing through the same manual handler (not `DialogProperties.dismissOnClickOutside`) is the intended wiring (spec §"Assumptions and open questions" item 5) — not blocking.

- **Decision:** post-save confirmation is shown — a Snackbar reading "Added to your shelf" (Visual Spec §5.14 copy, reused verbatim) — even when the active Library filter would hide the newly added book. The Snackbar does not mention the filter, and save does not auto-switch the active filter.
  **Alternatives considered:** no confirmation at all (rejected — sheet-closes-on-save and sheet-closes-on-cancel would then be visually identical, leaving no positive signal that the save succeeded); a filter-aware message or auto-switching the filter to "Want to read" on save (rejected for now — auto-switching silently changes the user's chosen view, a bigger surprise than a momentarily-filtered-out book; flagged to PO as a candidate if real usage shows confusion).
  **Reason:** distinguishes a successful save from a cancel using the mechanism the architect's plan already reserved for this (A12, `savedStateHandle["book_added"]`) — no new plumbing required to ship it.
  **Who agreed:** ux-specialist (spec author), resolving plan A12's "UX spec decides" open point.

- **Decision:** "Remove" on a set cover image reverts to the last preset explicitly chosen in the session, defaulting to Preset 1 if none was chosen.
  **Alternatives considered:** always revert to Preset 1 regardless of prior selection (rejected — throws away a deliberate earlier choice for no reason); remember nothing and leave the cover unset/blank (rejected — plan A10 and the data model require exactly one of image/preset at all times, never neither).
  **Reason:** least-surprise — returns the user to where they were before trying an image, consistent with "Remove" meaning "undo the image, not reset everything."
  **Who agreed:** ux-specialist (spec author), resolving plan §4.2's "the UX spec may override which preset" note.

- **Decision:** the Discard confirm dialog's destructive action renders as a filled `terracotta` button (terracotta fill, light/paper text), not terracotta text on the paper background. Inline error copy (image-import failure, save failure) uses the `overdue`/`error` token, not literal `terracotta`, for its text color.
  **Alternatives considered:** terracotta text-on-paper for both (rejected — measured ~3.0:1, fails WCAG AA 4.5:1 for normal text).
  **Reason:** contrast failure, same exact pattern already documented and fixed in #20 (plan R5: "terracotta on paper is 3.39:1 and fails AA for 10sp text") — this spec applies the established fix direction (fill for buttons, `overdue` token for small error text) rather than reintroducing the same defect.
  **Who agreed:** ux-specialist (spec author), per WCAG 2.2 AA and existing project precedent.
