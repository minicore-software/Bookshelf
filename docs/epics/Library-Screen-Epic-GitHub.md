# Library Screen — Home Screen

**Type:** Epic (tracking issue) · **Issue:** [#17](https://github.com/minicore-software/Bookshelf/issues/17) · **Repo:** [minicore-software/bookshelf](https://github.com/minicore-software/bookshelf) · **Board:** [Bookshelf project](https://github.com/users/minicore-software/projects/1) · **Team:** minicore-software
**Source:** brainstorming doc `Library-Screen-Epic.md` (decisions D1–D19)

---

## Summary

Replace the App Bootstrap epic's blank "Bookshelf" placeholder with the real Library screen — the app's home screen, and the first screen backed by real data. It reads actual `Book`/`Loan` rows from Room: currently-reading covers, lent-out books, and the full library grid with filter chips, reusing the existing `project/Main.dc.html` design with a redesigned header.

This epic:
1. **Data foundation** — `Book`/`Loan` Room schema, DAOs, repository wiring (first screen to touch real persistence).
2. **Header** — wordmark title (replacing the greeting + avatar), search bar only.
3. **Currently-reading & lent-out rows** — horizontal scroll rows, covers only (no reading progress yet).
4. **Library grid & filter chips** — the full collection, status-aware.
5. **Empty state** — the real first-run experience (0 books).

Read-only this epic: no Add Book, Book Detail, or My Loans screens are built here — taps on cards and the "See all" link are stubs for now.

## Background

- **Current state:** App Bootstrap epic (closed) produced a blank placeholder screen titled "Bookshelf" as the Navigation Compose start destination; `features/library/{ui,domain,data}` packages are scaffolded but empty; Hilt, Room, and Navigation Compose are wired but unused.
- **`CLAUDE.md` domain model:** `Book.status` is `want_to_read | reading | finished` — there is no "lent" status value. "Lent" comes from a separate `Loan` row with `returnedAt == null` (open loan), joined by `bookId`. `Book` has no comment/memory fields yet — a known gap for the future Book Detail/Edit epic, not this one.
- **Existing building block reused:** `project/Main.dc.html` (Design canvas, "Library" board) — header, currently-reading row, lent-out row, filter chips, library grid, FAB, and bottom nav are all mocked there and reused as-is except for the header.
- **New asset:** `project/wireframe_images/Landing_Screen_Sample_Title.png` — the reference for the new header wordmark, built exactly as shown.
- Palette (`paper` `#F3EEE3`, `ink` `#231F20`, `forest` `#2F4538`, `terracotta` `#C1693C`, `gold` `#C9A227`) and type (Fraunces + Inter) are already settled in `CLAUDE.md`.

## Flow

```
Bootstrap placeholder (closed)
        │
        ▼
Library screen (Navigation Compose start destination)
 ├── Header: wordmark title + search bar (search is a stub — no search feature yet)
 ├── Currently reading (Book.status == reading): cover + title only, no progress
 ├── Lent out (Book ⋈ open Loan): cover (dimmed) + borrower + due/overdue caption
 ├── Filter chips: All · Reading · Finished · Want to read · Lent out (lent = overlay, not a status)
 ├── Library grid: cover + title + status dot (+ "Lent to X" overlay where applicable)
 ├── FAB (+): stub — no Add Book screen yet
 └── Empty state: 0 books/reading/lent — dedicated first-run design
```

## Scope

### 1. Data foundation
- `Book` Room entity/DAO per the `CLAUDE.md` domain model (id, title, author, isbn?, coverUri?, genre?, pageCount?, publishedYear?, status, rating?, synopsis?, createdAt).
- `Loan` Room entity/DAO (id, bookId, borrowerName, borrowerContact?, loanedAt, dueAt, returnedAt?, remindEnabled, notes?) — at most one open loan per book.
- Repository layer exposing: all books (optionally filtered by status), "currently reading" books, books with an open loan (joined with loan due date/borrower), and library counts for the filter chips.
- Hilt wiring for the new DAOs/repository into `features/library/data`.

### 2. Header
- Remove the circular user-avatar icon.
- Replace the "Good evening / Marco's Shelf" greeting with a wordmark title built from `Landing_Screen_Sample_Title.png`, sized/colored for a compact header (not a full splash treatment).
- Only the search bar sits below the title — search itself is a stub (tapping it does nothing functional this epic).

### 3. Currently-reading & lent-out rows
- "Currently reading": horizontal row of books with `status == reading`, cover + title overlay only — no progress bar or page/percent caption (deferred, needs `ReadingLogEntry`).
- "Lent out": horizontal row of books with an open `Loan`, cover at reduced opacity, borrower name, and a due/overdue caption computed from `Loan.dueAt` (color-coded: upcoming vs. overdue).
- "See all" link on the lent-out row stays visible, same stub/no-op treatment as card taps.

### 4. Library grid & filter chips
- Filter chips: All, Reading, Finished, Want to read, Lent out — each with a live count from the repository. "Lent out" filters by open-loan join, not `Book.status`.
- 3-column grid of every book: cover, title, status-colored dot, and "Lent to X" caption for books with an open loan.
- Tapping any card (currently-reading, lent-out, or grid) is a stub/no-op — no Book Detail screen exists yet.

### 5. Empty state
- Fresh-install state (0 books, 0 reading, 0 lent): currently-reading and lent-out rows hidden, grid replaced with an empty-shelf message, filter chips either hidden or shown inert at 0.
- FAB remains visible (still a stub this epic) as the implied next step once Add Book ships.

## Out of scope

- Add Book, Edit Book, Book Detail, Lend a Book, My Loans screens — later epics. All taps that would lead to them are stubs/no-ops this epic.
- Real search functionality — the search bar is present but non-functional.
- Reading progress (%, page count) on the currently-reading row — needs `ReadingLogEntry`, part of the future progress-tracking epic.
- `Book` comment/note and 3-part memory (why/where/feel) fields — not in the current domain model; flagged for the Book Detail/Edit epic's schema migration.

## Dependencies

| Dependency | Owner |
|------------|-------|
| None — all open points were resolved during brainstorming | — |

## To settle at refinement

- Exact status-dot color mapping for want_to_read / reading / finished / lent-overlay in the grid (visible in the mock's colors, not yet written down as a token table).
- Whether the empty-state message/illustration gets its own micro design pass or reuses existing palette/type tokens plainly.

## Candidate tickets

**Order: Data foundation → Header → Rows → Grid & chips → Empty state.** Rows and grid depend on the data foundation ticket's repository queries; header is independent and can run in parallel. Empty state additionally depends on Rows and Grid & chips — it asserts their hidden/inert behavior at 0 books, so it needs those states to exist first, not just the data layer.

Each ticket carries its own tests as part of its acceptance criteria — no separate trailing test ticket.

| Area | Issue | Acceptance criteria |
|------|-------|---------------------|
| Data | [#18](https://github.com/minicore-software/Bookshelf/issues/18) Create `Book`/`Loan` Room schema, DAOs, repository | `Book` and `Loan` Room entities match the `CLAUDE.md` domain model; repository exposes queries for all books, reading-status books, open-loan books (joined with due date/borrower), and per-status counts; unit tests cover the open-loan join (0, 1, and attempted-2nd-open-loan-on-same-book cases). |
| Header | [#19](https://github.com/minicore-software/Bookshelf/issues/19) Library screen header (wordmark + search bar) — closes #10 | Avatar icon removed; wordmark renders at the size/position shown in `Landing_Screen_Sample_Title.png` for the collapsed header height, using Fraunces/palette tokens; search bar renders below with no greeting text; Compose UI test confirms tapping search is a no-op (doesn't crash or navigate). |
| Rows | [#20](https://github.com/minicore-software/Bookshelf/issues/20) Currently-reading & lent-out rows — depends on #18 | Currently-reading row shows covers + titles only for `status == reading` books, no progress UI; lent-out row shows cover (dimmed), borrower name, and due/overdue caption (color-coded) for books with an open loan; "See all" link visible; both rows horizontally scroll. Compose UI tests cover the populated state and confirm card taps and the "See all" tap are no-ops that don't crash. |
| Grid | [#21](https://github.com/minicore-software/Bookshelf/issues/21) Library grid & filter chips — depends on #18 | Filter chips (All/Reading/Finished/Want to read/Lent out) show live counts from the repository; tapping a chip filters the grid by the correct query (lent-out uses the open-loan join, not `Book.status`); grid renders cover + title + status dot + "Lent to X" caption where applicable. Compose UI tests cover chip filtering and confirm card taps are no-ops that don't crash. Status-dot color values TBD from `Main.dc.html`, confirm before implementation. |
| Empty state | [#22](https://github.com/minicore-software/Bookshelf/issues/22) Empty-state Library screen — depends on #20, #21 | With 0 books: currently-reading and lent-out rows are hidden; grid area shows an empty-shelf message instead of an empty grid; filter chips render inert (0 counts) or are hidden; FAB remains visible, and tapping it is a no-op (doesn't crash or navigate, consistent with the other stubs). Compose UI test covers the 0-book state end-to-end. Uses existing tokens, no new illustration. |

## References

- Design canvas: [`project/Main.dc.html`](project/Main.dc.html) ("Library" board)
- Header reference: [`project/wireframe_images/Landing_Screen_Sample_Title.png`](project/wireframe_images/Landing_Screen_Sample_Title.png)
- Brainstorm doc: `Library-Screen-Epic.md` (D1–D19)
