# Plan: #34 Remove a book (confirm + cascade)

| Field | Value |
|---|---|
| Ticket | [#34](https://github.com/minicore-software/Bookshelf/issues/34), part of epic [#29](https://github.com/minicore-software/Bookshelf/issues/29) (Book Management). Depends on #30 (schema) and #33 (Edit sheet with the "Remove book" button). Both are merged. #35 (delete from the Library grid) reuses this ticket's dialog and use case. |
| Branch | `feature/BOOKSHELF-0034-remove-book-confirm-cascade` |
| Stack | Android: Kotlin, Jetpack Compose, Material 3, Navigation Compose 2.10.2, Hilt 2.60.1, Coroutines/Flow, Room 2.8.5. Single `app` module. No backend. |
| Agents | `kotlin-android-developer` builds it, `kotlin-android-reviewer` reviews it |
| Work split | FRONTEND-ONLY (single Android stack, one build round) |
| UX spec | **Not needed** (R9). The dialog is a two-variant `AlertDialog` built on the existing `BookFormDiscardDialog` pattern, and its styling follows the 2026-10-27 entry in `docs/ux/decisions.md`. The epic left the copy to whoever picks up #34 (`Book-Management-Epic-Tickets.md`), so this plan defines it (section 3.3). |
| API contract | There is no HTTP or event contract. The binding seams are the use case and repository (4.1, 4.2), the dialog composable (4.3), and the Edit state, intents and result (4.4, 4.5). |
| ADR | None. There is no schema change: `loans.book_id → books.id ON DELETE CASCADE` already exists (ADR-001, plan #18 S6). There is no new dependency and no external integration. Deleting the cover file after the row delete is the step ADR-003 already prescribes ("#34 must delete the file after the row delete"). |

---

## 1. Context

What the code already provides:

- `BookDao.deleteById(id): Int` exists. Nothing in production calls it. Plan #18 says it is "the future delete-book path".
- `LoanEntity` declares `ForeignKey(onDelete = CASCADE)` on `book_id`. Room turns on `PRAGMA foreign_keys`, and `LoanDaoOpenLoanTest` (FF1) asserts that. `BookDaoTest.deletingBook_cascadesToItsLoans_includingTheOpenOne` already proves the DAO-level cascade, open loan included.
- **There is no `ReadingLogEntry` table.** `BookshelfDatabase` has two entities, `books` and `loans`, at schema version 2. `ReadingLogEntry` exists only in CLAUDE.md's domain model and belongs to the future progress epic (Library epic D11). See R1.
- `CoverImageStore.delete(storedRef)` is idempotent, never throws, and its KDoc already says it is "#34's cleanup hook when a book is removed".
- `EditBookRoute(onDone, onRemoveClick: (bookId) -> Unit)`. The nav graph passes `onRemoveClick = {}` with a `// #34` comment (plan #33 E12). "Remove book" is enabled when `isLoaded && !isSaving`, and its text uses `colorScheme.error`.
- `EditBookViewModel` already observes `ObserveBookDetailUseCase(bookId)`, live over `books` and `loans`. `isLentOut = detail.loan != null` is already in state, and `detail.loan.borrowerName` is available. A `null` emission (row gone) emits `EditBookResult.NotFound` once and discards any image draft.
- `BookDetailRoute` closes itself on `NotFound` by popping `BookDetail.route` specifically. Plan #33 R10 covers "book removed while Edit is open".
- `BookFormDiscardDialog` is the existing confirm-dialog chrome: an M3 `AlertDialog`, a filled `colorScheme.error` confirm button and a `TextButton` dismiss. Its show flag lives in the route as `rememberSaveable`.

Epic decisions that apply: D14 (one confirm dialog shared by Edit's "Remove book" and the Library long-press/overflow from #35), D18 (always a second confirm step), D19 (cascade-delete the history; warn on an open loan but do not block).

## 2. Approach (summary)

1. Add a domain write path: `LibraryRepository.removeBook(id): Boolean` calls `bookDao.deleteById`. Loans cascade through the existing FK, in the same single `DELETE` statement. `RemoveBookUseCase(bookId): RemoveBookResult` reads the row, deletes it, then deletes the stored cover file.
2. Add one stateless, shared dialog, `features/library/ui/removebook/RemoveBookDialog`, with an optional open-loan warning. #35 reuses it unchanged.
3. Edit owns the confirm flow. The dialog's show flag lives in `EditBookRoute` (`rememberSaveable`, the same as Discard). `EditBookViewModel.onRemoveConfirmed()` runs the use case single-flight and emits a new `EditBookResult.Removed`. The nav graph pops Edit **and** Detail on `Removed`.
4. Drop the `onRemoveClick` route parameter from `EditBookRoute`. The nav graph's `{}` lambda goes away with it.

## 3. Decisions

| # | Decision | Why / rejected alternatives |
|---|---|---|
| R1 | **"Cascade to `ReadingLogEntry`" is satisfied by construction and guarded by a fitness function.** No `reading_log_entries` table exists, so there are no rows to delete. This ticket adds **FF-R1** (section 5): a JVM test over the latest exported Room schema JSON that fails if any foreign key referencing `books` has an `onDelete` other than `CASCADE`. When the progress epic adds `reading_log_entries.book_id`, it must declare `CASCADE` or this test goes red. The cascade AC is then tested against `loans`, the only child table. | **Rejected:** creating `ReadingLogEntry` now. That is a schema migration (v2 → v3) and an ADR-worthy data-model decision for a feature nobody has designed (no mood/page UX, no DAO consumer), and it expands this ticket's scope. **Rejected:** deleting children explicitly in the repository. It duplicates what the FK already guarantees, and a future table would have to remember to join that list (FF-R1 is a stronger guard). Flagged for the caller in the report as an AC interpretation, not a scope change. |
| R2 | **Cascade relies on Room `@ForeignKey(onDelete = CASCADE)`, not on a repository transaction.** `removeBook` is a single `DELETE FROM books WHERE id = ?`. SQLite runs the FK cascade inside that statement, so it is atomic without `@Transaction`. | This is the existing schema contract (ADR-001, plan #18 S6), and it works for every writer, including #35's. **Rejected:** a `@Transaction` that deletes `loans` and then `books` by hand. It would be redundant, it could drift from the schema, and it needs the open-loan triggers to stay out of the way (they only fire on insert/update, so they would, but it is pointless code). |
| R3 | **`RemoveBookUseCase` order: read → delete row → delete stored cover file.** (1) Read the current `Book` (`repository.observeBook(id).first()`). `null` → `NotFound`, no file touched. (2) `repository.removeBook(id)`. `false` → `NotFound`, no file touched. A throw propagates and no file is touched. (3) Only after success, if `cover is Cover.Image`, call `coverImages.delete(cover.uri)` (never throws). | ADR-003: a missed file delete leaves an orphan (wasted space). Deleting the file first could leave a live row pointing at a missing file. Read-then-delete is not one transaction. In theory a concurrent cover change between the two could orphan the new file, but Edit blocks Save while a remove is in flight (R6) and the app is single-user. **Rejected:** a `@Transaction` DAO method that returns the deleted row. That is more machinery for an orphan-file-only risk. |
| R4 | **The dialog is a new, stateless, shared composable, `features/library/ui/removebook/RemoveBookDialog`.** It does not reuse `BookFormDiscardDialog`. | It needs a separately addressable warning element (test tag, its own paragraph) and its own copy. It is also not form chrome: #35 shows it from the Library grid, where no form exists. Putting it in `removebook/` keeps `LibraryRoute` from importing `editbook/` (FF-E4). It mirrors `BookFormDiscardDialog`'s structure (M3 `AlertDialog`, filled `colorScheme.error` confirm button, `TextButton` dismiss), so the visual language is the same. |
| R5 | **Edit owns the confirm flow in-sheet. There is no separate navigation destination.** The show flag is `rememberSaveable` in `EditBookRoute` (it survives rotation, the same as Discard). The remove itself runs in `EditBookViewModel`. | Plan #33 E12 expected #34 to change only the nav-graph lambda, perhaps with a `book/{id}/remove` dialog destination. This plan deliberately deviates. (a) Only Edit's VM can discard an unsaved `CoverDraft.Image` deterministically. A nav destination that pops Edit from outside would clear Edit's VM before its cleanup runs, which leaks the draft file. (b) One VM can make Save and Remove mutually exclusive (R6). (c) The open-loan state is already live in Edit's state. #35 reuses the composable (R4) and the use case (R3) with its own small confirm state in `LibraryViewModel`. That is "same dialog, same behaviour" (D14) without a cross-screen VM. **Rejected:** the nav destination, for the draft-leak and save/remove race reasons above. |
| R6 | **Save and Remove are mutually exclusive and single-flight.** `EditBookUiState.isRemoving` is set synchronously in `onRemoveConfirmed()`, before any suspend point. While `isRemoving`: Save, Cancel, X, scrim, back and Remove are disabled or ignored, and form fields are disabled. `onRemoveConfirmed()` is a no-op when `!isLoaded`, `isSaving`, `isRemoving`, or after a result was emitted. `onSave()` and `onDismissRequest()` are no-ops while `isRemoving`. | This mirrors #33's single-flight save (F17). Without it, a Save that lands after the delete gets `NotFound`, and a double confirm calls the use case twice. |
| R7 | **A `null` book emission while `isRemoving` is suppressed.** `onBookDetail(null)` does not emit `NotFound` when a remove is in flight. The use case's own `Removed` result is the single outcome. | Room invalidation can deliver the `null` row before the use case's coroutine resumes. Without the suppression, the outcome would race between `NotFound` (pops Edit only, then Detail closes itself later, which flickers) and `Removed` (pops both at once). |
| R8 | **`EditBookResult.Removed` pops Edit and Detail together.** The nav graph does `popBackStack(BookDetail.route, inclusive = true)`. If that returns `false` (Detail is already gone because its own `NotFound` close won the race), it falls back to `popBackStack(EditBook.route, inclusive = true)`. The user lands on Library, whose flows already reflect the removal. No Snackbar (not in the AC; see Open points). | Plan #33's hand-off note says #34 "must pop **both** Edit and Detail on success". Popping Detail inclusive removes everything above it in one step, with no intermediate frame showing a dead Detail. The fallback guarantees Edit never stays open on a deleted book. |
| R9 | **No UX spec.** | It is a standard M3 confirm dialog that follows an existing in-app pattern (`BookFormDiscardDialog`) and an already-logged contrast decision. The only new elements are copy (section 3.3, the epic delegates it to this ticket) and one conditional paragraph. There is no new screen, flow or navigation pattern. The PO can edit the copy in review without touching the design. |
| R10 | **The warning names the borrower.** `EditBookUiState` gains `borrowerName: String?`, taken from the live `detail.loan?.borrowerName` and updated on every emission (never stored in `SavedStateHandle`). The dialog's book title comes from the **persisted** title (`initialDraft.title`), not the field the user may be typing in. | "Specific warning" (AC2): naming who holds the book makes the consequence concrete. The data is already in the flow. The borrower name is already shown on Detail, so this exposes nothing new. The persisted title is what is actually being removed. |

### 3.3 Dialog copy (new strings, `remove_book_*`, shared by #34 and #35)

| Key | Text |
|---|---|
| `remove_book_title` | `Remove “%1$s”?` |
| `remove_book_body` | `This permanently removes the book and its history from your shelf. This can\'t be undone.` |
| `remove_book_open_loan_warning` | `%1$s still has this book. Removing it also deletes the loan record.` |
| `remove_book_confirm` | `Remove` |
| `remove_book_cancel` | `Cancel` |
| `edit_book_remove_failed` | `Couldn\'t remove this book. Try again.` |

Layout: title, then the body paragraph, then (open loan only) the warning paragraph in `colorScheme.error` text with test tag `remove_book_open_loan_warning`. The warning colour uses the `error`/`overdue` token, not literal terracotta (`docs/ux/decisions.md` 2026-10-27, AA contrast). Confirm is a filled `colorScheme.error` button and dismiss is a `TextButton`, the same as Discard. The whole dialog has test tag `remove_book_dialog`.

## 4. Seams (binding for the developer; report any deviation)

### 4.1 Repository and DAO

```kotlin
// LibraryRepository (domain)
/** Deletes the book row; its loans go with it via the FK cascade (ADR-001). `false` when the id does not exist. */
suspend fun removeBook(id: Long): Boolean

// RoomLibraryRepository
override suspend fun removeBook(id: Long): Boolean = bookDao.deleteById(id) != 0
```

`BookDao` does not change. Every `LibraryRepository` fake in `test/` and `androidTest/` gets the method (default: count calls, return `true`).

### 4.2 Use case (`features/library/domain/RemoveBookUseCase.kt`)

```kotlin
sealed interface RemoveBookResult {
    data object Removed : RemoveBookResult
    data object NotFound : RemoveBookResult
}

class RemoveBookUseCase @Inject constructor(
    private val repository: LibraryRepository,
    private val coverImages: CoverImageStore,
) {
    suspend operator fun invoke(bookId: Long): RemoveBookResult  // order per R3
}
```

It never touches drafts. Draft cleanup belongs to the caller's UI state.

### 4.3 Dialog (`features/library/ui/removebook/RemoveBookDialog.kt`)

```kotlin
const val REMOVE_BOOK_DIALOG_TEST_TAG = "remove_book_dialog"
const val REMOVE_BOOK_OPEN_LOAN_WARNING_TEST_TAG = "remove_book_open_loan_warning"
const val REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG = "remove_book_confirm_button"
const val REMOVE_BOOK_CANCEL_BUTTON_TEST_TAG = "remove_book_cancel_button"

@Composable
fun RemoveBookDialog(
    bookTitle: String,
    borrowerName: String?,      // non-null => open-loan warning shown
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,      // Cancel button, back, outside tap
)
```

It is stateless and has no ViewModel. `onDismissRequest = onDismiss`.

### 4.4 Edit state and intents

- `EditBookUiState` gains `isRemoving: Boolean = false` and `borrowerName: String? = null`. `isLentOut` stays (BorrowingSection uses it). Set both from the same emission.
- The `enabled` gate for the form, cover controls, Cancel and Remove becomes `isLoaded && !isSaving && !isRemoving`. `canSave` also requires `!isRemoving`. `BackHandler(enabled = !isSaving && !isRemoving)`. `requestDismiss()` returns early while `isRemoving`.
- `EditBookError` gains `RemoveFailed`. It shows in the existing `errorSlot` with `edit_book_remove_failed` (polite live region). The next edit or save/remove attempt clears it.
- New intent `onRemoveConfirmed()`:
  1. Guard (R6). Set `isRemoving = true, error = null` synchronously.
  2. `when (removeBookUseCase(bookId))`:
     - `Removed`: if the current cover is a `CoverDraft.Image`, call `discardDraft(draftRef)`. Then emit `EditBookResult.Removed`. A `CoverDraft.Stored` is never touched by the VM; the use case already deleted it as the row's cover.
     - `NotFound`: emit `NotFound` (it already discards the image draft).
  3. On `CancellationException`, rethrow. On any other `Exception`, set `isRemoving = false, error = RemoveFailed` and emit nothing.
- `onBookDetail(null)` while `isRemoving`: ignore it (R7).

### 4.5 Route and navigation

- `EditBookRoute(onDone: (EditBookResult) -> Unit, viewModel = hiltViewModel())`. The `onRemoveClick` parameter is **removed**.
- `var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }`. Tapping "Remove book" sets it to `true`. `RemoveBookDialog(bookTitle = uiState.initialDraft?.title ?: uiState.title, borrowerName = uiState.borrowerName, onConfirm = { showRemoveConfirm = false; viewModel.onRemoveConfirmed() }, onDismiss = { showRemoveConfirm = false })`.
- `EditBookResult` gains `data object Removed`.
- `BookshelfNavGraph`, Edit destination: `onDone = { result -> if (result == EditBookResult.Removed) { if (!navController.popBackStack(BookDetail.route, true)) navController.popBackStack(EditBook.route, true) } else navController.popBackStack(EditBook.route, true) }` (R8). The `onRemoveClick = {}` line is deleted.

## 5. Quality attributes and fitness functions

| Attribute | How the design serves it | Fitness function |
|---|---|---|
| **Data integrity** | DB-level FK cascade (R2). File deleted only after the row delete (R3). Single-flight, mutually exclusive save/remove (R6). | **FF-R1** (JVM, file-reading like FF-M3): parse the latest `app/schemas/com.minicore.bookshelf.core.database.BookshelfDatabase/<N>.json` (N = `@Database` version) and assert that every `foreignKeys` entry whose `table == "books"` has `onDelete == "CASCADE"`. Today that is the `loans` FK. It fails as soon as a future child table (for example `reading_log_entries`) forgets the cascade. **FF-R2** (JVM, `RemoveBookUseCaseTest`): `delete(storedRef)` is called only after `removeBook` returns `true`, never on `NotFound` or a throw, and never for a `Cover.Preset`. |
| **Safety against accidental loss** | An explicit confirm (D18). Dismissing by every route is a no-op (AC4). | Compose tests for Cancel, back and outside-tap dismiss (section 6, AC4). |
| **Evolvability (reuse by #35)** | A stateless dialog in `removebook/` and a domain use case that does not depend on Edit. | **FF-R3** (JVM, extend `EditBookSeamTest`/FF-E4 style): `ui/removebook/` imports neither `ui.editbook` nor `ui.bookform`. `LibraryRoute.kt`/`LibraryScreen.kt` still do not import `ui.editbook`. |
| **Accessibility** | A standard M3 `AlertDialog`: TalkBack announces the title, focus is trapped, and back dismisses it. The warning is plain text inside the dialog content, so it is read in order. Buttons are at least 48dp. Contrast tokens follow the logged decision. | Existing a11y patterns. Add one Compose test that the warning text is in the merged semantics of the dialog content when there is an open loan. |

## 6. Test strategy (per AC)

| AC | Level | Tests |
|---|---|---|
| AC1: tapping "Remove book" opens the confirm dialog | Compose (`EditBookRouteTest`, fake repo) | Tap `edit_book_remove_button` → `remove_book_dialog` is displayed with the persisted title (type a new title first to prove the dialog shows the *persisted* one). The sheet is still open and `removeCallCount == 0`. The Remove button is disabled while loading and while saving. |
| AC2: open-loan warning shown vs. not shown, and does not block | Compose + JVM | Book without a loan → `remove_book_open_loan_warning` does not exist. Book with an open loan (fake `byId` with `OpenLoan`) → the warning is displayed with the borrower's name, Confirm is enabled, and confirming calls `removeBook` once. VM test: `borrowerName` follows live emissions (a loan opens or closes → the field flips). |
| AC3: cascade deletes `Book` + `Loan` (+ `ReadingLogEntry`, see R1) | Instrumented (`RoomLibraryRepositoryTest`, real DB) + JVM | `removeBook(A)` where A has one returned and one open loan → `true`. A's row and **both** loans are gone (`findOpenLoan(A) == null`, a raw `SELECT COUNT(*) FROM loans WHERE book_id = A` is 0). Book B and B's loan are untouched. `observeCounts().lentOut` drops accordingly. `removeBook(unknownId)` → `false`. **FF-R1** covers the reading-log part. **FF-R2** for file ordering. |
| AC4: Cancel/dismiss is a no-op | Compose (fake repo) + E2E | For each of Cancel, system back and outside tap: the dialog closes, the Edit sheet is still open with the user's unsaved edits intact, `removeCallCount == 0`, and `discardDraft`/`delete` were never called. E2E: the row and its loans are still in the DB. |
| AC5: tests listed in the ticket | (above) | |
| Flow end to end | Instrumented (`EditBookEndToEndTest`, Hilt + real DB) | Library → card → Detail → pencil → Remove → confirm → Library is visible, Detail and Edit are gone, the card is gone from the grid and the "Lent out" row, and the stored cover file under `files/covers/` is deleted. |
| VM behaviour | JVM (`EditBookViewModelTest`) | Two `onRemoveConfirmed()` calls in one frame → one use case call. `onSave()`/`onDismissRequest()` during remove → no-op. A `null` emission during remove → only `Removed` is emitted, never `NotFound` (R7). An unsaved `CoverDraft.Image` → `discardDraft` on `Removed`. `CoverDraft.Stored` → the VM makes no file call. Use case throws → `RemoveFailed`, `isRemoving == false`, no result emitted, and a retry works. |
| Navigation | JVM/instrumented nav test (`BookshelfNavGraphTest`) | `Removed` with Detail below Edit → back stack is `[Home]`. `Removed` when Detail is already popped → Edit is popped too (fallback). `Saved`/`Cancelled`/`NotFound` behave as before. |

## 7. Security and privacy notes

- There is no new entry point outside the app. The only input is a `Long` id that comes from the nav argument already validated by #33. The delete is parameterised Room SQL, so there is no injection surface.
- Cover file deletion goes through `CoverImageStore.delete`, which rejects any ref that does not match `^covers/<uuid>\.jpg$` (no path traversal, even with a corrupt `cover_uri`).
- The borrower name (PII) is rendered in the dialog only. It must not be logged, not be put in `SavedStateHandle`, and not appear in exception messages. `BookLoggingTest` shows the project already cares about this.
- The delete is irreversible. There is no undo and no backup of the deleted rows, by design (D19, ADR-001 "Bad" consequence). The confirm step is the only safeguard, so dismiss must truly be a no-op (AC4 tests).

## 8. Task list (single `kotlin-android-developer` round, in build order)

1. **Domain and data:** `LibraryRepository.removeBook`, `RoomLibraryRepository.removeBook`, `RemoveBookUseCase` + `RemoveBookResult`. Update every `LibraryRepository` fake. Tests: `RemoveBookUseCaseTest` (FF-R2), the `RoomLibraryRepositoryTest` cascade case. (AC3)
2. **Fitness function:** FF-R1 schema-JSON cascade test. (AC3, R1)
3. **Dialog:** `ui/removebook/RemoveBookDialog.kt`, the `remove_book_*` strings (section 3.3), and previews for both variants. Add FF-R3 to the seam/layering tests. (AC1, AC2)
4. **Edit VM:** `isRemoving`, `borrowerName`, `RemoveFailed`, `onRemoveConfirmed()`, the R6 guards on save/dismiss/remove, R7 suppression, and `EditBookResult.Removed`. Add the `EditBookViewModelTest` cases. (AC2, AC3, AC4)
5. **Edit route and nav:** remove the `onRemoveClick` parameter, add the `rememberSaveable` show flag and the dialog wiring, the `enabled`/`BackHandler` gates, and the error slot for `RemoveFailed`. Update the nav graph `onDone` (R8) and delete `onRemoveClick = {}`. Update the `EditBookRouteTest` call sites and add the AC1/AC2/AC4 Compose tests. (AC1, AC2, AC4)
6. **End to end:** `EditBookEndToEndTest` remove flow (cascade + cover file + back stack) and the nav back-stack tests. (AC3, AC4)
7. **Docs:** CHANGELOG `Added` line under `[Unreleased]` (at the end, per delivery-conventions §3). Update the `EditBookRoute` KDoc (no longer "a no-op wired by the nav graph"). CLAUDE.md needs no change (`removebook/` is inside `features/library/ui/`, which CLAUDE.md does not enumerate at that depth).

## 9. Risks (for QA to probe)

| # | Risk | Probe |
|---|---|---|
| K1 | Cascade misses rows: FK pragma off on some open path, or a future child table without `CASCADE`. | AC3 instrumented test on a file-backed DB opened through `applyBookshelfConfig()`, plus FF-R1. |
| K2 | Remove and Save race: a double tap on Confirm, or Save tapped right before Remove. | Rapid taps. Remove during an in-flight save (gate the fake). Exactly one terminal result, no crash. |
| K3 | Back-stack bugs after removal: Detail left showing a dead book, Edit left open, or Library popped. | Remove from Edit (Detail below). Rotate with the dialog open, then confirm. Process death with the dialog open (the dialog should restore, and confirming still works). |
| K4 | Orphaned files: the stored cover is not deleted, or an unsaved image draft leaks. | Book with an image cover → remove → `files/covers/` has no file for it. Pick a new image in Edit without saving → remove → `cache/cover-drafts/` is empty. |
| K5 | The warning is stale: a loan opens or closes while the dialog is up. | Not reachable through the UI today (there is no lending UI). Covered by the VM live-emission test. |
| K6 | Dismiss is not a no-op: an outside tap or back confirms, or closes the Edit sheet. | AC4 tests for all three dismiss routes, with unsaved edits still present afterwards. |
| K7 | Long titles or borrower names, and a large font scale, overflow the dialog. | 2x font, 320dp width, a 200-character title. The `AlertDialog` text must scroll or wrap, and the buttons must stay visible. |
| K8 | `RemoveFailed` path leaves the sheet stuck disabled. | Fake repo throws → error shown, every control is enabled again, and a retry succeeds. |

## 10. Open points (non-blocking)

- **AC interpretation (R1):** the ticket's "`ReadingLogEntry` rows" clause has no table to act on yet. This plan meets it with FF-R1 instead of creating the table. If the PO wants the table now, that is a separate schema ticket with its own ADR.
- **Post-removal feedback:** there is no "Book removed" Snackbar or undo. The AC does not ask for one. A Snackbar could reuse the `KEY_BOOK_ADDED` savedStateHandle pattern later. Undo would need soft-delete (a schema change and an ADR).
- **#35 hand-off:** reuse `RemoveBookDialog` and `RemoveBookUseCase`. `LibraryViewModel` holds a `pendingRemoval: (bookId, title, borrowerName?)` (title and `openLoan.borrowerName` are already on `LibraryBook`) and applies the same single-flight rule as R6. Removing a `CoverDraft` is not relevant there.
