# Plan: #30 Extend Book schema: comment, memory, cover-color fields

| Field | Value |
|---|---|
| Ticket | [#30](https://github.com/minicore-software/Bookshelf/issues/30), part of epic [#29](https://github.com/minicore-software/Bookshelf/issues/29) (Book Management). The enabling ticket for #31 Add, #32 View, #33 Edit, #34 Remove and #35 Delete. |
| Branch | `feature/BOOKSHELF-0030-extend-book-schema` |
| Stack | Android: Kotlin, Room 2.8.5 (KSP), Hilt 2.60.1, Coroutines/Flow. Single `app` module. No backend. |
| Agents | `frontend-developer-android` builds it, `frontend-reviewer-android` reviews it |
| Work split | FRONTEND-ONLY |
| UX spec | Not needed. Data layer only. No screen, no new state, and no visible change (section 2, S6). |
| API contract | No HTTP or event contract. The binding contract is the **migration (section 3)** and the **domain/DAO/repository shapes (section 4)**. #31–#35 build against them. |
| ADR | [ADR-002](../adr/ADR-002-Store-Book-Cover-As-Uri-Or-Preset-Key-With-Trigger-Enforced-Exclusivity.md), Accepted (category: database mutation, the first real migration) |
| Status | Ready to build |

---

## 1. Context

- **Current state on `main` (111f2e0).** `BookshelfDatabase` is at `version = 1` with `exportSchema = true`, and `app/schemas/.../1.json` is committed. `books` has `cover_uri TEXT` (nullable) and no comment, memory or cover-colour column. No migration exists, and `applyBookshelfConfig()` registers only `OpenLoanInvariantCallback`. There is no `fallbackToDestructiveMigration`.
- **Covers today.** No UI writes `cover_uri`. `BookCover` ignores it and draws `coverPalette[Math.floorMod(bookId, 6)]`. The palette lives in `colors.xml` as `cover_1_bg`…`cover_6_bg`, with `cover_cream`/`ink` as foregrounds.
- **ADR-001** set up the trigger-in-callback pattern, the single build function, no `REPLACE` on `books`, and stable `TEXT` literals for enums. Version 1 was replaced in place because nothing had shipped. There are still no tags or releases, but #30's AC explicitly requires a data-preserving migration, so **this ticket introduces migration 1 → 2** and does not replace version 1 again.
- **Epic decisions in play:** D3 (cover = upload OR one of 6 presets, short comment), D4/D15 (3 independently optional memory fields), D9 (one discriminated cover field, never both), D11 (this ticket goes first). Not in this ticket: D7 (author optional), see the open items in section 11.

## 2. Key decisions

| # | Decision | Why / alternatives |
|---|---|---|
| S1 | **Two nullable columns, `cover_uri` (existing) and `cover_color` (new), made mutually exclusive by `BEFORE INSERT`/`BEFORE UPDATE` triggers on `books`.** In the domain the pair is a sealed `Cover` (`Image(uri)` / `Preset(preset)`), so Kotlin cannot express "both". | Same mechanism as ADR-001, and it covers every writer. **Rejected:** a `CHECK` constraint (Room cannot declare it, so fresh and upgraded installs would differ), app-level only (any `BookEntity` writer bypasses it), and one encoded column (needs a table rebuild on SQLite 3.22 and string parsing on every read). Full trade-off in ADR-002. |
| S2 | **`cover_color` stores a stable preset key, `preset_1`…`preset_6`, not a hex value.** `CoverPreset` enum in `core/model`, persisted through an explicit `when` in `BookshelfConverters`, like `BookStatus`. | The palette values are not settled: `colors.xml` and Visual Spec §1.4 disagree. With keys, reconciling them is a resource-only change. **Rejected:** hex (freezes today's colours into user data), ordinal (reordering silently remaps rows). |
| S3 | **Unknown stored key → the converter returns `null`, which is not a throw. Reads map "neither set" to the id-derived preset.** | A cover is cosmetic. Crashing the whole Library over a bad colour key (downgrade, bad import) is worse than drawing a default. This deliberately differs from `BookStatus`, where an unknown value breaks filtering and counts and so must fail loudly. |
| S4 | **Backfill: `UPDATE books SET cover_color = 'preset_' || ((id % 6) + 1) WHERE cover_uri IS NULL AND cover_color IS NULL`.** Rows with a `cover_uri` keep it, and their `cover_color` stays null. The new text fields stay null. | IDs are `AUTOINCREMENT` and ≥ 1, so `id % 6` = `floorMod(id, 6)`. `preset_{n+1}` ↔ `cover_{n+1}_bg` is exactly the colour `BookCover` draws today, so there is no visible change (ticket value). `CoverPreset.forBookId(id)` applies the same formula for the "neither" fallback, and a test pins that SQL and Kotlin agree. |
| S5 | **Hand-written `MIGRATION_1_2`, registered inside `applyBookshelfConfig()`** next to the callbacks (`addMigrations(*BookshelfMigrations.ALL)`). It is not `@AutoMigration`, and there is no destructive fallback. | One reviewable transaction holds the `ALTER`s, backfill and triggers. Tests and production share the registration path (extends the ADR-001 single-build-path constraint). See ADR-002. |
| S6 | **`BookCover` (composable) changes its parameter from `coverUri: String?` to `cover: Cover`.** `Preset` → that preset's colours. `Image` → still the id-derived placeholder (image loading stays deferred to #31, as the KDoc already says). | Required to compile once `Book.coverUri` is replaced by `Book.cover`. Output is pixel-identical for all existing data (S4). The existing pixel tests (`LibraryGridPixelTest`, `LibraryRowsPixelTest`) guard this. The resolution is an exhaustive `when (preset)` in `core/designsystem`, never `ordinal` or list position. |
| S7 | **Blank optional text becomes null at the domain → entity mapper** (`comment`, `memoryWhy`, `memoryWhere`, `memoryWhat`: `takeIf { it.isNotBlank() }`). No trimming of other content, and no length caps. | View (#32) shows "— not yet written —" when a field is absent. One rule in one place stops "" vs null drifting across #31 and #33. The epic deliberately sets no caps. `TEXT` is unbounded, and a cap belongs in the form if one is ever needed. |
| S8 | **Write surface: `BookDao` gains `update` (`@Update`, `ABORT`) and `observeById`. `LibraryRepository` gains `observeBook`, `addBook` and `updateBook`.** No `@Upsert`, no `REPLACE`. | Meets AC4 ("read and write") with the smallest surface #31–#33 need. `@Update` is a plain `UPDATE`, so it never cascades into `loans` (ADR-001 S10/R5). Use cases (clock, `want_to_read` default, form validation) are #31/#33's job. |
| S9 | **Column names:** `comment`, `memory_why`, `memory_where`, `memory_what`, `cover_color`. All are `TEXT`, nullable, with **no `@ColumnInfo(defaultValue)`**. | Snake_case, matching the existing columns. With no default the entity matches `ADD COLUMN … TEXT`, so Room's post-migration `TableInfo` validation passes. |

## 3. Migration contract (binding)

### 3.1 Schema delta, version 1 → 2

`books`, added columns (all other columns, the `index_books_status` index and the whole `loans` table are unchanged):

| Column | SQLite type | Null | Default | Kotlin (entity) | Kotlin (domain `Book`) |
|---|---|---|---|---|---|
| `comment` | `TEXT` | yes | none | `comment: String?` | `comment: String?` (null = none; never blank, S7) |
| `memory_why` | `TEXT` | yes | none | `memoryWhy: String?` | `memoryWhy: String?` |
| `memory_where` | `TEXT` | yes | none | `memoryWhere: String?` | `memoryWhere: String?` |
| `memory_what` | `TEXT` | yes | none | `memoryWhat: String?` | `memoryWhat: String?` |
| `cover_color` | `TEXT` | yes | none | `coverColor: CoverPreset?` (converter) | together with `cover_uri` → `cover: Cover` (non-null) |

Allowed values of `cover_color`: `NULL` or one of the literals `preset_1`, `preset_2`, `preset_3`, `preset_4`, `preset_5`, `preset_6`.

New triggers on `books` (not in the schema JSON, same as ADR-001):

```sql
CREATE TRIGGER IF NOT EXISTS books_single_cover_insert
BEFORE INSERT ON books
WHEN NEW.cover_uri IS NOT NULL AND NEW.cover_color IS NOT NULL
BEGIN SELECT RAISE(ABORT, 'book cover has both uri and color'); END;

CREATE TRIGGER IF NOT EXISTS books_single_cover_update
BEFORE UPDATE OF cover_uri, cover_color ON books
WHEN NEW.cover_uri IS NOT NULL AND NEW.cover_color IS NOT NULL
BEGIN SELECT RAISE(ABORT, 'book cover has both uri and color'); END;
```

Invariant: **never both set**. "Neither set" is tolerated at the SQL level (S3), but the repository never writes it because `Cover` is non-null.

### 3.2 `MIGRATION_1_2` (`core/database/migration/BookshelfMigrations.kt`)

Statements, in this order. Room wraps `migrate()` in a transaction, so a failure rolls the whole thing back.

1. `ALTER TABLE books ADD COLUMN comment TEXT`
2. `ALTER TABLE books ADD COLUMN memory_why TEXT`
3. `ALTER TABLE books ADD COLUMN memory_where TEXT`
4. `ALTER TABLE books ADD COLUMN memory_what TEXT`
5. `ALTER TABLE books ADD COLUMN cover_color TEXT`
6. Backfill: `UPDATE books SET cover_color = 'preset_' || ((id % 6) + 1) WHERE cover_uri IS NULL AND cover_color IS NULL`
7. `BookCoverInvariantCallback.createTriggers(db)` (the two statements in 3.1, shared with the callback so the SQL exists once)

Rules:
- `BookshelfMigrations.ALL = arrayOf(MIGRATION_1_2)`. `applyBookshelfConfig()` becomes `addCallback(OpenLoanInvariantCallback).addCallback(BookCoverInvariantCallback).addMigrations(*BookshelfMigrations.ALL)`.
- `@Database(version = 2)`. Commit the generated `app/schemas/.../2.json` and **keep `1.json` unchanged** (the migration test needs it).
- `BookCoverInvariantCallback` follows `OpenLoanInvariantCallback` exactly (`onCreate` + `onOpen`, `CREATE TRIGGER IF NOT EXISTS`). After a migration, `onOpen` also restores the `loans` triggers, which the `MigrationTestHelper` v1 database never had.
- Never `fallbackToDestructiveMigration*`.

### 3.3 Backfill truth table

| Before (v1) | After (v2) |
|---|---|
| `cover_uri = NULL`, id = k | `cover_uri = NULL`, `cover_color = 'preset_' + ((k % 6) + 1)` |
| `cover_uri = 'content://…'` | `cover_uri` unchanged, `cover_color = NULL` |
| any row | `comment`, `memory_*` = `NULL`. Every v1 column is byte-identical. `loans` rows are unchanged and still reference the book. |

## 4. Domain, DAO and repository shapes (binding for #31–#35)

**`core/model`** (pure Kotlin):

```kotlin
enum class CoverPreset { Preset1, Preset2, Preset3, Preset4, Preset5, Preset6;
    companion object { fun forBookId(bookId: Long): CoverPreset = entries[Math.floorMod(bookId, entries.size.toLong()).toInt()] } }

sealed interface Cover {
    data class Image(val uri: String) : Cover
    data class Preset(val preset: CoverPreset) : Cover
}

data class Book(
    val id: Long, val title: String, val author: String, val isbn: String?,
    val cover: Cover,                       // replaces coverUri
    val genre: String?, val pageCount: Int?, val publishedYear: Int?,
    val status: BookStatus, val rating: Int?, val synopsis: String?,
    val comment: String?, val memoryWhy: String?, val memoryWhere: String?, val memoryWhat: String?,
    val createdAt: Instant,
)
```

(Field order is illustrative. Names, types and nullability are binding.)

**`core/database`:**
- `BookEntity` gains `comment`, `memoryWhy`, `memoryWhere`, `memoryWhat` (`String?`) and `@ColumnInfo(name = "cover_color") coverColor: CoverPreset?`. Default values on the Kotlin constructor (`= null`) are allowed to limit fixture churn. A `@ColumnInfo(defaultValue)` is not.
- `BookshelfConverters`: `coverPresetToString(CoverPreset?): String?` (explicit `when` → `preset_1`…`preset_6`) and `stringToCoverPreset(String?): CoverPreset?` (unknown → `null`, S3).
- `BookDao` adds:
  - `@Update(onConflict = OnConflictStrategy.ABORT) suspend fun update(book: BookEntity): Int` (rows affected; 0 = no such id)
  - `@Query("SELECT * FROM books WHERE id = :id") fun observeById(id: Long): Flow<BookEntity?>`
  - The existing `insert` is unchanged. `LibraryBookRow` picks the new columns up through `b.*` + `@Embedded`, so the queries need no change.

**Mappers (`features/library/data/LibraryMappers.kt`):**
- `BookEntity.toDomain()`: `cover = when { coverUri != null -> Image(coverUri); coverColor != null -> Preset(coverColor); else -> Preset(CoverPreset.forBookId(id)) }`. Image wins if both are somehow set, matching Visual Spec §5.9 "the image always replaces the color" (the triggers prevent that state anyway).
- `Book.toEntity()`: `Image` → `(coverUri = uri, coverColor = null)`, `Preset` → `(null, preset)`. The four text fields go through blank → null (S7).

**`LibraryRepository` additions:**

| Method | Semantics |
|---|---|
| `fun observeBook(id: Long): Flow<Book?>` | Cold, re-emits on `books` changes. Emits `null` when the id does not exist (or after a delete). |
| `suspend fun addBook(book: Book): Long` | Requires `book.id == 0L` (`require`). Inserts with `ABORT` and returns the new id. The caller supplies `createdAt` and `status` (#31's use case: `Clock`, `WantToRead`). |
| `suspend fun updateBook(book: Book): Boolean` | `@Update` by id. `false` when the id does not exist. Never touches `loans`. |

Errors: a both-covers write cannot happen through the repository (the type rules it out). If it happens at DAO level, `SQLiteConstraintException` propagates. The repository does not map it, because it is a programming error and not a user outcome.

Compatibility: the schema change is additive. In Kotlin, `Book.coverUri` → `Book.cover` and the `BookCover(coverUri=)` → `BookCover(cover=)` change break call sites inside the app (mechanical, around 25 test fixtures plus 3 production call sites). Nothing outside the app consumes them, so **no `!`** on the commits.

## 5. Security (light threat model)

- **Entry points:** none new. No exported component, permission, network or IPC.
- **Input:** all SQL is Room-bound or static. The backfill is a constant statement with no interpolation. No `@RawQuery`.
- **Stored data:** `comment` and `memory_*` are the user's own personal reflections ("What was I going through?"), so they are sensitive. They are stored locally and included in the existing cloud backup of `bookshelf.db`, which is the same exposure as the rest of the library and acceptable for a single-user app. **Rule:** never log `Book`/`BookEntity`. Data-class `toString()` prints the memory fields. No `Log.*`/`println` with these objects (same rule as #18).
- **Cover URI:** this ticket only stores the string. Whether it is a persisted SAF permission or a copy in app storage is #31's decision. Not validated here.
- **Integrity:** the both-covers triggers (S1), `ABORT` on insert and update, no `REPLACE`/`@Upsert` on `books`.

## 6. Quality attributes and fitness functions

| Attribute | How the design serves it | Fitness function (developer implements) |
|---|---|---|
| **Data integrity (migration safety)** | Additive `ADD COLUMN` only, one transaction, a deterministic backfill, and v1 data byte-identical afterwards. | **FF-M1** (instrumented): `MigrationTestHelper` creates v1 from `1.json`, seeds rows, runs `runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)`, then asserts the section 3.3 table. |
| **Integrity (cover invariant)** | Triggers plus the sealed `Cover`. | **FF-M2** (instrumented): a DB built through `applyBookshelfConfig()` has `books_single_cover_insert`/`_update` (and still both `loans_*` triggers) in `sqlite_master`. A both-set insert or update throws `SQLiteConstraintException`. Extends #18's FF1. |
| **Evolvability** | Preset keys instead of colours (S2), one migration registry, committed schema per version. | **FF-M3** (JVM, file-reading like `LayeringTest`): for the `@Database` version N read from `BookshelfDatabase.kt`, `app/schemas/.../1.json`…`N.json` all exist, `BookshelfMigrations.ALL` covers every step `k → k+1`, and no file under `src/main` contains `fallbackToDestructiveMigration`. **FF-M4** (JVM): exact-literal test that `CoverPreset` persists as `preset_1`…`preset_6`, unknown → null. |
| **No visible change** | The backfill formula equals the old render formula (S4). | **FF-M5**: in FF-M1, for ids 1..13 the backfilled `cover_color` equals the converter literal of `CoverPreset.forBookId(id)`. The existing grid and row pixel tests stay green unmodified, apart from fixture construction. |

## 7. Test strategy

| AC | Level | Test |
|---|---|---|
| AC1: additive columns, no data loss | Instrumented (`BookshelfMigration1To2Test`, `MigrationTestHelper`) | v1 DB seeded with raw SQL: ≥ 7 books (so ids wrap past 6), one book with `cover_uri` set, all-nullables-null and all-fields-set rows, `status` values in all three literals, and one open plus one returned loan. After migration: `runMigrationsAndValidate` passes (Room validates the column set against the entity), every v1 column of every row is equal, and the loans are intact and still joined. |
| AC2: backfill | Instrumented (same class) | Section 3.3: `cover_color` per id formula (FF-M5), the `cover_uri` row keeps its URI with `cover_color` NULL, and all four text columns are NULL. Then open the migrated file with `Room.databaseBuilder(...).applyBookshelfConfig()` and read through `BookDao.observeLibraryBooks(null)`. The domain `cover` is `Preset(forBookId(id))` / `Image(uri)`, and both `loans_*` and `books_*` triggers exist (onOpen re-assertion). |
| AC3: migration test exists | Build | The class above runs in `connectedAndroidTest`. Needs `androidTestImplementation(libs.room.testing)` (new catalog entry `androidx.room:room-testing`, `version.ref = "room"`) and `app/schemas` added to the `androidTest` assets source set (use the AGP 9.4 DSL that compiles). |
| AC4: DAO read/write | Instrumented (`BookDaoTest`) | Round-trip all 5 new columns via `insert` → `observeById`. `update` changes comment/memory/cover (Image ↔ Preset both directions) and returns 1, and returns 0 for a missing id. **`update` on a lent book leaves its loans intact** (guards against `REPLACE`). Both-set insert and both-set update throw `SQLiteConstraintException`, and the row is unchanged afterwards. |
| AC4: repository | Instrumented (`RoomLibraryRepositoryTest`) + JVM (mappers) | Instrumented: `addBook` → `observeBook` returns the same `Book` (with id). `updateBook` re-emits. `addBook` with id ≠ 0 throws `IllegalArgumentException`. JVM: `toDomain` for Image / Preset / neither (fallback) / unknown key (fallback); `toEntity` blank → null for each of the 4 text fields, and Image/Preset column split. |
| Wiring | Build | `./gradlew lint test connectedAndroidTest assembleDebug`. FF-M3. `LayeringTest` still green (`core/designsystem` imports `core/model` only). |

Risky failure modes are listed in section 10.

## 8. Work split and tasks

**FRONTEND-ONLY:** one Android app with no backend. All the work is in the `app` module.

`frontend-developer-android`, in build order:

1. `core/model`: `CoverPreset` (+ `forBookId`), `Cover`, the new `Book` fields, replacing `coverUri` with `cover`. (AC4)
2. `BookshelfConverters`: `CoverPreset` ↔ literal, plus FF-M4. (AC4)
3. `BookEntity`: 5 new columns (S9). `BookCoverInvariantCallback` with a shared `createTriggers(db)`. (AC1, AC4)
4. `core/database/migration/BookshelfMigrations.kt`: `MIGRATION_1_2` (section 3.2). Register it in `applyBookshelfConfig()`, bump `version = 2`, commit `2.json`. (AC1, AC2)
5. `room-testing` dependency, schema assets for `androidTest`, `BookshelfMigration1To2Test` (FF-M1, FF-M5). (AC1–AC3)
6. `BookDao.update` / `observeById` and `BookDaoTest` additions (FF-M2). (AC4)
7. Mappers (`toDomain` / `toEntity`, S3, S7) with JVM tests. `LibraryRepository` + `RoomLibraryRepository`: `observeBook`, `addBook`, `updateBook`, with instrumented tests. (AC4)
8. `core/designsystem`: `BookCover(cover: Cover, …)` (S6), resolving `CoverPreset` → `CoverColors` with an exhaustive `when`. Update the 3 production call sites and the fixtures. Pixel tests stay green. (no-visible-change)
9. FF-M3 JVM test. Update the CLAUDE.md domain model `Book` row (comment?, memoryWhy?, memoryWhere?, memoryWhat?, cover = coverUri? XOR coverColor?) and the architecture tree (`core/database/migration/`). Fix the stale "this ticket is read-only" KDoc on `BookEntity` and the "coverUri is accepted but ignored" KDoc on `BookCover`. (all)

## 9. Assumptions recorded (not blocking)

- **Preset palette:** the 6 keys map 1:1 onto the existing `cover_1_bg`…`cover_6_bg` (+ foregrounds) in `colors.xml`. Replacing those values with Visual Spec §1.4 (index N ↔ row N) is a resource-only change for #31 or the reconciliation pass, with no data impact (ADR-002).
- **No distributed build:** there are no tags or releases, but the migration is written as if users exist (the AC demands it). From now on, version bumps go through a migration and never an in-place replace.

## 10. Risks for QA to probe

| # | Risk | Probe |
|---|---|---|
| R1 | **Room post-migration validation fails on device** (`IllegalStateException: Migration didn't properly handle`) because of a `defaultValue`/affinity mismatch between the entity and the `ALTER` SQL. | FF-M1's `runMigrationsAndValidate`. Reviewer: no `@ColumnInfo(defaultValue)` on the new fields. |
| R2 | **Visible colour change** for existing books (off-by-one between SQL `% 6 + 1` and Kotlin `floorMod`, or a palette resolved by list position after a reorder). | FF-M5. The pixel tests are unchanged. Manually upgrade a v1 install with books and compare the covers before and after. |
| R3 | **Triggers missing on upgraded installs** (migration skipped `createTriggers`, or the callback is not registered). | FF-M2 on a migrated file, not only a fresh DB. |
| R4 | **Loan history wiped by an update** (a `REPLACE`/`@Upsert` slipped in). | The `BookDaoTest` "update on a lent book keeps loans" case. Grep the diff for `REPLACE`/`@Upsert`. |
| R5 | **Unknown or corrupt `cover_color` crashes the Library.** | Mapper JVM test plus a raw-SQL `UPDATE books SET cover_color='bogus'` in an instrumented test, then `observeLibraryBooks` emits the fallback preset. |
| R6 | **Real v1 install fails to open after the update** (wrong version, missing migration, schema JSON regenerated over `1.json`). | Install a `main` build, add data through a debug seeder or App Inspection, install this branch over it, and check that the app opens with the data intact. FF-M3 checks that `1.json` exists, and the reviewer checks that its diff is empty. |
| R7 | **Memory text leaks into logs.** | Grep the diff for `Log.`/`println` with `Book`/`BookEntity`. |

## 11. Open items (not blocking this ticket)

- **Author is `NOT NULL` but epic D7 makes it optional.** #31 must either store `""` for "no author" (no migration; the View/grid render an empty author as absent) or add a migration that makes `author` nullable. The latter means rebuilding the `books` table on SQLite 3.22, with `loans` FK + trigger care. Recommendation for #31: `""` at the use case, consistent with S7's "absent = not shown" display rule. Not decided here because it is #31's scope and AC.
- **Cover image storage** (SAF persisted permission vs copy into app storage, and cleanup on delete for #34) is #31/#34's decision.
- **No ADR index exists**, and CLAUDE.md does not reference one, so none was created.
