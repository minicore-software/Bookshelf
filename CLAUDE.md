# CLAUDE.md

## AI Behavior Rules

**Always:**
- Provide clear, concise, technically accurate responses
- Follow required output schemas and formats
- Ask for clarification only when essential
- Provide actionable and specific insights when asked

**Never:**
- Invent system behavior or hallucinate design details not provided
- Produce speculative or fictional logs
- Output code invalid in Kotlin/Android contexts (unless explicitly asked)
- Provide harmful instructions or security-sensitive details

## Bug Workflow

Investigating and fixing a bug follows this procedure, not an ad-hoc patch:

1. Investigate and confirm the root cause first (reproduce, read logs/stack traces —
   don't guess).
2. Plan the change and write a short spec (what's broken, root cause, the fix,
   acceptance criteria, test requirement) under `docs/specs/<ticket>-<slug>.md`.
3. Create the bug ticket in the GitHub Project board (label `bug`) before any fix
   lands — the spec and ticket come before code changes.
4. Fix it with the `kotlin-android-developer` agent, then review/verify with the
   `qa-engineer` agent. The full `development-pipeline` (architect, UX spec stage,
   multi-round review loop) is not required for a bug fix — developer + QA is enough.
5. The ticket follows the board's normal status cycle (e.g. Backlog/Ready → In
   progress → In review → Done) like any other ticket.

## Build & Development Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew test --tests "com.minicore.bookshelf.ClassName"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

**App ID:** `com.minicore.bookshelf` | **compileSdk:** 37 (minor 2) | **minSdk:** 29 | **targetSdk:** 36

---

## Architecture

Layered MVVM + Repository, feature-first package structure:

```
app/src/main/java/com/minicore/bookshelf/
 ├── BookshelfApp.kt        # @HiltAndroidApp Application
 ├── MainActivity.kt        # @AndroidEntryPoint, hosts the NavHost
 ├── navigation/            # BookshelfDestinations (routes), BookshelfNavGraph (NavHost),
 │                          # per-destination nav-seam files (AddBookNavigation,
 │                          # BookDetailNavigation, EditBookNavigation - BOOKSHELF-0033 gave
 │                          # navigateToEditBook its real body and registered book/{bookId}/edit)
 ├── features/
 │    ├── library/
 │    │    ├── ui/
 │    │    │    ├── addbook/   # AddBookRoute/AddBookViewModel/AddBookUiState (the manual "add
 │    │    │    │              # a book" sheet, BOOKSHELF-0031), built on bookform/'s shared
 │    │    │    │              # chrome and rules (BOOKSHELF-0033 extraction)
 │    │    │    ├── bookdetail/ # BookDetailRoute/BookDetailViewModel/BookDetailUiState (the
 │    │    │    │               # read-only "Book" modal, reached only via navigation/'s
 │    │    │    │               # navigateToBookDetail - BOOKSHELF-0032)
 │    │    │    ├── bookform/  # Stateless BookForm + its field composables (StarRatingInput,
 │    │    │    │              # YearStepperField, CoverPicker, MemorySection) plus the shared
 │    │    │    │              # chrome/rules both forms are built on - BookFormSheet,
 │    │    │    │              # BookFormDiscardDialog, BookFormRules (BOOKSHELF-0033). Never
 │    │    │    │              # imports addbook/ or editbook/, so either screen can reuse it
 │    │    │    ├── editbook/  # EditBookRoute/EditBookViewModel/EditBookUiState, BorrowingSection
 │    │    │    │              # (the "edit a book" sheet stacked on Book Detail, reached only via
 │    │    │    │              # navigation/'s navigateToEditBook - BOOKSHELF-0033). The lent
 │    │    │    │              # indicator is read-only and live; this package never references a
 │    │    │    │              # Loan write path. Owns the "Remove book" confirm flow in-sheet
 │    │    │    │              # (BOOKSHELF-0034) via removebook/'s shared dialog
 │    │    │    └── removebook/ # RemoveBookDialog - the stateless "remove this book?" confirm
 │    │    │                    # dialog (open-loan warning, never blocking), shared by editbook/
 │    │    │                    # and the Library grid's future delete action (BOOKSHELF-0034/#35).
 │    │    │                    # Never imports editbook/ or bookform/
 │    │    ├── domain/     # LibraryRepository contract, read models (LibraryBook, LentBook,
 │    │    │               # LibraryCounts, OpenLoan), use cases (incl. AddBookUseCase,
 │    │    │               # UpdateBookUseCase, ObserveBookDetailUseCase, RemoveBookUseCase),
 │    │    │               # BookDraft (incl. CoverDraft.Stored), BookDetail/BookDetailLoan
 │    │    └── data/       # RoomLibraryRepository (entity <-> domain mapping)
 │    ├── lending/
 │    │    ├── ui/         # Lend-a-book sheet, My Loans screen
 │    │    ├── domain/
 │    │    └── data/
 │    ├── progress/
 │    │    ├── ui/         # Update-progress sheet, reading log
 │    │    ├── domain/
 │    │    └── data/
 │    ├── splash/
 │    │    └── ui/         # Two-stage cold-start splash (BookshelfSplashScreen)
 │    └── stats/
 │         └── ui/         # Reading insights (books/pages/streak/genres)
 ├── core/
 │    ├── model/            # Book, BookStatus, Cover, CoverPreset - pure Kotlin domain model shared by every feature
 │    ├── database/         # Room database, shared DAOs
 │    │    ├── entity/      # BookEntity, LoanEntity
 │    │    ├── converter/   # BookshelfConverters (Instant/LocalDate/BookStatus/CoverPreset <-> column types)
 │    │    ├── migration/   # BookshelfMigrations (one Migration object per schema version step)
 │    │    └── dao/         # BookDao, LoanDao, LibraryBookRow/StatusCountRow projections
 │    ├── designsystem/    # BookshelfTheme, palette, typography (Fraunces/paper/ink/forest)
 │    │    └── component/  # Shared composables used across features (e.g. BookCover)
 │    ├── coverimage/      # CoverImageStore/FileCoverImageStore (app-private cover-image files,
 │    │                    # ADR-003), CoverImageLoader/FileCoverImageLoader + LocalCoverImageLoader
 │    │                    # (BookCover's/CoverPicker's image-drawing seam, bound in MainActivity -
 │    │                    # see BOOKSHELF-0031)
 │    ├── scanner/         # ISBN barcode scanner (ML Kit)
 │    └── lookup/          # Book metadata lookup (title/author/ISBN search, future)
 └── di/                   # Hilt modules
```

**Data flow:** Compose UI → ViewModel (StateFlow) → Use Cases → Repository → Room DAO

Navigation Compose is used from the start. Initial routes: Library (home), Discover/Add, Loans, Stats, Settings, plus pushed screens (Book Detail, Update Progress, Lend Book).

---

## Domain Model

Four core entities, feature-first and normalized by ID (never by title — titles can change):

| Entity | Key fields |
|---|---|
| `Book` | id, title, author, isbn?, cover (`Cover.Image(uri)` XOR `Cover.Preset(CoverPreset)`, never both), genre?, pageCount?, publishedYear?, status (want_to_read / reading / finished), rating?, synopsis?, comment?, memoryWhy?, memoryWhere?, memoryWhat?, createdAt |
| `ReadingLogEntry` | id, bookId, currentPage, mood? (hooked / steady / slow), note?, recordedAt |
| `Loan` | id, bookId, borrowerName, borrowerContact?, loanedAt, dueAt, returnedAt?, remindEnabled, notes? |
| `Shelf` / `Tag` | id, name — user-defined groupings (future; not required for v1) |

**Book identity is ID-based, not name-based** — titles can change, and this matters once barcode lookup and loan history exist.

**`Cover.Image.uri`/the `cover_uri` column hold an app-private relative reference** (`covers/<uuid>.jpg` under `filesDir`), never the original picker URI — a downscaled, EXIF-stripped JPEG copy owned by `core/coverimage/CoverImageStore` (ADR-003, BOOKSHELF-0031), not a `content://` grant that can be revoked or a remote URL.

Relationships: `Book` → many `ReadingLogEntry` (progress-over-time); `Book` → many `Loan` (loan history; at most one open loan — `returnedAt == null` — per book at a time).

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** MVVM + Repository pattern
- **Local Storage:** Room
- **Async:** Coroutines + Flow
- **Dependency Injection:** Hilt (with KSP, not kapt)
- **Navigation:** Navigation Compose
- **Scanning:** CameraX + ML Kit Barcode Scanning, on-device ISBN lookup

It intentionally avoids enterprise-level overengineering while keeping a scalable, feature-first structure. No backend/auth, no monetization (ads/billing) or product flavors — Bookshelf is local-first, single-user, and free.

### Dependency versions

| Dependency | Version |
|---|---|
| AGP | 9.4.1 |
| Kotlin / Compose compiler plugin | 2.3.0 |
| KSP | 2.3.12 |
| Compose BOM | 2026.09.00 |
| Hilt | 2.60.1 |
| Hilt Android Testing | 2.60.1 |
| Room (incl. Room Testing) | 2.8.5 |
| Navigation Compose | 2.10.2 |
| Hilt Navigation Compose | 1.4.0 |
| Lifecycle ViewModel/Runtime Compose | 2.11.0 |
| Kotlin Coroutines | 1.11.0 |
| CameraX | 1.6.2 |
| ML Kit Barcode Scanning | 16.0.1 |
| Core KTX | 1.19.1 |
| AppCompat | 1.8.0 |
| Material | 1.14.0 |
| Core SplashScreen | 1.2.0 |
| JUnit 4 / AndroidX JUnit / Espresso | 4.13.2 / 1.3.0 / 3.7.0 |
| AndroidX Arch Core Testing | 2.2.0 |

`compileSdk` 37 (minor API level 2), `targetSdk` 36, `minSdk` 29, Java 11 source/target compatibility, `kotlin.code.style=official`, `room.schemaLocation` + `room.incremental` KSP args.

---

## Design Reference

> **Pending reconciliation:** [`docs/specs/Bookshelf-Visual-Spec.md`](docs/specs/Bookshelf-Visual-Spec.md) is the source of truth for colors, type, spacing and component styling going forward — it supersedes the palette/type tokens below, which are now stale. The `project/` canvas below still holds the current screen layouts and element organization; only its visual tokens are superseded. This section needs a proper rewrite once the two are reconciled.

The product design lives in [project/](project) as a Design canvas (`project/canvas.json` + one `.dc.html` per screen): Library, Book Detail, Discover & Add, Update Progress, Reading Stats, Settings, Lend a Book, My Loans. Treat it as the source of truth for layout, copy, and screen organization.

**Palette (stale, see above):** paper `#F3EEE3`, ink `#231F20`, forest (primary) `#2F4538`, terracotta (accent) `#C1693C`, gold (rating/finished) `#C9A227`.
**Type (stale, see above):** Fraunces (serif, titles/headings) + Inter (sans, UI/body).

---

## Documentation Conventions

All project documents other than `README.md`/`CLAUDE.md`/`CHANGELOG.md` live under [`docs/`](docs), never scattered at the repo root:

```
docs/
 ├── epics/   # epic brainstorming docs + their GitHub-ready rewrite (<Slug>-Epic.md, <Slug>-Epic-GitHub.md)
 ├── plans/   # implementation plans
 ├── specs/   # feature/UX specs
 └── adr/     # Architecture Decision Records
```

File naming: `<Slug>-<kind>.md` (e.g. `App-Bootstrap-Epic.md`, `App-Bootstrap-Epic-GitHub.md`). ADRs follow the folder's own numbering once one exists (`ADR-001-<Title-In-Dashes>.md` otherwise).

---

## Branching

Branches must be prefixed `BOOKSHELF-XXXX` — the GitHub issue number, zero-padded to 4 digits — before the slug:

```
<kind>/BOOKSHELF-<XXXX>-<slug>
```

Example: issue #7 → `feature/BOOKSHELF-0007-android-project-scaffold` (not `feature/7-android-project-scaffold`). `kind` is `feature` | `fix` | `chore` | `refactor` | `docs`, same as for commits. Commits keep using Conventional Commits as normal — this prefix is branch-naming only.
