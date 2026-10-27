# Epic (DRAFT): Book Management

| Field   | Value |
|---------|-------|
| Tracker | GitHub Issues + Projects v2 — minicore-software/bookshelf — [issue #29](https://github.com/minicore-software/Bookshelf/issues/29) |
| Team    | minicore-software (solo, TBD) |
| Repo    | minicore-software/bookshelf |
| Owner   | TBD |
| Status  | Written to GitHub — [issue #29](https://github.com/minicore-software/Bookshelf/issues/29) |
| Updated | 2026-10-26 |

> This draft contains only what is confirmed by the user or found in the repo.
> Anything not confirmed is listed under **Open Questions** rather than assumed.
> Work proceeds part by part. This repo uses GitHub Issues/Projects, not Jira —
> "epic" here means a tracking issue with child task issues, not a Jira epic.

---

## 1. Summary

Give Bookshelf full CRUD on the `Book` entity, via four screens reached from the
Library landing screen: **Add book** (FAB "+"), **Edit book** (pencil icon on
Book Detail), **View book** (tap a card — read-only), **Delete book** (long-press
a card → popover with remove icon). This is the epic `Library-Screen-Epic`
(#17) deliberately deferred (its D1/D3/D8).

Parts:
1. Add book
2. Edit book
3. View book
4. Delete book

## 2. Decisions (confirmed by product owner)

| # | Decision |
|---|----------|
| D1 | **Epic name: "Book Management."** (user) |
| D2 | **New wireframes supersede the existing `project/AddBook.dc.html` (scan/search flow) and `project/BookDetail.dc.html` (progress-bar hero) canvas screens** for this epic's scope. Scan/search-to-add becomes a separate future epic; canvas files get updated to match these screens out of band from ticket creation — not itself a ticket in this breakdown. (user) |
| D3 | **Add book:** FAB "+" (bottom-right, Library screen) opens modal "New book" form — Title, Author, Year (numeric stepper), Rating (5-star tap), Short comment (textarea), Cover (upload image OR pick 1 of 6 preset solid colors). Actions: Cancel / "Add to shelf". (wireframe: `Add_New_Book_Screen.png`) |
| D4 | **Edit book:** pencil icon, top-right of Book Detail screen, opens same form as Add, pre-filled, plus optional "Memory" section (Why did I read this? / Where was I? / What was I going through? — 3 free-text fields) and optional "Borrowing" section (checkbox: "This book is currently lent out"). Actions: "Remove book" (destructive, left-aligned) / Cancel / "Save changes". (wireframe: `Edit_Book_Screen_Part_1.png`, `Edit_Book_Screen_Part_2.png`) |
| D5 | **View book:** tapping a card on the Library landing screen opens read-only "Book" modal — cover thumbnail, title/author/year, star rating, status dot ("ON THE SHELF" / "LENT OUT" presumably), quoted comment, "Lend it to a friend?" row with "Lend out" button, Memory section (Why/Where/Feeling, or "— not yet written —" placeholders). Actions: Close / Edit (→ Part 2). (wireframe: `Show_Book_Screen.png`) |
| D6 | **Delete book:** long-press a card on the Library landing screen shows a small popover near the card with a "remove" icon button (Android uninstall-style pattern). (user) |
| D7 | **Add book: only Title is required.** Author/Year optional, fillable later via Edit. "Add to shelf" enables once Title is non-empty. (Q1.1) |
| D8 | **Rating defaults to unset (0/null), rendered as 5 empty outline stars** — visually distinct from a genuine 1-star rating. Same empty-state convention applies on View. (Q1.2) |
| D9 | **Cover image and cover color are mutually exclusive, one discriminated field.** Picking one clears the other (`coverUri` OR `coverColor`, never both). (Q1.3) |
| D10 | **New books always start at `Book.status = want_to_read`.** No status picker on the Add form, matching the wireframe. (Q1.4) |
| D11 | **Room schema migration (comment, memory×3, cover-color fields) is its own enabling ticket, ordered first** — both Add and Edit depend on it. (Q1.5) |
| D12 | **Manual-entry Add does not foreclose scan/search returning later.** FAB isn't hard-coded to only open the manual sheet — a future epic may add scan/search back as an alternate entry (branch/choice), so Add book tickets should leave that door open. (Q1.6) |
| D13 | **"Currently lent out" checkbox on Edit is read-only**, reflecting an existing open `Loan` (`returnedAt == null`). Actual lending happens only through the Lend Book flow — this epic doesn't write `Loan` rows. (Q2.1) |
| D14 | **"Remove book" (Edit) and the long-press delete popover (Part 4) share one confirmation dialog** — same copy/behavior from both entry points. (Q2.2) |
| D15 | **Memory fields (Why/Where/What) are independently optional** — each can be filled or left blank on its own, matching the wireframe's per-field placeholders. (Q2.3) |
| D16 | **View's status dot is simplified to 2 states: "on the shelf" / "lent out"** — does not surface `reading`/`finished`/`want_to_read` distinctly on this screen. (Q3.1) |
| D17 | **"Lend out" button on View is a stub/no-op this epic.** No real Lend Book screen wired — this epic is Book CRUD only, consistent with D13. (Q3.2) |
| D18 | **Popover "remove" icon opens a second confirm step** before actual delete — not an immediate single-tap delete. Shares the dialog established in D14. (Q4.1) |
| D19 | **Delete cascade-deletes the book's `Loan` and `ReadingLogEntry` history.** The confirm dialog (D14/D18) specifically warns when an open loan exists, but doesn't block the delete. (Q4.2) |
| D20 | **Delete also gets a non-long-press entry point: an overflow "⋮" menu item on the card**, alongside long-press — avoids a touch-only destructive gesture given #21's recent TalkBack fix on these cards. (Q4.3) |

## 3. Background (what exists today)

**`CLAUDE.md` domain model:**
- `Book` — id, title, author, isbn?, coverUri?, genre?, pageCount?, publishedYear?, status (want_to_read/reading/finished), rating?, synopsis?, createdAt. **No comment/note field, no 3-part memory field, no cover-color field** — three gaps the wireframes require; a Room schema migration is needed somewhere in this epic.
- `Loan` — bookId, borrowerName, borrowerContact?, loanedAt, dueAt, returnedAt?, remindEnabled, notes?. At most one open loan (`returnedAt == null`) per book. View's "Lend out" button and Edit's "currently lent out" checkbox both touch this without a stated behavior yet.
- `ReadingLogEntry` exists too — relevant to delete's cascade question (Part 4).

**`docs/epics/Library-Screen-Epic.md`** (GitHub issue #17, closed/implemented):
- D1: explicitly scoped OUT Book Detail/Add Book/Edit Book screens — "future epics." This is that epic.
- D3: recorded the same future-field list (title, author(s), status, comment, 3-part memory, rating) we're now seeing in the wireframes — consistent, not a surprise.
- D8: flagged the 6 wireframe images (including these 4) as reference material for this future epic.

**Canvas (superseded per D2):**
- `project/AddBook.dc.html` — scan-barcode card, recent searches, title/author/ISBN search with results list, "Can't find it? Add manually" link.
- `project/BookDetail.dc.html` — full-screen hero, reading-progress bar + %, metadata chips, synopsis, freeform notes list, bottom actions (bookmark / lend / update progress).

**Related epics / tickets (context only):**
- #17 — Library Screen — Home Screen (closed) — produced the Library landing screen this epic's Add/View/Delete entry points live on.
- No existing Book Management epic — no network access to search GitHub issues this session; assumed none per #17's own "no existing epic found" note and repo's small issue history.

**PO review (checkpoint 1) — folded into Part batches below, flagged "(PO)".**

## 4. Part 1 — Add book

**Goal (inferred, confirm with user):** Let the user add a book to their shelf via a manual form, without requiring scan/search (deferred to a future epic). *(PO: unstated why manual-entry supersedes scan/search for this epic — worth one sentence in the epic so it doesn't read as a quiet regression.)*

**Confirmed:** D1–D4.

### Working flow

```
Library screen (FAB "+")
        │
        ▼
"New book" modal (D3)
 ├── Title, Author, Year            — required? optional? Q1.1
 ├── Rating (5-star tap)            — default/unset state: Q1.2
 ├── Short comment (textarea)       — optional, length cap?: Q1.3
 ├── Cover: upload image OR 1 of 6 colors — mutually exclusive?: Q1.4
 ├── "Add to shelf" → persists Book — initial status?: Q1.5
 └── Needs Room migration first (comment/memory/cover-color fields) — Q1.6
```

### Answered — Part 1

| # | Question | Answer |
|---|----------|--------|
| ~~Q1.1~~ | Required fields to save? | Title only → D7 |
| ~~Q1.2~~ | Rating default state? | Unset/0, empty stars → D8 |
| ~~Q1.3~~ | Cover image vs color mutually exclusive? | Yes, single discriminated field → D9 |
| ~~Q1.4~~ | Initial `Book.status`? | Always `want_to_read` → D10 |
| ~~Q1.5~~ | Migration as own enabling ticket? | Yes, ordered first → D11 |
| ~~Q1.6~~ | Manual-entry final, or FAB may branch later? | Door left open for future scan/search → D12 |

**Part 1: closed.**

## 5. Part 2 — Edit book

**Goal:** Let the user correct/update a book's data and optional personal context (Memory), and see lending status. *(PO: Edit's "currently lent out" checkbox touches `Loan` with no stated write behavior — see Q2.1.)*

**Confirmed:** D1, D2, D4.

### Working flow

```
Book Detail (pencil icon) → "Edit book" modal (D4, same fields as Add + Memory + Borrowing)
 ├── Memory section (optional, 3 free-text fields) — new Book fields, needs migration (Q1.5)
 ├── Borrowing checkbox "currently lent out" — read-only reflection of open Loan, or writable?: Q2.1
 ├── "Remove book" (destructive) — same confirm flow as Part 4 delete?: Q2.2
 └── "Save changes" → persists edits
```

### Answered — Part 2

| # | Question | Answer |
|---|----------|--------|
| ~~Q2.1~~ | Lent checkbox writable or read-only? | Read-only, reflects open Loan → D13 |
| ~~Q2.2~~ | Shared delete confirm flow with Part 4? | Yes, one shared dialog → D14 |
| ~~Q2.3~~ | Memory fields independently optional? | Yes → D15 |

**Part 2: closed.**

## 6. Part 3 — View book

**Goal:** Read-only book detail, reachable from a Library card tap, with an entry point into Edit and into Lend Book.

**Confirmed:** D1, D2, D5.

### Working flow

```
Library card tap → "Book" modal (D5)
 ├── Status dot: "ON THE SHELF" / "LENT OUT" / other? — Q3.1
 ├── "Lend out" button → where does it route?: Q3.2
 ├── Memory section with "— not yet written —" placeholders — read from Edit's Memory fields
 └── Close / Edit (→ Part 2)
```

### Answered — Part 3

| # | Question | Answer |
|---|----------|--------|
| ~~Q3.1~~ | All status-dot states, or simplified? | Simplified: on-the-shelf / lent-out only → D16 |
| ~~Q3.2~~ | "Lend out" real route or stub? | Stub/no-op this epic → D17 |

**Part 3: closed.**

## 7. Part 4 — Delete book

**Goal:** Let the user remove a book from their shelf, with a safe, accessible confirmation path.

**Confirmed:** D1, D2, D6.

### Working flow

```
Library card long-press → popover (D6, remove icon)
 ├── Confirm step before actual delete? — Q4.1
 ├── Cascade: what happens to this book's Loan/ReadingLogEntry history? — Q4.2
 └── Non-touch/TalkBack path to delete (today: long-press only) — Q4.3
```

### Answered — Part 4

| # | Question | Answer |
|---|----------|--------|
| ~~Q4.1~~ | Immediate delete or confirm step? | Second confirm step → D18 |
| ~~Q4.2~~ | Cascade history, block, or orphan on delete? | Cascade-delete, warn on open loan → D19 |
| ~~Q4.3~~ | Non-touch delete path? | Add overflow "⋮" menu item too → D20 |

**Part 4: closed.**

**All parts closed.**

## N. Dependencies

| Dependency | Owner | Source |
|------------|-------|--------|
| Room schema migration (comment, memory×3, cover-color fields on `Book`) | TBD | Q1.5 |
| Lend Book epic/screen (destination for View's "Lend out" and Edit's lent-status checkbox) | TBD | Q2.1, Q3.2 |
| `project/AddBook.dc.html` / `project/BookDetail.dc.html` canvas updates to match new screens | TBD | D2 |
