# Plan: #18 Create Book/Loan Room schema, DAOs, repository

| Field | Value |
|---|---|
| Ticket | [#18](https://github.com/minicore-software/Bookshelf/issues/18), part of epic [#17](https://github.com/minicore-software/Bookshelf/issues/17) (Library Screen, Home Screen). The data-foundation ticket that #20 (rows) and #21 (grid and chips) build on. |
| Branch | `feature/BOOKSHELF-0018-book-loan-room-schema` |
| Stack | Android: Kotlin, Room 2.8.5 (KSP), Hilt 2.60.1, Coroutines/Flow. Single `app` module. No backend. |
| Agents | `frontend-developer-android` builds it, `frontend-reviewer-android` reviews it |
| Work split | FRONTEND-ONLY |
| UX spec | Not needed. This is a pure data layer with no UI, no screen and no user-visible state. |
| API contract | No HTTP or event contract. The binding contract is the **repository interface and read models** in section 5. #20 and #21 build against them. |
| ADR | [ADR-001](../adr/ADR-001-Model-Book-And-Loan-Tables-With-Trigger-Enforced-Open-Loan.md), Accepted (database mutation: first real schema) |
| Status | Ready to build |

---

## 1. Context

- **Current state on `main` (844349e).** `core/database/BookshelfDatabase` is `@Database(entities = [PlaceholderEntity::class], version = 1, exportSchema = true)` with no DAOs and no type converters. `di/DatabaseModule` provides it as a `@Singleton` (`bookshelf.db`, `Room.databaseBuilder(...).build()`, no driver set, so the SupportSQLite path is in use). `app/schemas/.../1.json` holds only `scaffold_placeholder`. `features/library/{data,domain}` contain only `.gitkeep`. `BookshelfDatabaseTest` (androidTest) opens an in-memory DB.
- **Placeholder removal rule** (KDoc on `PlaceholderEntity`): the first real entity replaces it. If no build has been distributed, replace it in place and keep `version = 1`. There are no git tags and no GitHub releases, and the release build type is unsigned, so **keep `version = 1`** (decision S9).
- **Domain model** (CLAUDE.md): `Book` and `Loan` fields as listed in the ticket. "At most one open loan (`returnedAt == null`) per book." Identity is by ID, never by title.
- **Epic decisions that shape the data layer:** D9 (read-only epic, so there are no write flows in the UI), D10 ("lent" is an overlay from an open `Loan`, not a `Book.status`), D17 (the lent row shows the borrower and a due or overdue caption). D3 records a future 5-star rating. The design copy is day-granular ("Due in 3 days", "Overdue 2 days", "Due Oct 22").
- **No existing ADRs.** `docs/adr/` is created by this ticket.

## 2. Key decisions

| # | Decision | Why / alternatives |
|---|---|---|
| S1 | **Package placement.** Entities, converters and DAOs go in `core/database/{entity,converter,dao}`. The pure-Kotlin shared model (`Book`, `BookStatus`) goes in a new `core/model/` package. Library-specific read models and the `LibraryRepository` interface go in `features/library/domain/`. `RoomLibraryRepository` goes in `features/library/data/`. Hilt modules go in `di/`. | `Book` and `Loan` are shared tables: library, lending, progress and stats all read them, so their DAOs are the "shared DAOs" CLAUDE.md puts in `core/database`. `@Database` has to reference every DAO, so DAOs in `features/*` would make `core` depend on features. `core/model` stops the entity (core) from importing a feature's enum. **Rejected:** DAOs in `features/library/data`. The ticket wording suggests it, but it inverts the dependency direction and lending would later have to reach into library for `LoanDao`. **Rejected:** having the repository return entities directly. That leaks Room annotations into ViewModels and UI, and the read models need a joined shape anyway. |
| S2 | **IDs are `Long` with `@PrimaryKey(autoGenerate = true)`** (SQLite `AUTOINCREMENT`, so IDs are never reused after a delete). | The app is local-first and single-user with no sync, and backup restores the whole DB file, so there is no merge. Never reusing IDs keeps ID-based identity safe for future references such as loan history and the reading log. **Rejected:** UUID `String` IDs. They have no consumer today and cost a bigger index plus string-keyed joins. See ADR-001 for the revisit trigger. |
| S3 | **Timestamps are `java.time.Instant`, stored as epoch millis (`INTEGER`):** `createdAt`, `loanedAt`, `returnedAt`. **`dueAt` is a `java.time.LocalDate`, stored as epoch day (`INTEGER`).** | Due and overdue are calendar-day concepts in the design. An instant at local midnight would shift by a day when the device time zone changes, which would turn "Due today" into "Overdue". `java.time` is available natively from API 26 (minSdk is 29), so no desugaring is needed. The Kotlin property and column keep the domain-model name (`dueAt` / `due_at`). Because of the converter, any SQL comparison on `due_at` must bind a `LocalDate` parameter, never raw millis (noted in KDoc). |
| S4 | **`BookStatus` enum (`WantToRead`, `Reading`, `Finished`) is stored as `TEXT` with explicit stable values `"want_to_read"`, `"reading"`, `"finished"`.** These values come from an explicit `when` in the converter, not from `enum.name`. Unknown values throw. | The values match the CLAUDE.md vocabulary and stay readable in the DB inspector, and renaming a Kotlin constant cannot corrupt stored data. An unknown value would only appear after a downgrade or a bad migration, so throwing makes that fail loudly. **Rejected:** ordinal `INTEGER`. Reordering the enum would silently remap every row. |
| S5 | **At most one open loan per book is enforced in the database by two `BEFORE INSERT` / `BEFORE UPDATE` triggers on `loans` that `RAISE(ABORT)`.** They are created with `CREATE TRIGGER IF NOT EXISTS` from a `RoomDatabase.Callback` in both `onCreate` and `onOpen`. | This is the invariant the AC tests, and it must hold on every write path: today's DAO, the future lending flow, import, restore and test fixtures. It cannot depend on one repository method remembering to check. Room's `@Index` has no partial (`WHERE`) form, so the natural SQL answer (`UNIQUE INDEX ... WHERE returned_at IS NULL`) cannot be declared in Room. Full trade-off in ADR-001. **Rejected:** a repository-level transactional check only (any other writer bypasses it). **Rejected:** a partial unique index created by hand in a callback (it sits outside Room's schema model, and how Room's migration-time schema validation treats an undeclared index is unverified). **Rejected:** a nullable `open_marker` column with a regular `UNIQUE(book_id, open_marker)` index (it relies on SQLite treating NULLs as distinct, but it adds a persisted column outside the domain model that must be kept in sync with `returned_at`). `onOpen` re-asserting `IF NOT EXISTS` matters: a future auto-migration that recreates `loans` would drop the triggers silently, and this restores them on the next open. |
| S6 | **Foreign key `loans.book_id → books.id ON DELETE CASCADE`. Composite index `loans(book_id, returned_at)`. Index `books(status)`.** | Deleting a book removes its loan history. In a single-user app an orphaned loan has no meaning, and the user has no other way to reach it. The composite index serves the FK (Room warns without an index whose leading column is `book_id`), the open-loan `LEFT JOIN` and the trigger's `EXISTS` probe. `books(status)` serves the chip filter and counts. It is cheap but not essential at expected sizes. **Rejected:** `RESTRICT`. It would force a "return or delete loans first" flow that no design covers. |
| S7 | **The "all books" query already carries the open-loan overlay:** `LEFT JOIN loans ON book_id = id AND returned_at IS NULL`, giving `LibraryBook(book, openLoan: OpenLoan?)`. The lent-out query is the same join as an `INNER JOIN`, giving `LentBook`. | #21's grid needs "Lent to X" on every card (D10). Doing it in one SQL query avoids #21 combining two flows and matching rows by ID in memory. Because of S5 the `LEFT JOIN` returns at most one row per book, and the AC3 tests pin that. |
| S8 | **Counts:** `SELECT status, COUNT(*) ... GROUP BY status` plus `SELECT COUNT(DISTINCT book_id) FROM loans WHERE returned_at IS NULL`, combined in the repository into `LibraryCounts` (missing statuses count as 0), then `distinctUntilChanged()`. | No status string literals in SQL: values go through the converter, so S4 stays the single source. Both flows re-emit when their tables change. A brief intermediate emission between the two is harmless for chip badges. **Rejected:** one query with `SUM(status = 'reading')`. It is a single snapshot, but it duplicates the S4 strings in SQL. |
| S9 | **Replace `PlaceholderEntity` in place and keep `version = 1`. Regenerate `app/schemas/.../1.json`.** No `fallbackToDestructiveMigration`. | This is the documented removal rule, and no build has been distributed (no tags or releases). Developers with an existing debug install must uninstall it once, because the old DB's identity hash no longer matches and Room throws on open. **Do not** add a destructive fallback to work around that: it would silently wipe user data on every future schema mistake. |
| S10 | **Writes stay on the DAOs this ticket.** The repository is read-only (epic D9). `LoanDao.insert` uses `OnConflictStrategy.ABORT`. `REPLACE` is forbidden on `loans` and `books`. | No UI writes this epic. The AC3 tests write through the DAOs. The lending epic adds repository write methods that map `SQLiteConstraintException` to a domain result (`AlreadyOnLoan`). `REPLACE` on `books` would delete and re-insert the row, and with `ON DELETE CASCADE` that wipes its loans. |
| S11 | **DAO tests run on real SQLite as instrumented tests (`androidTest`). Converter and repository-assembly logic get JVM unit tests (`test`).** | The behaviour under test (`LEFT JOIN` semantics, triggers, FK cascade) lives in the SQLite engine. A fake DAO would test nothing that matters. This follows the existing `BookshelfDatabaseTest` pattern. **Rejected:** Robolectric, so the DAO tests could run under `./gradlew test`. It is a new test dependency, its support for compileSdk 37 is unconfirmed, and it emulates a different SQLite build than the device's. See section 8 on how this meets the AC's "unit tests". |

## 3. Schema (version 1)

`books`

| Column | Type | Null | Kotlin (`BookEntity`) | Notes |
|---|---|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | no | `id: Long = 0` | S2 |
| `title` | TEXT | no | `title: String` | Non-blank is enforced at the write boundary (Add Book epic), not in SQL |
| `author` | TEXT | no | `author: String` | Free text, comma-separated authors allowed (epic D3) |
| `isbn` | TEXT | yes | `isbn: String?` | String, because of leading zeros and the ISBN-10 `X`. Not unique: a user can own two copies |
| `cover_uri` | TEXT | yes | `coverUri: String?` | |
| `genre` | TEXT | yes | `genre: String?` | |
| `page_count` | INTEGER | yes | `pageCount: Int?` | |
| `published_year` | INTEGER | yes | `publishedYear: Int?` | |
| `status` | TEXT | no | `status: BookStatus` | S4. Index `index_books_status` |
| `rating` | INTEGER | yes | `rating: Int?` | 1..5 (epic D3). Range is checked at the write boundary later |
| `synopsis` | TEXT | yes | `synopsis: String?` | |
| `created_at` | INTEGER | no | `createdAt: Instant` | Epoch millis |

`loans`

| Column | Type | Null | Kotlin (`LoanEntity`) | Notes |
|---|---|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | no | `id: Long = 0` | |
| `book_id` | INTEGER FK → `books.id` ON DELETE CASCADE | no | `bookId: Long` | S6 |
| `borrower_name` | TEXT | no | `borrowerName: String` | |
| `borrower_contact` | TEXT | yes | `borrowerContact: String?` | PII (section 6) |
| `loaned_at` | INTEGER | no | `loanedAt: Instant` | Epoch millis |
| `due_at` | INTEGER | no | `dueAt: LocalDate` | **Epoch day**, S3 |
| `returned_at` | INTEGER | yes | `returnedAt: Instant?` | `NULL` means the loan is open |
| `remind_enabled` | INTEGER (0/1) | no | `remindEnabled: Boolean` | |
| `notes` | TEXT | yes | `notes: String?` | |

Index `index_loans_book_id_returned_at` on (`book_id`, `returned_at`).

Triggers, created by `OpenLoanInvariantCallback` (`onCreate` and `onOpen`, SupportSQLite overloads, because no driver is configured):

```sql
CREATE TRIGGER IF NOT EXISTS loans_single_open_loan_insert
BEFORE INSERT ON loans
WHEN NEW.returned_at IS NULL
 AND EXISTS (SELECT 1 FROM loans WHERE book_id = NEW.book_id AND returned_at IS NULL)
BEGIN SELECT RAISE(ABORT, 'book already has an open loan'); END;

CREATE TRIGGER IF NOT EXISTS loans_single_open_loan_update
BEFORE UPDATE OF book_id, returned_at ON loans
WHEN NEW.returned_at IS NULL
 AND EXISTS (SELECT 1 FROM loans WHERE book_id = NEW.book_id AND returned_at IS NULL AND id <> NEW.id)
BEGIN SELECT RAISE(ABORT, 'book already has an open loan'); END;
```

On Android, `RAISE(ABORT, ...)` surfaces as `android.database.sqlite.SQLiteConstraintException`. The developer confirms the exact type in the AC3 test and asserts on it.

**One build path for every database instance.** Add a single function, for example `fun RoomDatabase.Builder<BookshelfDatabase>.applyBookshelfConfig(): RoomDatabase.Builder<BookshelfDatabase>` in `core/database`, that adds the callback (and anything added later). Both `DatabaseModule` and every test's `inMemoryDatabaseBuilder` call it. This stops a test from passing against a database that lacks the triggers.

## 4. DAO shapes (`core/database/dao`)

`BookDao`
- `@Insert(onConflict = ABORT) suspend fun insert(book: BookEntity): Long`
- `@Query("DELETE FROM books WHERE id = :id") suspend fun deleteById(id: Long): Int`. This is the cascade test hook and the future delete-book path.
- `fun observeLibraryBooks(status: BookStatus?): Flow<List<LibraryBookRow>>`:
  ```sql
  SELECT b.*, l.id AS open_loan_id, l.borrower_name AS open_loan_borrower_name,
         l.loaned_at AS open_loan_loaned_at, l.due_at AS open_loan_due_at
  FROM books AS b
  LEFT JOIN loans AS l ON l.book_id = b.id AND l.returned_at IS NULL
  WHERE (:status IS NULL OR b.status = :status)
  ORDER BY b.created_at DESC, b.id DESC
  ```
- `fun observeLentOutBooks(): Flow<List<LibraryBookRow>>`: the same projection with `INNER JOIN` and no status filter, `ORDER BY l.due_at ASC, b.id ASC` (most urgent first).
- `fun observeStatusCounts(): Flow<List<StatusCountRow>>`: `SELECT status, COUNT(*) AS count FROM books GROUP BY status`

`LoanDao`
- `@Insert(onConflict = ABORT) suspend fun insert(loan: LoanEntity): Long`. The trigger aborts a second open loan.
- `@Query("UPDATE loans SET returned_at = :returnedAt WHERE id = :loanId AND returned_at IS NULL") suspend fun markReturned(loanId: Long, returnedAt: Instant): Int`
- `@Query("SELECT * FROM loans WHERE book_id = :bookId AND returned_at IS NULL") suspend fun findOpenLoan(bookId: Long): LoanEntity?`
- `@Query("SELECT COUNT(DISTINCT book_id) FROM loans WHERE returned_at IS NULL") fun observeOpenLoanCount(): Flow<Int>`

Row POJOs (`core/database/dao`, or `core/database/model` if the developer prefers):
- `LibraryBookRow(@Embedded val book: BookEntity, @ColumnInfo("open_loan_id") val openLoanId: Long?, @ColumnInfo("open_loan_borrower_name") val openLoanBorrowerName: String?, @ColumnInfo("open_loan_loaned_at") val openLoanLoanedAt: Instant?, @ColumnInfo("open_loan_due_at") val openLoanDueAt: LocalDate?)`
- `StatusCountRow(val status: BookStatus, val count: Int)`

The default ordering (newest added first; lent books by due date) is a sensible default, not a design requirement. If the mock implies another order, #20 and #21 may change the `ORDER BY` without touching the contract.

`BookshelfDatabase`: `entities = [BookEntity::class, LoanEntity::class]`, `version = 1`, `exportSchema = true`, `@TypeConverters(BookshelfConverters::class)`, `abstract fun bookDao(): BookDao`, `abstract fun loanDao(): LoanDao`.

`BookshelfConverters` (`core/database/converter`): `Instant ↔ Long` (epoch millis), `LocalDate ↔ Long` (epoch day), `BookStatus ↔ String` (S4). Nullable in, nullable out.

## 5. Contract: repository and read models (binding for #20 and #21)

`core/model/`
```kotlin
enum class BookStatus { WantToRead, Reading, Finished }

data class Book(
    val id: Long, val title: String, val author: String, val isbn: String?,
    val coverUri: String?, val genre: String?, val pageCount: Int?, val publishedYear: Int?,
    val status: BookStatus, val rating: Int?, val synopsis: String?, val createdAt: Instant,
)
```

`features/library/domain/model/`
```kotlin
data class OpenLoan(val loanId: Long, val borrowerName: String, val loanedAt: Instant, val dueAt: LocalDate)
data class LibraryBook(val book: Book, val openLoan: OpenLoan?)   // openLoan != null  <=>  "lent" overlay (D10)
data class LentBook(val book: Book, val loan: OpenLoan)
data class LibraryCounts(val all: Int, val reading: Int, val finished: Int, val wantToRead: Int, val lentOut: Int)
```

`features/library/domain/LibraryRepository.kt`
```kotlin
interface LibraryRepository {
    fun observeBooks(status: BookStatus? = null): Flow<List<LibraryBook>>   // null = All chip
    fun observeCurrentlyReading(): Flow<List<LibraryBook>>                  // = observeBooks(Reading)
    fun observeLentOutBooks(): Flow<List<LentBook>>                         // "Lent out" row and chip
    fun observeCounts(): Flow<LibraryCounts>                                // chip badges
}
```

Semantics:
- All flows are cold, backed by Room, and re-emit on table changes. They never emit `null`. An empty library gives `emptyList()` and `LibraryCounts(0, 0, 0, 0, 0)`, which the empty state (#22) relies on.
- `lentOut` counts books with an open loan. It is independent of status: a lent book is also counted under its status, and `all` = sum of the three statuses.
- `borrowerContact` and `notes` are deliberately **not** exposed in the library read models. The Library screen does not show them (section 6, data minimisation).
- Overdue is **not** computed in the repository. #20 derives it from `OpenLoan.dueAt` against `LocalDate.now(clock)` with an injected `java.time.Clock`. A repository-computed flag would go stale at midnight because the flow does not re-emit then.
- Mapping from entity to model happens in `RoomLibraryRepository` (`features/library/data`), in internal mapper functions. Entities never leave `data`.

Compatibility: everything is new and additive, so no breaking change (`!` does not apply).

## 6. Security (light threat model)

- **Entry points:** none new. No exported component, permission, network access or IPC. The data is reached only in-process through Hilt.
- **Input:** all SQL uses Room-bound parameters. No `@RawQuery`, and no string-concatenated SQL (rule for reviewers).
- **Stored data and PII:** `borrowerName` and `borrowerContact` are personal data about third parties, stored locally. The existing backup rules include `bookshelf.db` in cloud backup and device transfer, so this data reaches the user's own Google backup. That is acceptable for a single-user personal app, and recorded here so the lending epic can decide whether `borrower_contact` needs an exclusion. **Rule:** never log entities or models. Data-class `toString()` prints every field, `borrowerContact` included. No `Log.*` calls with entities in this ticket.
- **Data minimisation:** the library read models omit `borrowerContact` and `notes` (section 5).
- **Integrity:** the triggers (S5), FK cascade (S6) and `ABORT` conflict strategy (S10) keep the invariants at the database level, so a buggy writer cannot corrupt the overlay.

## 7. Quality attributes and fitness functions

| Attribute | How the design serves it | Fitness function (developer implements) |
|---|---|---|
| **Data integrity** | Trigger-enforced single open loan (S5), FK cascade (S6), no `REPLACE` (S10). | **FF1** (instrumented): after building the DB through `applyBookshelfConfig()`, `sqlite_master` contains both triggers and `PRAGMA foreign_keys` returns 1. **FF2** (instrumented): close and reopen a file-backed test DB after manually dropping a trigger; the trigger is back (proves `onOpen` re-assertion). |
| **Evolvability / migration safety** | Exported schema JSON under review; stable persisted status values (S4); single build path (section 3). | **FF3** (JVM): converter round-trip tests, plus an exact-value assertion that `BookStatus` persists as `want_to_read` / `reading` / `finished`. Changing a stored value then fails a test instead of silently orphaning rows. **FF4** (review): `app/schemas/.../1.json` is committed and its diff is reviewed. |
| **Layering** | Dependency direction `features → core`, never the reverse (S1). Entities stay in `data`. | **FF5** (JVM, cheap): a test walks `app/src/main/java/com/minicore/bookshelf/core/**` and fails on any `import com.minicore.bookshelf.features`. It is the same file-reading style as `FrauncesFontFileTest`. Promote it to Konsist only if more rules appear. |
| **Performance** | Indices for filter, join and trigger probe (S6). Flow queries run off the main thread (Room-managed). | None automated. Expected libraries are hundreds to low thousands of rows, and full-list flows are fine at that size. Revisit with Paging 3 if the grid ever needs more than about 5k books. |

## 8. Test strategy

**On "unit tests" in AC3:** the open-loan join and the invariant are SQL behaviour, so they are tested on an in-memory Room database on real SQLite (`androidTest`, run with `./gradlew connectedAndroidTest`). These are isolated, single-component DAO and repository tests, which is the only level where the join is actually exercised (S11). Pure logic (converters, count assembly) is JVM unit-tested under `./gradlew test`.

| AC | Level | Test |
|---|---|---|
| AC1: entities match the domain model | Instrumented + review | Insert a `BookEntity` with every nullable field null, and another with every field set. Both round-trip unchanged (all 12 `Book` and 9 `Loan` fields). The schema JSON is reviewed against section 3. |
| AC1 | JVM | FF3 (converters, stable status values). |
| AC2: repository queries | Instrumented (`RoomLibraryRepositoryTest`, real DAOs) | `observeBooks(null)` returns all books in order. `observeBooks(Finished)` returns only finished books. `observeCurrentlyReading()` returns only reading books. `observeCounts()` is correct for a mixed library, `all` equals the sum of statuses, and missing statuses are 0. An empty DB gives empty lists and all-zero counts. Use `first()` or Turbine-less `take(n).toList()` with `runTest` (Turbine is not a dependency; do not add it for this). |
| AC2 | JVM | `LibraryCounts` assembly from `StatusCountRow` lists with missing statuses (fake flows). |
| **AC3, 0 open loans** | Instrumented (`LoanDaoTest` / `BookDaoTest`) | A book with no loans, and a book with only a returned loan: `observeLentOutBooks()` is empty, `openLoan == null` for both in `observeBooks()`, and `lentOut == 0`. |
| **AC3, 1 open loan** | Instrumented | One returned plus one open loan on book A: exactly one `LentBook` (A) with the open loan's borrower, `loanedAt` and `dueAt` (not the returned loan's). A appears once in `observeBooks()` with `openLoan` set, and `lentOut == 1`. |
| **AC3, attempted 2nd open loan** | Instrumented | With an open loan on A, inserting a second open loan on A throws `SQLiteConstraintException`. Afterwards there is still exactly one open loan on A, the borrower is unchanged, and **A appears exactly once** in `observeBooks()` (no duplicate row from the join). |
| AC3 (edges, same test class) | Instrumented | An open loan on a different book B is allowed (`lentOut == 2`). After `markReturned` on A's loan, a new open loan on A is allowed (re-lend). Updating a returned loan back to `returned_at = NULL` while another loan on the same book is open throws (update trigger). `markReturned` on an already-returned loan returns 0 rows. Deleting book A cascades to its loans. |
| Wiring | Build | `./gradlew assembleDebug` compiles the Hilt graph (`LibraryRepository` is injectable). The existing `BookshelfDatabaseTest` is switched to `applyBookshelfConfig()` and keeps passing. |

## 9. Work split and tasks

**FRONTEND-ONLY:** one Android app with no backend. The data layer lives in the app module.

`frontend-developer-android`, in build order:

1. `core/model/`: `BookStatus`, `Book` (section 5). (AC1)
2. `core/database/converter/BookshelfConverters` (S3, S4) and the JVM tests for FF3. (AC1)
3. `core/database/entity/`: `BookEntity`, `LoanEntity` (section 3: columns, FK, indices). Delete `PlaceholderEntity`. Update `BookshelfDatabase` (entities, converters, DAO accessors, `version = 1`). Regenerate `app/schemas/.../1.json` (S9). (AC1)
4. `OpenLoanInvariantCallback` plus `applyBookshelfConfig()` (section 3). Switch `DatabaseModule` and `BookshelfDatabaseTest` to it. FF1, FF2. (AC3)
5. `core/database/dao/`: `BookDao`, `LoanDao`, `LibraryBookRow`, `StatusCountRow` (section 4). Instrumented DAO tests for AC1 round-trip, AC3 (all three cases plus edges) and cascade. (AC1, AC3)
6. `features/library/domain/`: read models and the `LibraryRepository` interface. `features/library/data/RoomLibraryRepository` with mappers and `distinctUntilChanged()` on counts. Instrumented repository tests and the JVM count-assembly test. Remove both `.gitkeep` files. (AC2, AC3)
7. Hilt: `DatabaseModule` gains `provideBookDao` and `provideLoanDao`. New `di/LibraryDataModule` with `@Binds @Singleton LibraryRepository ← RoomLibraryRepository`. FF5 (layering test). (AC2)
8. Update the CLAUDE.md architecture tree: add `core/model/` and the `core/database/{entity,converter,dao}` subpackages. Run `./gradlew lint test connectedAndroidTest assembleDebug`. In the PR, note that existing debug installs must be uninstalled once (S9). (all)

## 10. Risks for QA to probe

| # | Risk | Probe |
|---|---|---|
| R1 | **Triggers missing on a real install** because `DatabaseModule` does not use `applyBookshelfConfig()`, or the wrong `Callback` overload is implemented, so tests pass on a different build path. | FF1 against the same builder function. The reviewer checks that `DatabaseModule` calls it. Manual: open the DB in App Inspection and check `sqlite_master` for both triggers. |
| R2 | **Duplicate grid rows** if the invariant ever fails (`LEFT JOIN` fan-out). | The AC3 "exactly once" assertion. |
| R3 | **Due-date off-by-one** across time zones, or a raw-millis comparison against `due_at`. | The `LocalDate` round-trip test. Reviewer rule: no numeric literal or `Long` param compared to `due_at`. |
| R4 | **Crash on first launch for existing debug installs** (identity hash mismatch, S9). | Expected and documented. Uninstall once. Confirm no `fallbackToDestructiveMigration` was added. |
| R5 | **Cascade wipes loan history** through `REPLACE` or a delete. | Reviewer rule: no `OnConflictStrategy.REPLACE`. Cascade behaviour is tested and intended. |
| R6 | **Count flicker** between the two combined flows. | Cosmetic. `distinctUntilChanged()` limits it. #21 verifies visually. |
| R7 | **PII in logs or crash reports** through data-class `toString()`. | Grep the diff for `Log.` or `println` with entities or models. |

## 11. Open items (not blocking)

- **No seed data on device.** The epic is read-only (D9), so on a real install #20 and #21 will show only the empty state until Add Book ships. Their Compose tests use a fake `LibraryRepository`. If manual on-device QA of the populated state is wanted, #20 or #21 should add a debug-only seeder (`src/debug`). That is not in this ticket's scope.
- **Lending epic follow-ups** (ADR-001 Constraints): add repository write methods that map `SQLiteConstraintException` to a domain `AlreadyOnLoan` result, and decide whether `borrower_contact` is excluded from cloud backup.
- **No ADR index exists** and CLAUDE.md does not reference one, so none was created.
