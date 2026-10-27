# Book Management — Tickets

Epic: [#29](https://github.com/minicore-software/Bookshelf/issues/29). Refined by PO breakdown from the epic's 5-candidate draft — "Delete book" split into #34 (confirm + cascade, via Edit) and #35 (grid entry points) to fix a forward dependency. All 6 created and open.

| # | Issue | Type | Status | Depends on |
|---|-------|------|--------|------------|
| 1 | [#30](https://github.com/minicore-software/Bookshelf/issues/30) Extend `Book` schema — comment, memory, cover-color fields | Task | Open | — |
| 2 | [#31](https://github.com/minicore-software/Bookshelf/issues/31) Add a book manually | Story | Open | #30 |
| 3 | [#32](https://github.com/minicore-software/Bookshelf/issues/32) View a book's details | Story | Open | #30, #31 |
| 4 | [#33](https://github.com/minicore-software/Bookshelf/issues/33) Edit a book's details | Story | Open | #30, #31, #32 |
| 5 | [#34](https://github.com/minicore-software/Bookshelf/issues/34) Remove a book (confirm + cascade) | Story | Open | #30, #33 |
| 6 | [#35](https://github.com/minicore-software/Bookshelf/issues/35) Delete a book from the Library grid | Story | Open | #34 |

## Deferred to refinement (not blockers)

- **Delete confirm-dialog copy** (incl. open-loan warning wording) — left out of AC on purpose; whoever picks up #34 drafts it.
- **Preset cover-color palette** (6 hex values) — pull from `Add_New_Book_Screen.png` wireframe swatches at implementation time (#31).
- **Title max-length / Year bounded range** — no caps added; Room columns don't need them architecturally, add only if something breaks in practice.

## Notes

- #17 (Library Screen, closed) required no changes — it already deferred this exact scope to "a future epic."
- Full ticket bodies (value, scope, AC) are on GitHub; this file tracks keys/status/dependencies only — see `Book-Management-Epic-GitHub.md` for the epic-level summary and `Book-Management-Epic.md` for the underlying decisions (D1–D20).
