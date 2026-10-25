# Epic (DRAFT): Library Screen — Home Screen

| Field   | Value |
|---------|-------|
| Tracker | GitHub Issues + Projects v2 — [minicore-software/bookshelf](https://github.com/minicore-software/bookshelf) — not created yet |
| Team    | minicore-software (solo, TBD) |
| Repo    | minicore-software/bookshelf |
| Owner   | TBD |
| Status  | Written to GitHub — [issue #17](https://github.com/minicore-software/Bookshelf/issues/17), refined into 5 child issues: [#18](https://github.com/minicore-software/Bookshelf/issues/18) [#19](https://github.com/minicore-software/Bookshelf/issues/19) [#20](https://github.com/minicore-software/Bookshelf/issues/20) [#21](https://github.com/minicore-software/Bookshelf/issues/21) [#22](https://github.com/minicore-software/Bookshelf/issues/22) |
| Updated | 2026-10-25 |

> This draft contains only what is confirmed by the user or found in the repo.
> Anything not confirmed is listed under **Open Questions** rather than assumed.
> Work proceeds part by part. This repo uses GitHub Issues/Projects, not Jira —
> "epic" here means a tracking issue with child task issues, not a Jira epic.

---

## 1. Summary

Build the real Library screen (the app's home screen) — replacing the Bootstrap
epic's blank "Bookshelf" placeholder — showing currently-reading books, lent-out
books, and the full library grid with filter chips, largely reusing the existing
`project/Main.dc.html` design. The header changes: no user-avatar icon, and the
"Good evening / Marco's Shelf" greeting is replaced by a wordmark-style title
(like the splash/launcher branding) with just the search bar below it.

Parts (tentative, refined as brainstorm proceeds):
1. Scope & data foundation (read vs. write path, Book/Loan modeling, Room wiring)
2. Header redesign (title wordmark, removed avatar, search bar only)
3. Currently-reading & lent-out rows
4. Library grid + filter chips
5. Empty state

## 2. Decisions (confirmed by product owner)

| # | Decision |
|---|----------|
| D1 | **Scope is the Library landing screen only.** Book Detail, Add Book, Edit Book, Lend Book screens are explicitly NOT built in this epic — future epics. (user) |
| D2 | **Font family is settled:** Fraunces (serif, titles) + Inter (sans, body) — already documented in `CLAUDE.md`'s Design Reference. No open question here. (user) |
| D3 | **Future Book Detail fields (recorded for data-model awareness only, not built this epic):** Title, Author(s) (comma-separated, no parsing), status (On the shelf / Lent), Comment/Note, Memory (3 parts: Why / Where / Feel), 5-star rating. (user) |
| D4 | **Reuse `project/Main.dc.html` as the design baseline**, mostly as-is — layout, currently-reading row, lent-out row, filter chips, library grid, FAB, bottom nav. (user) |
| D5 | **Remove the circular user-initials avatar** from the top-right of the header. (user) |
| D6 | **Replace the "Good evening / Marco's Shelf" greeting** with a wordmark/title treatment resembling the splash/launcher branding — referencing `project/wireframe_images/Landing_Screen_Sample_Title.png` as the shape to aim for, adapted to feel like a compact header lockup rather than a full splash screen. (user) |
| D7 | **Below the title: only the search bar remains** — no greeting text of any kind. (user) |
| D8 | **6 wireframe reference images** (`Add_New_Book_Screen`, `Book_Card_Element`, `Edit_Book_Screen_Part_1/2`, `Lent_Book_Card_Element`, `Show_Book_Screen`) are reference material for a **different, future epic** (add/edit/detail screens) — not built here, only confirm the same data fields as D3. (user) |
| D9 | **Read-only this epic.** No Add Book flow — FAB (+) and search bar route to a stub/disabled state, not a real screen. (Q1.1) |
| D10 | **"Lent" is an overlay, not a status.** Computed by joining `Book` with an open `Loan` (`returnedAt == null`) — `Book.status` stays `want_to_read/reading/finished`; "lent" is a derived flag layered on top for the filter chip and grid status dot. (Q1.2) |
| D11 | **Currently-reading progress is deferred.** No % / page progress this epic — needs `ReadingLogEntry` from the not-yet-built progress feature. (Q1.3) |
| D12 | **This epic owns the `Book`/`Loan` Room schema**, DAOs, and repository wiring — first screen to touch real persistence. (Q1.4) |
| D13 | **Empty-state Library screen gets a real design pass in this epic** (0 books/reading/lent — the actual first-run state). (Q1.5) |
| D14 | **Header wordmark built exactly as `Landing_Screen_Sample_Title.png` shows** — resize/recolor for a compact header, no further design iteration. (Q1.6) |
| D15 | **No other changes to `Main.dc.html`** beyond avatar removal (D5) and greeting swap (D6/D7) — filter chips, grid, lent-card opacity, FAB, bottom nav built exactly as mocked. (Q1.7) |
| D16 | **"Currently reading" row shows cover + title only**, no progress bar/% this epic (consistent with D11). (Q2.1) |
| D17 | **"Lent out" row keeps both the Due/Overdue caption and the borrower name**, exactly as mocked — `Loan.dueAt` doesn't depend on the deferred progress data. (Q2.2) |
| D18 | **Tapping any book card (currently-reading, lent-out, grid) is a stub/no-op** — no Book Detail screen exists yet. (Q2.3) |
| D19 | **"See all" link on the Lent-out row stays visible**, same stub/no-op treatment as card taps (not hidden). (Q2.4) |

## 3. Background (what exists today)

**`project/Main.dc.html`** (Design canvas, board title "Library"):
- Header: greeting text + "Marco's Shelf" title (Fraunces) + avatar circle, search bar (currently links to `AddBook.dc.html` — a stand-in "search or add" entry point, not a wired search feature).
- "Currently reading" horizontal row: cover + title overlay, progress bar + "64% · p.212 of 331" caption — this data comes from `ReadingLogEntry`, not `Book` directly.
- "Lent out" horizontal row: cover at reduced opacity, borrower name, due/overdue caption (color-coded).
- Filter chips: All · Reading · Finished · Want to read · Lent out, each with a count.
- "Your library" 3-column grid: cover + title + colored status dot; lent books also show "Lent to X" under the title.
- FAB (+) → `AddBook.dc.html`. Bottom nav: Library / Discover / Stats / Settings.

**`CLAUDE.md` domain model:**
- `Book.status` is `want_to_read | reading | finished` — **does not include "lent."** "Lent" comes from a separate `Loan` row with `returnedAt == null` (open loan), joined by `bookId`.
- `Book` has **no comment/note field and no 3-part memory (why/where/feel) field** — a gap against D3; not this epic's problem to fix, but flagged for whoever builds the Book Detail/Edit epic (likely a later migration).
- `features/library/{ui,domain,data}` packages already scaffolded empty (App Bootstrap epic); Hilt, Room, Navigation Compose already wired but unused.

**Related epics / tickets (context only):**
- #6 — App Bootstrap — Runnable Shell, Icon & Splash Screen (closed/implemented) — produced the blank placeholder this epic replaces as the nav start destination.
- #9 — Implement two-stage splash screen from `splash-screen.svg` (open).
- #10 — Blank landing screen titled "Bookshelf" (open).
- No existing Library epic — searched GitHub issues, none found.

**Repo check:** `resources/` only has `icon.svg` and `splash-screen.svg` — no separate title-lockup asset yet. The only reference for D6's wordmark is `project/wireframe_images/Landing_Screen_Sample_Title.png`.

## 4. Part 1 — Scope & data foundation

**Goal (inferred, confirm with user):** Replace the Bootstrap placeholder with a real, data-backed Library home screen reading actual `Book`/`Loan` rows from Room, so the app has its first genuinely useful screen and a persistence layer other features build on. *(Not stated outright by the user — inferred from "first real feature" framing and "first screen to read/write real Book data via Room." Flagging as inferred rather than assumed.)*

**Confirmed:** D1, D4.

### Working flow (what's undecided shapes this)

```
Bootstrap placeholder (closed, #10)
        │
        ▼
Library screen (this epic)
 ├── reads Book rows (status: want_to_read/reading/finished) ── Room, scope: Q1.4
 ├── reads open Loan rows (returnedAt == null) for "lent" overlay ── modeling: Q1.2
 ├── reads ReadingLogEntry for "currently reading" progress % ── in scope?: Q1.3
 ├── write path? (FAB "+", search bar tap target) ── Q1.1
 └── empty state (0 books) ── Q1.5
```

### Answered — Part 1

| # | Question | Answer |
|---|----------|--------|
| ~~Q1.1~~ | Write path this epic, or read-only? | Read-only; FAB/search stub → D9 |
| ~~Q1.2~~ | "Lent" overlay or separate model? | Overlay on open Loan → D10 |
| ~~Q1.3~~ | Progress bar in scope? | Deferred, covers only → D11 |
| ~~Q1.4~~ | This epic own Book/Loan schema? | Yes → D12 |
| ~~Q1.5~~ | Empty state spec'd this epic? | Yes → D13 |
| ~~Q1.6~~ | Wordmark exact or rough direction? | Exact, as shown → D14 |
| ~~Q1.7~~ | Other changes beyond avatar/greeting? | None → D15 |

**Part 1: closed.**

## 5. Part 2 — Interaction surfaces without a destination screen

**Goal:** D9 (read-only) and D1 (no Book Detail/Add Book/My Loans this epic) mean most taps in the mock lead to screens that don't exist yet. Needs a consistent stub treatment.

**Confirmed:** D1, D9.

### Answered — Part 2

| # | Question | Answer |
|---|----------|--------|
| ~~Q2.1~~ | Currently-reading row: cover+title only, or hide? | Cover+title only, no progress bar → D16 |
| ~~Q2.2~~ | Keep Due/Overdue caption, or simplify? | Keep both due/overdue caption AND borrower name (as mocked) → D17 |
| ~~Q2.3~~ | Card tap with no Book Detail screen — what happens? | Stub/no-op → D18 |
| ~~Q2.4~~ | "See all" link (→ My Loans, not built) — stub or hide? | Same stub/no-op treatment, stays visible → D19 |

**Part 2: closed.**

**All parts closed.**

## N. Dependencies

| Dependency | Owner | Source |
|------------|-------|--------|
| Header wordmark asset — final form TBD pending Q1.6 | user | D6, Q1.6 |
| `Book`/`Loan` Room schema — may be a prerequisite epic pending Q1.4 | TBD | Q1.4 |
