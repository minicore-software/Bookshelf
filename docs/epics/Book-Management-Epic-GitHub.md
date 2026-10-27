# Book Management

**Type:** Epic (tracking issue) · **Issue:** [#29](https://github.com/minicore-software/Bookshelf/issues/29) · **Repo:** [minicore-software/bookshelf](https://github.com/minicore-software/bookshelf) · **Team:** minicore-software
**Status:** Refined into 6 child issues: [#30](https://github.com/minicore-software/Bookshelf/issues/30) [#31](https://github.com/minicore-software/Bookshelf/issues/31) [#32](https://github.com/minicore-software/Bookshelf/issues/32) [#33](https://github.com/minicore-software/Bookshelf/issues/33) [#34](https://github.com/minicore-software/Bookshelf/issues/34) [#35](https://github.com/minicore-software/Bookshelf/issues/35)
**Source:** brainstorming doc `Book-Management-Epic.md` (decisions D1–D20)

---

## Summary

Give Bookshelf full CRUD on the `Book` entity, via four screens reached from the Library landing screen: Add, Edit, View, and Delete. This is the work `Library Screen — Home Screen` (#17) deliberately deferred. It lets the user keep their shelf accurate and personal — add books by hand, correct details, record private reading memories, and remove books they no longer own.

This epic:
1. **Data foundation** — Room migration adding `comment`, 3-part memory (why/where/what), and cover-color fields to `Book`.
2. **Add book** — FAB "+" opens a manual-entry modal form.
3. **View book** — tapping a Library card opens a read-only detail modal.
4. **Edit book** — pencil icon on View opens the same form as Add, pre-filled, plus Memory and a read-only lending indicator.
5. **Delete book** — long-press or an overflow menu on a Library card, with a shared confirm dialog, cascades the book's loan/reading-log history.

Scan/search-to-add (the existing `project/AddBook.dc.html` flow) and reading-progress tracking (the existing `project/BookDetail.dc.html` flow) are explicitly out of scope — this epic replaces those two canvas screens with the new wireframes.

## Background

- **Current state:** `Library Screen` epic (#17, closed) built the Library landing screen read-only — card taps, the FAB, and "See all" are stubs. It deliberately deferred Add/Edit/View Book Detail to "a future epic" (this one), and already recorded the field list this epic now implements (title, author, status, comment, 3-part memory, rating).
- **`CLAUDE.md` domain model:** `Book` has no `comment`, memory, or cover-color fields today — this epic's first ticket adds them via Room migration. `Loan` already exists (bookId, borrowerName, borrowerContact?, loanedAt, dueAt, returnedAt?, remindEnabled, notes?), at most one open loan (`returnedAt == null`) per book — this epic reads it but never writes it.
- **Canvas superseded:** `project/AddBook.dc.html` (scan/search flow) and `project/BookDetail.dc.html` (progress-bar hero) predate the new wireframes and describe a different flow; they're replaced for this epic's scope and should be updated to match once these screens ship (not itself a ticket here).
- **New wireframes** (`project/wireframe_images/`): `Add_New_Book_Screen.png`, `Edit_Book_Screen_Part_1.png`, `Edit_Book_Screen_Part_2.png`, `Show_Book_Screen.png`.

## Flow

```
Library screen (from #17)
 ├── FAB (+) ───────────────► Add book modal ──"Add to shelf"──► Book persisted (status=want_to_read)
 │                                                                         │
 ├── Card tap ──────────────► View book modal (read-only)                 │
 │                               ├── status dot: on-the-shelf / lent-out  │
 │                               ├── "Lend out" button — stub, no-op      │
 │                               ├── Edit (pencil) ───────────────────────┤
 │                               └── Close                                │
 │                                                                        ▼
 │                            Edit book modal (same fields as Add, pre-filled)
 │                               ├── + Memory (why/where/what, each optional)
 │                               ├── + "Currently lent out" — read-only, open-Loan reflection
 │                               ├── "Remove book" ──────┐
 │                               └── "Save changes"      │
 │                                                        ▼
 └── Long-press / overflow "⋮" ──► Delete confirm dialog (shared with "Remove book")
                                        ├── warns if book has an open loan
                                        └── confirm ──► cascade-delete Book + its Loan + ReadingLogEntry rows
```

## Scope

### 1. Data foundation
- Room migration adding to `Book`: `comment: String?`, `memoryWhy: String?`, `memoryWhere: String?`, `memoryWhat: String?`, and a single discriminated cover field (`coverUri: String?` XOR `coverColor: String?` — never both set).
- Existing rows backfill with nulls for the new text fields; cover defaults to a deterministic preset color where neither `coverUri` nor `coverColor` exists today.
- DAO/repository updates to read/write the new fields.

### 2. Add book
- FAB "+" opens a modal form: Title (required — enables "Add to shelf" once non-empty), Author (optional), Year (optional, numeric stepper), Rating (5-star tap, defaults unset/0 — rendered as empty outline stars), Short comment (optional textarea), Cover (upload image OR pick 1 of 6 preset colors — mutually exclusive, single field).
- New books always save with `status = want_to_read`; no status picker on this form.
- "Add to shelf" persists the book and returns to Library; "Cancel" discards.
- FAB is not hard-coded exclusively to this form — leave room for a future epic to offer scan/search as an alternate entry.

### 3. View book
- Tapping a Library card opens a read-only modal: cover, title/author/year, star rating (empty-star state for unrated), a 2-state status dot ("on the shelf" / "lent out" — derived from an open `Loan` join, same pattern as #17's D10), quoted comment, a "Lend it to a friend?" row with a "Lend out" button, and a Memory section (Why/Where/Feeling, or "— not yet written —" placeholders per field).
- "Lend out" is a stub/no-op this epic — no `Loan` is created here.
- "Edit" (pencil icon) opens Edit book, pre-filled. "Close" returns to Library.

### 4. Edit book
- Same form as Add, pre-filled with the book's current values, plus:
  - **Memory** section (optional, 3 independently-optional free-text fields: Why did I read this? / Where was I? / What was I going through?).
  - **Borrowing** section: a read-only "This book is currently lent out" checkbox reflecting an existing open `Loan` — never writes a `Loan` row.
- "Remove book" (destructive, left-aligned) opens the shared delete-confirm dialog (see Delete book).
- "Save changes" persists edits; "Cancel" discards.

### 5. Delete book
- Entry points: long-press a Library card (popover with a "remove" icon, Android uninstall-style), an overflow "⋮" menu item on the card (non-touch/TalkBack-reachable alternative), and "Remove book" on Edit — all three open the **same confirmation dialog**.
- The dialog warns specifically when the book has an open loan, but does not block deletion.
- Confirming cascade-deletes the `Book` row along with its `Loan` and `ReadingLogEntry` history.

## Out of scope

- Scan/search-to-add (`project/AddBook.dc.html`'s flow) — may return as an alternate Add entry in a future epic; this epic doesn't foreclose it but doesn't build it either.
- Reading-progress tracking on Book Detail (`project/BookDetail.dc.html`'s progress bar / freeform notes list) — separate future epic.
- Any `Loan`-writing behavior — "Lend out" (View) and the lent-status checkbox (Edit) are both read-only/stub this epic; the Lend Book epic owns writing `Loan` rows.
- Updating `project/AddBook.dc.html` / `project/BookDetail.dc.html` canvas files to match the new screens — a housekeeping follow-up, not a ticket here.

## Dependencies

| Dependency | Owner |
|------------|-------|
| Lend Book epic/screen — destination for View's "Lend out" and Edit's lent-status checkbox, once they stop being stubs | TBD |

## To settle at refinement

- Exact confirm-dialog copy for delete (especially the open-loan warning wording).
- Preset cover-color palette values (6 colors) — visible in the wireframe's swatches, not yet written down as design tokens.
- Whether Title-only validation needs a max-length cap, and whether Year accepts a bounded range (e.g., no future years).

## Tickets

**Order: Schema → Add → View → Edit → Remove (via Edit) → Delete (from grid).** Refined by PO breakdown from the original 5-candidate draft: "Delete book" was split into two (#34, #35) to resolve a forward dependency — Edit (#33) references a "Remove book" button, but the confirm dialog it opens didn't exist yet in the original sequencing. #34 builds that dialog via Edit's button; #35 adds the Library-grid entry points (long-press + overflow) reusing #34's dialog verbatim.

Each ticket carries its own tests as part of its acceptance criteria — no separate trailing test ticket. Full ticket bodies live on GitHub; see `Book-Management-Epic-Tickets.md` for a local copy.

| Area | Issue | Depends on |
|------|-------|------------|
| Data | [#30](https://github.com/minicore-software/Bookshelf/issues/30) Extend `Book` schema — comment, memory, cover-color fields | — |
| Add | [#31](https://github.com/minicore-software/Bookshelf/issues/31) Add a book manually | #30 |
| View | [#32](https://github.com/minicore-software/Bookshelf/issues/32) View a book's details | #30, #31 |
| Edit | [#33](https://github.com/minicore-software/Bookshelf/issues/33) Edit a book's details | #30, #31, #32 |
| Remove | [#34](https://github.com/minicore-software/Bookshelf/issues/34) Remove a book (confirm + cascade), via Edit's button | #30, #33 |
| Delete | [#35](https://github.com/minicore-software/Bookshelf/issues/35) Delete a book from the Library grid (long-press + overflow), reuses #34's dialog | #34 |

## References

- Wireframes: `project/wireframe_images/Add_New_Book_Screen.png`, `Edit_Book_Screen_Part_1.png`, `Edit_Book_Screen_Part_2.png`, `Show_Book_Screen.png`
- Superseded canvas: `project/AddBook.dc.html`, `project/BookDetail.dc.html`
- Brainstorm doc: `Book-Management-Epic.md` (D1–D20)
