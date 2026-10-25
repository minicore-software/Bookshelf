# Model Book and Loan as Room tables with a trigger-enforced single open loan per book

* **Status:** Accepted
* **Group:** Data
* **Deciders:** software-architect (design delegated by the BOOKSHELF-0018 ticket owner); TBD — confirm in PR review
* **Date:** 2026-10-25

## Context and Problem Statement

Bookshelf has no real schema yet. `BookshelfDatabase` holds only a scaffold placeholder table. The Library screen epic (#17) is the first to read real data, and it needs `Book` and `Loan` tables plus a "lent" overlay derived from an open loan (`returned_at IS NULL`). The domain model says a book has **at most one open loan at a time**. If two open rows ever exist, the overlay join duplicates the book in the grid and the lent-out row. The lending, progress and stats epics all build on this schema, so its identity, time and invariant choices have to be made now, before #20 and #21 consume them. Changing them later costs a migration.

**Assumptions:**

* Single user, local-first, no sync or backend (CLAUDE.md). Backup restores the whole database file, so records are never merged across devices.
* Library size is hundreds to low thousands of books.
* No build has been distributed yet (no tags or releases), so schema version 1 can be replaced in place.
* The app uses Room's default SupportSQLite path (no `setDriver`).

## Decision Drivers

* The single-open-loan invariant must hold on every write path, including the current DAO, the future lending flow, import or restore, and test fixtures. It must not depend on one method remembering to check.
* The mechanism must survive future Room migrations without silent loss.
* The schema stays inside what Room models and validates, so Room's exported schema JSON remains the reviewable truth where possible.
* The persisted model matches the CLAUDE.md domain model, with no extra columns that duplicate state.
* Due and overdue are calendar-day concepts in the design ("Due in 3 days", "Overdue 2 days") and must not shift when the time zone changes.

## Considered Options

**Enforcing one open loan per book:**

* **Option 1: `BEFORE INSERT` and `BEFORE UPDATE` triggers on `loans` that `RAISE(ABORT)` when another open loan exists for the same `book_id`.** They are created with `CREATE TRIGGER IF NOT EXISTS` from a `RoomDatabase.Callback` in `onCreate` and `onOpen`.
  * Pros: enforced by SQLite for every writer. No extra column. The model matches the domain exactly. Re-asserting in `onOpen` restores the triggers if a future table-recreating migration drops them. Room's schema validation does not cover triggers, so they cannot cause a validation failure.
  * Cons: lives outside Room's schema JSON, so a reviewer cannot see it in the schema diff. Every database instance, test DBs included, must be built through the same configuration function. Two triggers are needed (insert and update).
* **Option 2: Repository-level check inside a `@Transaction`** (query for an open loan, insert only if none exists).
  * Pros: plain Kotlin, easy to read, returns a domain error directly.
  * Cons: only protects callers that go through that method. A direct DAO insert, an import or a test fixture bypasses it.
  * Rejected because: it is not an invariant, it is a convention.
* **Option 3: Partial unique index** `CREATE UNIQUE INDEX ... ON loans(book_id) WHERE returned_at IS NULL`, created by hand in a callback.
  * Pros: the idiomatic SQL answer. One statement. A constraint violation is semantically exact.
  * Cons: Room's `@Index` has no partial (`WHERE`) form, so the index sits outside Room's schema model. How Room's migration-time schema validation treats an index it did not declare has not been verified for Room 2.8.5. The risk is a validation failure on the first future migration, discovered on users' devices.
  * Rejected because: it carries an unverified failure mode in exactly the migration path we cannot test until it is too late. It can be revisited if Room adds partial-index support.
* **Option 4: Nullable `open_marker` column (1 when open, NULL when returned) with a Room-declared `UNIQUE(book_id, open_marker)` index**, relying on SQLite treating NULLs as distinct.
  * Pros: fully Room-native, visible in the schema JSON, and migrations carry it automatically.
  * Cons: adds a persisted column that is not in the domain model and must be kept in sync with `returned_at` by every writer. A writer that sets `returned_at` but forgets the marker breaks re-lending.
  * Rejected because: it trades one enforced invariant for a new consistency obligation, the same class of problem as Option 2, moved to a different column.

**Identity:** `Long` autoincrement IDs (chosen; never reused after delete) vs UUID strings (rejected: no sync or merge consumer, larger keys and string joins).

**Time:** `Instant` as epoch millis for `createdAt`, `loanedAt` and `returnedAt`; `LocalDate` as epoch day for `dueAt` (chosen) vs `Instant` for all four (rejected: a due date at local midnight shifts a day across time zones and flips "due today" to "overdue").

**Status storage:** `TEXT` with explicit stable values `want_to_read`, `reading`, `finished` (chosen) vs ordinal `INTEGER` (rejected: reordering the enum silently remaps every row).

## Decision Outcome

**Chosen option:** "Option 1 (triggers)", together with `Long` IDs, `Instant`/`LocalDate` time types, `TEXT` status values and `loans.book_id → books.id ON DELETE CASCADE`. Option 1 is the only option that meets the first two drivers (every writer, survives migrations) without adding duplicated state (driver 4). Its cost, living outside the schema JSON, is contained by a single build function and an instrumented test that asserts the triggers exist. Implementation cost is two SQL statements, one callback and one test class, all within the team's existing Room skills.

### Consequences

* **Good:** the overlay join returns at most one row per book, guaranteed by the engine, so #20 and #21 can rely on it.
* **Good:** the persisted model matches the CLAUDE.md domain model field for field.
* **Good:** due and overdue logic works on whole days and is safe across time-zone changes.
* **Bad:** part of the schema (two triggers) is not in `app/schemas/*.json`. Reviewers must know to look at the callback.
* **Bad:** a test database built without the shared configuration function silently lacks the invariant.
* **Bad:** `ON DELETE CASCADE` means deleting a book deletes its loan history. This is intended, but it is irreversible for the user.
* **Constraint:** every `BookshelfDatabase` instance (production and tests) is built through the shared `applyBookshelfConfig()` builder function.
* **Constraint:** no `OnConflictStrategy.REPLACE` on `books` or `loans`. A delete-and-reinsert on `books` cascades and wipes loans.
* **Constraint:** SQL comparisons on `loans.due_at` bind a `LocalDate` (epoch day), never epoch millis.
* **Constraint:** persisted `BookStatus` strings never change without a data migration.
* **Constraint:** any future migration that recreates the `loans` table must keep the triggers. `onOpen` re-asserts them, and the migration test must check `sqlite_master`.
* **Follow-up:** the lending epic adds repository write methods that map the trigger's `SQLiteConstraintException` to a domain `AlreadyOnLoan` result.
* **Follow-up:** the lending epic decides whether `borrower_contact` (third-party PII) is excluded from cloud backup, which currently includes the whole database.
* **Follow-up:** revisit identity (UUIDs) if sync, multi-device merge or file import of other users' libraries is ever added.

## Related

* **Requirement:** [#18](https://github.com/minicore-software/Bookshelf/issues/18) (AC1 entities match the domain model; AC3 tests for 0, 1 and an attempted 2nd open loan), epic [#17](https://github.com/minicore-software/Bookshelf/issues/17) decision D10 ("lent" is an overlay from an open loan).
* **Decisions:** none. This is the first ADR.
* **Artifacts:** [docs/plans/18-book-loan-room-schema.md](../plans/18-book-loan-room-schema.md) (schema, DAO shapes, repository contract).
