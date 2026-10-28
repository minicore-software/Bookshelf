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

## 2026-10-28 — BOOKSHELF-0035 (Delete a book from the Library grid)

- **Decision:** the card actions popover's "Remove book" item uses `MaterialTheme.colorScheme.error` (the `overdue` token, `#9B2C2C`) for its text and leading trash glyph, not literal `terracotta`, even though `Bookshelf-Visual-Spec.md` §7.3 specifies terracotta text/icon for its own destructive-item precedent ("Sign out").
  **Alternatives considered:** literal terracotta text per §7.3's letter (rejected — this is terracotta-on-paper, the exact combination already measured at ~3.0–3.39:1 and documented as an AA failure for normal text on 2026-10-27, and again in #20 plan R5); a filled terracotta pill for the menu item (rejected — out of place inside an otherwise plain M3 `DropdownMenuItem` row, and not what any other menu item in the app looks like).
  **Reason:** applies the same fix direction already established twice (#20, #31) rather than reintroducing the identical defect in a third place; keeps every destructive-text instance in the Library feature on one AA-safe token, matching `RemoveBookDialog`'s own use of `colorScheme.error`.
  **Who agreed:** ux-specialist (spec author), per WCAG 2.2 AA and existing project precedent.

- **Decision:** the grid card's overflow "⋮" sits at the cover's top-start corner as a **dark** translucent disc (`ink` at 55% opacity, matching `BookCover`'s existing lent-dimming opacity) with a light (`paper`) glyph and a thin light ring, not a light disc — mirroring, but visually inverting, `LibraryStatusDot`'s top-end position and ring treatment.
  **Alternatives considered:** a light/paper disc, matching the status dot's own ring colour (rejected — fails WCAG 1.4.11's 3:1 non-text contrast minimum against light preset covers and light user-image covers, the single most likely case); bottom-end placement (rejected — risks visual/physical collision with the Library FAB on the grid's bottom row, flagged as risk K9 in the architect's plan); top-end, next to or merged with the status dot (rejected — the architect's plan explicitly calls out that corner as already occupied and requires a different one).
  **Reason:** WCAG 1.4.11 (non-text contrast) must hold against arbitrary cover art, including near-white and near-black images (ticket AC, plan risk K8); a dark-scrim-plus-light-glyph badge is also the literal pattern the AC cites as the target ("Android uninstall-style").
  **Who agreed:** ux-specialist (spec author), per WCAG 2.2 AA (§1.4.11) and the architect's plan (L3, L9, K8, K9).
