# Plan: #32 View a book's details

| Field | Value |
|---|---|
| Ticket | [#32](https://github.com/minicore-software/Bookshelf/issues/32), part of epic [#29](https://github.com/minicore-software/Bookshelf/issues/29) (Book Management). Depends on #30 (schema, merged) and #31 (Add a book, merged into `main`). |
| Branch | `feature/BOOKSHELF-0032-view-book-details` (based on `main`) |
| Stack | Android: Kotlin, Jetpack Compose, Material 3, Navigation Compose 2.10.2, Hilt 2.60.1, Coroutines/Flow, Room 2.8.5. Single `app` module. No backend. |
| Agents | `frontend-developer-android` builds it, `frontend-reviewer-android` reviews it |
| Work split | FRONTEND-ONLY |
| UX spec | **Not needed** (V9). Layout and copy come from the wireframe `project/wireframe_images/Show_Book_Screen.png` (epic D5) and Visual Spec §5.10/§5.12. The few gaps are decided in section 3 of this plan. |
| API contract | No HTTP or event contract. The binding seams are the **route and navigation seams (4.1)**, the **read query and use case (4.2)** and the **UI state (4.3)**. |
| ADR | None. The ticket only adds a read query and a screen. No schema change, framework change or external integration. |
| Budget | About 3 h for the whole pipeline. The design is kept small on purpose. |

---

## 1. Context

Tapping a Library card (grid, "Currently reading" row or "Lent out" row) calls `onBookClick(bookId)`. Today `BookshelfNavGraph` wires that to `{}`. This ticket adds a read-only "Book" modal and opens it from that callback.

What the code already provides:
- `Book` (core/model) already has `comment`, `memoryWhy`, `memoryWhere`, `memoryWhat` and `cover` (from #30). Blank values are stored as `null` (`LibraryMappers.blankToNull`). No `ReadingLogEntry` is needed: the Memory section lives on `Book`.
- An open loan is read with one pattern only: `LEFT JOIN loans l ON l.book_id = b.id AND l.returned_at IS NULL` into `LibraryBookRow`, mapped to `LibraryBook(book, openLoan: OpenLoan?)` (`BookDao.observeLibraryBooks`). ADR-001's triggers guarantee at most one row.
- `AddBookManual` is a `dialog(...)` destination with sheet chrome. It is reached only through `NavController.navigateToAddBook()` (the #31 A2 seam, guarded by `AddBookSeamTest`).
- `LoanDueStatus.of(dueAt, today)`, `CurrentDateProvider` and the public `loanDueCaption(status)` composable already format a due date the way the Library's lent row shows it.

Epic decisions that apply: D5 (modal contents), D8 (an unrated book shows 5 empty outline stars), D16 (2-state dot derived from the open loan, not `Book.status`), D17 ("Lend out" is a no-op stub), D2 (the old `project/BookDetail.dc.html` canvas is **superseded** by the wireframe, so do not build from it).

## 2. Key decisions

| # | Decision | Why / alternatives |
|---|---|---|
| V1 | **Route `book/{bookId}`** (`bookId`: `NavType.LongType`), registered as a `dialog(...)` destination with the same `DialogProperties` chrome style as `AddBookManual`. Only the id is passed. | ID-based identity (CLAUDE.md). A modal per D5 and Visual Spec §5.10 ("one sheet hosts every view"). **Rejected:** a full-screen `composable` push (the wireframe is a modal, and Add is already a dialog sheet). **Rejected:** passing the `Book` in the route or saved state (stale data, and it breaks on process death). The route is named so that `book/{bookId}/edit` (#33) becomes a sibling. |
| V2 | **One navigation seam per destination: `NavController.navigateToBookDetail(bookId)`** in `navigation/BookDetailNavigation.kt`, with `launchSingleTop = true`. `BookshelfDestinations.BookDetail` is referenced only from `navigation/`. | Same pattern as #31 A2. A fast double tap on a card does not stack two modals. |
| V3 | **New read query `BookDao.observeLibraryBook(id): Flow<LibraryBookRow?>`**: the existing `observeLibraryBooks` SELECT/JOIN with `WHERE b.id = :id`. Repository: `LibraryRepository.observeLibraryBook(id): Flow<LibraryBook?>` maps it with the existing `toLibraryBook()`. | It reuses the one open-loan pattern, projection and mapper. Room tracks both `books` and `loans`, so the modal updates live when a loan opens or closes. **Rejected:** `combine(observeBook(id), loanDao.findOpenLoan)`. `findOpenLoan` is `suspend`, not observed, and it adds a second way to compute "lent". **Rejected:** filtering `observeBooks(null)` in memory (loads the whole library for one row). |
| V4 | **`ObserveBookDetailUseCase(bookId): Flow<BookDetail?>`** combines `repository.observeLibraryBook(id)` with `CurrentDateProvider.observe()`. It produces `BookDetail(book, loan: BookDetailLoan?)` where `BookDetailLoan(borrowerName, dueStatus: LoanDueStatus)`. | Follows CLAUDE.md's flow (ViewModel → use case → repository) and mirrors `ObserveLibraryRowsUseCase`, so overdue is derived per render date and never stored (repository KDoc). |
| V5 | **Availability is `loan != null`. `Book.status` is never read for the dot** (D16). | The single source of the "lent" overlay (epic #17 D10). |
| V6 | **Edit: affordance plus seam, not a screen.** The pencil "Edit" button calls `onEditClick(bookId)`. The NavGraph wires it to `navController.navigateToEditBook(bookId)` in `navigation/EditBookNavigation.kt`. **In this ticket its body is a documented no-op** (`// #33 registers book/{bookId}/edit and navigates here`). No `EditBook` route and no placeholder screen are registered. | #33 depends on #32 and owns the Edit screen. So AC "Edit opens Edit book, pre-filled" is **completed and verified by #33**. This ticket's testable part: the button is present, enabled, passes the correct `bookId` through the seam and does not crash. **Rejected:** registering a placeholder "Coming soon" destination (throwaway UI that #33 deletes). **Rejected:** navigating to an unregistered route (`IllegalArgumentException`, crash). #33 changes only the seam's body and the NavGraph. The detail screen does not change. Pre-fill works through the id: #33's ViewModel loads the book by `bookId` from `SavedStateHandle`, the same way as V8. |
| V7 | **"Lend out" is a no-op `onLendClick` that the NavGraph wires to `{}`** (D17). The button stays enabled, as the wireframe shows it. | The AC requires that tapping it does not crash or navigate. The Lend Book epic changes only the NavGraph lambda. |
| V8 | **The ViewModel reads `bookId` from `SavedStateHandle`.** State: `Loading` → `Loaded(BookDetailUi)` / `NotFound`. On `NotFound` the route calls `onClose()` **once** (one-shot), so the modal closes itself. | It survives process death. `NotFound` covers a book deleted while the modal is open (#34/#35 later) and a stale back-stack id. **Rejected:** showing an error UI for a vanished book (nothing to act on). |
| V9 | **No UX spec.** | The wireframe and Visual Spec §5.12 fix layout, copy and states. The remaining gaps are small presentation rules, decided in section 3. |
| V10 | **Package `features/library/ui/bookdetail/`** (`BookDetailRoute`, `BookDetailViewModel`, `BookDetailUiState`). Domain types go in `features/library/domain/` (`ObserveBookDetailUseCase.kt`, `model/BookDetail.kt`). | Same layout as `addbook/`. The Library UI does not import `bookdetail/`. |

## 3. Presentation rules (fill the wireframe's gaps; binding)

Source of truth: `project/wireframe_images/Show_Book_Screen.png` and Visual Spec §5.12. When the two disagree, the ticket AC wins.

1. **Header:** title "Book" and a ✕ close button. **Footer:** text button "Close" and a secondary "Edit" button with the pencil icon, right-aligned. Close, ✕, system back and scrim tap all close the modal (`popBackStack`). Nothing can be lost because the modal is read-only, so there is no confirm.
2. **Cover:** `BookCover(variant = BookCoverVariant.Row, dimmed = false)`. Do not add a new cover variant.
3. **Author · year line:** both present → `Author · 1939`. Author blank (D7 allows it) → year only. Year null → author only. Both missing → hide the line.
4. **Rating:** 5 stars, read-only. `rating == null` → 5 empty outline stars (D8). TalkBack reads "Rated N of 5" or "Not rated". Reuse the star drawing from `bookform/StarRatingInput` only if it can be used in read-only mode without changing its behaviour. Otherwise use a small private display composable.
5. **Status pill (2 states, D16):** a dot plus an uppercase label. Copy is **"On the shelf" / "Lent out"**. This follows the ticket AC and the Library's "Lent out" wording, not the Visual Spec's "On loan". Colours: use existing tokens only. "On the shelf" uses a forest/status-family token. "Lent out" uses `lentAccent`, the same accent as the Library's lent overlay. Do not add new colour tokens (the Visual Spec reconciliation is pending, see CLAUDE.md).
6. **Comment block:** label "COMMENT". The text is wrapped in curly quotes (“…”) and set italic. **When `comment == null`, hide the whole block.** The AC asks for a quoted comment, and there is nothing to quote.
7. **Availability block:**
   - Not lent: label "ON THE SHELF", text "Lend it to a friend?" and a "Lend out" button (V7).
   - Lent: label "LENT TO", the borrower's name, and one caption line from the existing `loanDueCaption(dueStatus)` (overdue uses the existing overdue style). **No "Lend out" button and no "Mark returned" button.** A second open loan is impossible (ADR-001), and this epic writes no `Loan` rows (D13/D17).
8. **Memory section:** heading "MEMORY", then three cells labelled "WHY", "WHERE" and "FEELING" (from `memoryWhy`, `memoryWhere`, `memoryWhat`). A null value shows `— not yet written —` in non-italic, faint ink (Visual Spec §2.3). The cells stack in one column on phones. Use the label glyphs (❦ ✿ ✸) only if the bundled fonts render them; otherwise drop them. They are decorative and should be hidden from TalkBack. TalkBack reads an empty cell as "Why, not yet written", without the dashes.
9. **Scroll:** the body scrolls, and the footer stays visible.
10. All copy goes in `strings.xml` (`book_detail_*`).

## 4. Contract (binding)

### 4.1 Navigation

```kotlin
// BookshelfDestinations
data object BookDetail : BookshelfDestinations("book/{bookId}") {
    const val ARG_BOOK_ID = "bookId"
    fun route(bookId: Long) = "book/$bookId"
}

// navigation/BookDetailNavigation.kt
fun NavController.navigateToBookDetail(bookId: Long)   // navigate(route(bookId)) { launchSingleTop = true }

// navigation/EditBookNavigation.kt
fun NavController.navigateToEditBook(bookId: Long)     // no-op until #33 (V6)

// BookDetailRoute
@Composable fun BookDetailRoute(
    onClose: () -> Unit,
    onEditClick: (bookId: Long) -> Unit,
    onLendClick: (bookId: Long) -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel(),
)
```

NavGraph wiring: `LibraryRoute(onBookClick = { navController.navigateToBookDetail(it) })`. In the dialog: `onClose = { navController.popBackStack() }`, `onEditClick = { navController.navigateToEditBook(it) }`, `onLendClick = {}`.

### 4.2 Read path

```kotlin
// BookDao: same SELECT list and LEFT JOIN as observeLibraryBooks, WHERE b.id = :id
fun observeLibraryBook(id: Long): Flow<LibraryBookRow?>

// LibraryRepository: cold, re-emits on books/loans changes, emits null when the id does not exist
fun observeLibraryBook(id: Long): Flow<LibraryBook?>

// domain
data class BookDetailLoan(val borrowerName: String, val dueStatus: LoanDueStatus)
data class BookDetail(val book: Book, val loan: BookDetailLoan?)   // loan != null <=> "Lent out"
class ObserveBookDetailUseCase { operator fun invoke(bookId: Long): Flow<BookDetail?> }
```

### 4.3 UI state

```kotlin
sealed interface BookDetailUiState {
    data object Loading : BookDetailUiState
    data class Loaded(val detail: BookDetail) : BookDetailUiState
    data object NotFound : BookDetailUiState
}
```

`StateFlow` via `stateIn(viewModelScope, WhileSubscribed(5_000), Loading)`. The presentation rules in section 3 are pure functions of `BookDetail`. Keep them as testable functions (for example `authorYearLine(author, year): String?`), not inline in composables.

## 5. Quality attributes and fitness functions

| Attribute | How the design serves it | Fitness function (cheap) |
|---|---|---|
| Correctness / consistency | "Lent" comes from the one LEFT JOIN pattern (V3/V5). The flow is live over `books` and `loans`. | DAO tests in section 7. |
| Evolvability | Seams for Detail, Edit and Lend. #33 and the Lend epic change only `navigation/`. | **FF-V1** (JVM, add to `AddBookSeamTest` or a sibling `BookDetailSeamTest`): `BookDetail` and `navigateToEditBook` are referenced only from `navigation/`, and `LibraryScreen.kt`/`LibraryRoute.kt` do not import `…ui.bookdetail`. |
| Read-only guarantee | The ViewModel depends only on `ObserveBookDetailUseCase`. | **FF-V2** (file scan): no file under `ui/bookdetail/` references `addBook`, `updateBook`, `LoanDao` or `BookDao`. |
| Accessibility | Rule 3.4/3.8 semantics. The modal title is a heading. Close and Edit have text labels. Touch targets are at least 48dp. | Compose UI test semantics assertions (section 7). |

## 6. Security and privacy (light threat model)

Local-only, single-user app with no new entry point outside the process. The modal is not exported and has no deep link: **do not add a `deepLinks` entry** to the route. That would let other apps open it with arbitrary ids. A non-numeric or unknown `bookId` leads to `NotFound`, which closes the modal (V8). The modal shows personal text (comment, memories, borrower name). **Do not log** `Book`, `BookDetail` or loan fields (the existing `BookLoggingTest` covers `Book`). The new `BookDetail`/`BookDetailLoan` types should not be logged either.

## 7. Test strategy

| AC | Level | Tests |
|---|---|---|
| AC1 data | DAO (androidTest, `BookDaoTest`) | `observeLibraryBook`: unknown id → `null`. No loan → `openLoanId == null`. Open loan → populated. Only a returned loan → `null` loan. **Re-emits** when a loan is inserted and when it is `markReturned`. |
| AC1 data | JVM unit | `ObserveBookDetailUseCase`: lent and not-lent mapping, `dueStatus` changes with `CurrentDateProvider`. `null` passes through. |
| AC1 rules | JVM unit | `authorYearLine` (4 cases), rating description (null / 1 / 5), comment hidden when null. The status uses the loan and ignores `Book.status`: a book with `Finished` status and no loan shows "On the shelf", and a `Reading` book with a loan shows "Lent out". |
| AC1 UI | Compose UI (androidTest) | A fully populated book shows every element. An empty book (title only) shows 5 empty stars, no comment block, three "— not yet written —" cells and no author line. A lent book shows "Lent out", "LENT TO {name}" and no Lend out button. |
| AC2 | Compose UI | Tapping "Lend out" invokes `onLendClick` and nothing else. The modal stays open with no crash. In the NavGraph test the current route is still `book/{bookId}`. |
| AC3 | JVM / NavGraph | Tapping Edit calls `onEditClick` with the **same `bookId`**. The NavGraph test shows no crash and the route unchanged (seam no-op, V6). **Pre-fill is verified in #33.** |
| AC4 | NavGraph (`BookshelfNavGraphTest`) | Card tap → route `book/{bookId}` with the right id. Close, ✕ and back → route `home`. A double tap on a card leaves one detail entry on the back stack. |
| V8 | JVM ViewModel | Repository emits `null` → `NotFound` → `onClose` called exactly once, also across recomposition. |

## 8. Work split and tasks (FRONTEND-ONLY, `frontend-developer-android`, in build order)

1. `BookDao.observeLibraryBook` + `LibraryRepository/RoomLibraryRepository.observeLibraryBook` + DAO tests. (AC1)
2. `BookDetail`, `BookDetailLoan`, `ObserveBookDetailUseCase` + unit tests. (AC1)
3. Destinations, `navigateToBookDetail`, `navigateToEditBook` (no-op seam), NavGraph `dialog` registration, Library `onBookClick` wiring, FF-V1, NavGraph tests. (AC3, AC4)
4. `BookDetailViewModel` + `BookDetailUiState` + the section 3 pure presentation functions + tests (including `NotFound` → close). (AC1)
5. `BookDetailRoute` UI per section 3, strings, accessibility, FF-V2, Compose UI tests. (AC1–AC4)
6. CLAUDE.md architecture tree: add `ui/bookdetail/` under `features/library/`, and update the CHANGELOG. (all)

## 9. Risks for QA to probe

- **Stale lent state:** lend or return a book while the modal is open (through a DB write in the test). The pill and block must flip without reopening.
- **Vanished book:** delete the row while the modal is open. It must close once, without a crash and without a close loop.
- **Process death / rotation** with the modal open: it restores to the same book (id from `SavedStateHandle`).
- **Double tap** on a card, or tapping a card in the "Lent out" row: one modal, correct book.
- **Long content:** a long title, a 2,000-char comment or long memories must scroll, and the footer must stay reachable. Large font scale (200%) must keep Close/Edit reachable.
- **Unrated vs 1-star** look different (D8).
- **Status ignores `Book.status`** (D16), as in the section 7 cases.
- The Edit and Lend out buttons must do nothing visible and must not crash. This is expected until #33 and the Lend epic.

## 10. Open items and follow-ups (not blocking)

- **#33** registers `book/{bookId}/edit`, replaces the body of `navigateToEditBook`, and decides whether Edit opens over the detail modal or replaces it. It also verifies this ticket's "pre-filled" AC.
- **Lend Book epic** replaces `onLendClick = {}` and adds "Mark returned" to the lent block (Visual Spec §5.12 loan callout).
- Visual Spec reconciliation (CLAUDE.md "Pending reconciliation") may later change the pill colours and fonts. This ticket uses existing tokens only.
- `project/BookDetail.dc.html` is stale per epic D2. Updating the canvas is out of band.
