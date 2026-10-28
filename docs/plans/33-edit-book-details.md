# Plan: #33 Edit a book's details

| Field | Value |
|---|---|
| Ticket | [#33](https://github.com/minicore-software/Bookshelf/issues/33), part of epic [#29](https://github.com/minicore-software/Bookshelf/issues/29) (Book Management). Depends on #30 (schema), #31 (Add form) and #32 (View, pencil entry point). All three are merged. |
| Branch | `feature/BOOKSHELF-0033-edit-book-details` |
| Stack | Android: Kotlin, Jetpack Compose, Material 3, Navigation Compose 2.10.2, Hilt 2.60.1, Coroutines/Flow, Room 2.8.5. Single `app` module. No backend. |
| Agents | `frontend-developer-android` builds it, `frontend-reviewer-android` reviews it |
| Work split | FRONTEND-ONLY |
| UX spec | **Not needed** (E13). Layout, copy and field order come from `project/wireframe_images/Edit_Book_Screen_Part_1.png` / `_Part_2.png` (epic D4), Visual Spec §5.10/§5.11, and #31's UX spec (`docs/specs/31-add-book-manually.md`) for every reused control. The few gaps are decided in section 3. |
| API contract | No HTTP or event contract. The binding seams are the **route and navigation seams (4.1)**, the **draft and use case (4.2, 4.3)** and the **UI state and intents (4.4)**. |
| ADR | None. No schema change (#30 already added the columns), no new dependency, no external integration. The cover-file lifecycle on edit is the one ADR-003 already prescribes ("#33 must delete a replaced file only after `updateBook` succeeds"). |

---

## 1. Context

What the code already provides:

- `Book` has `comment`, `memoryWhy`, `memoryWhere`, `memoryWhat` and `cover: Cover` (#30). `Book.toEntity()` maps blank optional text to `null` (`LibraryMappers.blankToNull`).
- `LibraryRepository.updateBook(book): Boolean` (`@Update` by id, `ABORT`, `false` when the id is gone, never touches `loans`). Nobody calls it yet.
- `ObserveBookDetailUseCase(bookId): Flow<BookDetail?>` gives the book plus its open loan (`loan != null` ⇔ lent out), live over `books` and `loans`, `null` when the id is gone (#32 V3/V4).
- `bookform/BookForm` is stateless and seeded by parameters; `bookform/` must not import `addbook/` (FF-A4) precisely so #33 can reuse it (#31 A3/A4).
- `BookDraft` / `CoverDraft` (`Preset` | `Image(draftRef)`) model an unsaved form. There is **no variant for "the book's already-stored image"** - Add never needed one. Edit does (E5).
- `CoverImageStore` already has `commit`, `uncommit`, `discardDraft`, `delete`, `exists`. `FileCoverImageLoader` resolves both `covers/…` and `cover-drafts/…` refs, so the existing `CoverPicker` thumbnail can draw a stored cover unchanged.
- The Edit seam exists: `navigation/EditBookNavigation.kt`'s `navigateToEditBook(bookId)` is a documented no-op; Book Detail's pencil button already calls it with the right id (#32 V6). `BookDetailSeamTest.navigateToEditBook_isReferencedOnlyFromNavigation` guards it. Book Detail's `onClose` already pops `BookDetail.route` specifically, anticipating Edit stacked on top.
- `AddBookRoute` holds the sheet chrome (scrim sibling, 92% height minus status bar, IME padding, pinned footer, `BackHandler`, discard confirm). That code went through three review rounds (scrim semantics, snackbar one-shot, IME), so it must not be copy-pasted (E9).

Epic decisions that apply: D4 (Edit = Add form pre-filled + Memory + Borrowing, footer "Remove book" / Cancel / "Save changes"), D7 (only Title required), D8 (unset rating ≠ 1 star), D9 (cover XOR), D13 (lent checkbox is read-only, no `Loan` writes), D14 (Remove's confirm belongs to #34), D15 (memory fields independently optional).

## 2. Key decisions

| # | Decision | Why / alternatives |
|---|---|---|
| E1 | **Route `book/{bookId}/edit`** (`bookId`: `LongType`), a `dialog(...)` destination with the same `DialogProperties` as `AddBookManual`/`BookDetail`. `navigateToEditBook(bookId)` gets its real body: `navigate(EditBook.route(bookId)) { launchSingleTop = true }`. No `deepLinks`. | Sibling of `book/{bookId}` as #32 V1 planned. `launchSingleTop` stops a double tap on the pencil from stacking two forms. |
| E2 | **Edit opens on top of Book Detail and returns to it.** Save, Cancel, ✕, back and scrim all pop `EditBook.route` (inclusive), never a bare `popBackStack()`. Detail is then visible again and already shows the saved values (its flow is live). | Wireframe flow is Detail → Edit (→ back to Detail). **Rejected:** replacing Detail (`popUpTo(BookDetail) inclusive`) and landing on the Library after save: the user loses the place they came from and gets no visible confirmation of the edit. Known cost: two stacked scrims make the background darker under Edit; accepted (QA visual check, R9). |
| E3 | **No post-save toast.** The positive signal is Detail re-rendering with the new values. | The Library's Snackbar host sits under the Detail dialog window, so a "Saved" message there would be hidden. Adding a Snackbar host inside Detail is new plumbing for a signal the user already sees. Visual Spec §5.14's "Saved" stays available for later. |
| E4 | **`BookDraft` gains `memoryWhy`, `memoryWhere`, `memoryWhat: String = ""`.** Defaults keep `BookDraft.Empty` and every Add call site unchanged. `AddBookUseCase` keeps writing `null` memories (Add has no Memory section, Visual Spec §5.11). | One draft type for both forms (#31 A3). **Rejected:** a separate `EditBookDraft` (duplicates five fields and the validation). |
| E5 | **`CoverDraft` gains `Stored(storedRef: String)`**: the book's existing, already-committed image. Rules: Edit seeds `Stored(uri)` from `Cover.Image(uri)`. No intent ever deletes or moves a `Stored` file; only a **successful** save may delete it (E7). `AddBookUseCase` and `AddBookViewModel` treat `Stored` as unreachable (`require`/`error`, programming error). `CoverPicker` renders `Stored` exactly like `Image` (thumbnail + Replace/Remove). | Without it the form cannot express "keep the current image", and mapping a stored ref into `Image(draftRef)` would make Cancel/Replace call `discardDraft` on a permanent file (the loader regexes would reject it, but intent-wise it is the wrong lifecycle). Sealed type keeps D9 by construction. **Rejected:** a nullable `originalCoverRef` side field with `Preset`/`Image` (two fields that must agree; easy to desync). |
| E6 | **`UpdateBookUseCase(bookId, draft): UpdateBookResult`** is the edit write-boundary. Same field rules as `AddBookUseCase` (A6) plus memories (trim, blank → `null`). It **re-reads the row at save time** and copies only the form's fields onto it; `id`, `status`, `isbn`, `genre`, `pageCount`, `synopsis`, `createdAt` come from the fresh row, never from the form. | Fields not on the form must survive an edit even if another flow (future progress/status updates) changed them while the sheet was open. **Rejected:** writing back the `Book` loaded when the sheet opened (silent overwrite of concurrent changes). **Rejected:** `@Query UPDATE` of only the form's columns (a new DAO method per field set; the read-then-`@Update` is enough for a single-user local app). |
| E7 | **Cover commit order on edit** (binding, ADR-003): (1) validate; (2) read the current book, `null` → `NotFound` with no file touched; (3) `Image(draft)` → `commit`; (4) `updateBook`; on throw or `false` → `uncommit` the new file, then rethrow / return `NotFound`; (5) only after success, if the old cover was `Image(oldRef)` and the new cover is not `Image(oldRef)` → `delete(oldRef)` (never throws). | A failed save leaves the old cover file intact and the draft retryable. A crash between (4) and (5) leaves an orphan file, accepted by ADR-003 ("wasted space, never a crash"). |
| E8 | **The lent indicator is live and read-only.** The ViewModel derives `isLentOut = detail.loan != null` from `ObserveBookDetailUseCase` on **every** emission (a loan opening or closing while the sheet is open flips it). There is no intent to change it, and nothing under `ui/editbook/` or in `UpdateBookUseCase` references `LoanDao`, `LoanEntity` or any loan write (FF-E3). | D13. Reuses #32's one open-loan read path (no second way to compute "lent"). |
| E9 | **Extract the shared sheet chrome and form rules into `bookform/`, and refactor Add onto them:** `BookFormSheet` (scrim, sizing, header with ✕, scrolling body slot, error slot, pinned footer slot, `paneTitle`), `BookFormDiscardDialog` (copy passed in), and `BookFormRules` (pure: year-text filter, year step with clock seed and clamp, rating toggle, `publishedYear` parse). Test tags are passed in so Add keeps its existing `add_book_*` tags and every #31 test stays green unmodified. | The chrome encodes three review rounds of fixes (scrim semantics order, IME + pinned footer, 92% minus status bar). Two copies would drift. **Rejected:** copy-paste into `editbook/` (drift, double maintenance). **Rejected:** one `BookFormViewModel` with an add/edit mode flag (cover lifecycle and save differ enough that a mode flag spreads `if (edit)` through every intent). The refactor is behaviour-preserving; #31's JVM, Compose and pixel tests are its safety net and must pass unchanged. |
| E10 | **Pre-fill once, then never overwrite.** The first non-null `BookDetail` seeds the form and an `initialDraft`; later emissions only update `isLentOut` (and `NotFound` on `null`). After process death, fields restore from `SavedStateHandle` (a `seeded` flag says so) and `initialDraft` is re-derived from the row. | Re-seeding on every Room emission would wipe what the user is typing. The row cannot change under the sheet except through this sheet, so re-deriving `initialDraft` is safe. |
| E11 | **Dirty = `toDraft() != initialDraft`.** A dirty dismiss shows the discard confirm; a clean one closes at once (same policy as #31's UX decision, docs/ux/decisions.md 2026-10-27). "Save changes" is enabled whenever the form is loaded, the title is not blank, and no save/import is in flight - **not** gated on dirty. A clean save writes the same row and closes. | Matches the wireframe (Save shown enabled) and avoids a "why is Save grey?" moment. The extra no-op write is harmless. |
| E12 | **"Remove book" is a visible, enabled, destructive-styled, left-aligned no-op** (`onRemoveClick(bookId)` wired to `{}` in the NavGraph with a `// #34 wires the confirm + cascade` comment). Disabled while saving. | AC says it is present and #34 owns the handler. Wiring through a route parameter means #34 changes only the NavGraph lambda (plus its own dialog), not this screen. |
| E13 | **No UX spec.** | Every input control (text fields, year stepper, star rating, cover picker), the sheet, the dismiss policy, error states and TalkBack semantics are already specified by #31's spec and reused verbatim. The wireframe fixes the two new sections, their copy and placeholders. The only genuinely new interaction is the read-only checkbox; its behaviour is fixed by AC/D13 and its semantics are decided in section 3.5. This is the same call #32 made (V9). |
| E14 | **Package layout:** `features/library/ui/editbook/` (`EditBookRoute`, `EditBookViewModel`, `EditBookUiState`, `BorrowingSection`), `features/library/ui/bookform/` (+ `MemorySection`, `BookFormSheet`, `BookFormDiscardDialog`, `BookFormRules`), `features/library/domain/UpdateBookUseCase.kt`. | Borrowing is edit-only (Visual Spec §5.11), so it lives in `editbook/`. Memory may later appear on Add, so it lives in `bookform/`. |

## 3. Presentation rules (fill the wireframe's gaps; binding)

Source of truth: the two Edit wireframes, then Visual Spec §5.10/§5.11, then #31's UX spec for every reused control. When they disagree, the ticket AC wins.

1. **Sheet:** `BookFormSheet`, title **"Edit book"**, ✕ close. Body order: `BookForm` (Book section + Cover, unchanged) → `MemorySection` → `BorrowingSection`. Field labels follow `BookForm`'s existing in-field label style, not the wireframe's external uppercase labels (consistency with Add; Visual Spec reconciliation is pending).
2. **Loading:** until the first emission, the body shows a centred progress indicator; Save and Remove are disabled; dismiss works and closes immediately (nothing to lose). Room makes this a single frame in practice.
3. **Memory section:** heading **"Memory"** (same section-heading style and `heading()` semantics as BookForm's "Book"/"Cover" headings) followed by the hint **"optional · why, where, what you were going through"** (small, `onSurfaceVariant`). Three multiline fields, each `minLines = 3`, `maxLines = 8`, no length cap (epic: none added):
   - **"Why did I read this?"** - placeholder "Recommended by…"
   - **"Where was I?"** - placeholder "On the train to Edinburgh, the rainy week…"
   - **"What was I going through?"** - placeholder "Restless, mostly. Tea kept going cold."
   Each is independent (D15). The label is the accessible name; the placeholder is never the only name.
4. **Pre-fill mapping:** `title`, `author` as stored (`""` stays empty); `yearText = publishedYear?.toString() ?: ""`; `rating` as stored (`null` = 5 outline stars, D8); `comment ?: ""`; memories `?: ""`; cover `Image(uri)` → `CoverDraft.Stored(uri)`, `Preset(p)` → `CoverDraft.Preset(p)`. `lastPreset` seeds from the book's preset, or `Preset1` when the book has an image. A book whose row has neither cover column set already maps to its id-derived preset (`LibraryMappers`), so the form preselects that swatch and saving persists it explicitly - the cover looks the same before and after.
5. **Borrowing section (read-only checkbox, AC4):** heading **"Borrowing"** (no "optional" hint - there is nothing to fill in, so the wireframe's hint would mislead). One row: a checkbox glyph plus the label **"This book is currently lent out"**, and below it a helper line **"Changes when you lend or return this book."** (small, `onSurfaceVariant`).
   - Render with `Checkbox(checked = isLentOut, onCheckedChange = null, enabled = false)` - the disabled look tells sighted users it is not a control they operate; the label and helper text stay full-contrast, so the information is never carried by the dimmed box alone.
   - Semantics on the row, merged: `role = Role.Checkbox`, `toggleableState = On/Off`, `disabled()`, label + helper as the description. **No `onClick` action** and no `toggleable`/`clickable` modifier anywhere in the row. TalkBack reads "This book is currently lent out, checkbox, checked (or not checked), disabled. Changes when you lend or return this book."
   - The row never takes focus as a control, so it does not trap keyboard/switch users.
6. **Footer** (Visual Spec §5.10, wireframe): **"Remove book"** text button far left, text in `MaterialTheme.colorScheme.error` (the `overdue` token: literal terracotta text on paper fails AA, docs/ux/decisions.md 2026-10-27); then **"Cancel"** text button and the primary **"Save changes"** button right-aligned with a 10dp gap. At narrow widths or large font scales the footer must wrap (e.g. `FlowRow`, Remove on its own line above Cancel/Save) rather than clip or overlap - every button stays fully visible and ≥ 48dp tall.
7. **Saving:** all fields, the cover controls and Remove are disabled; Save shows the spinner (same as Add). Save failure keeps every field and shows **"Couldn't save your changes. Try again."** in the existing error slot (polite live region).
8. **Discard confirm** (dirty dismiss): title **"Discard your changes?"**, body **"Your edits to this book won't be saved."**, buttons **"Keep editing"** / **"Discard"** (filled error-colour button, same as Add's dialog). Outside tap / back on the dialog = Keep editing.
9. **Cover:** `CoverPicker` unchanged except that `Stored` renders like `Image` (thumbnail, "Custom cover image", Replace / Remove). Remove on a stored image returns to `Preset(lastPreset)` (#31 UX decision); Cancel afterwards restores the original image because nothing was deleted.
10. All copy in `strings.xml` as `edit_book_*` (Memory strings as `book_form_memory_*`, since `MemorySection` lives in `bookform/`). The wireframe's sample values ("Lark Rise to Candleford", etc.) are not copy.

## 4. Contract (binding)

### 4.1 Navigation

```kotlin
// BookshelfDestinations
data object EditBook : BookshelfDestinations("book/{bookId}/edit") {
    const val ARG_BOOK_ID = "bookId"
    fun route(bookId: Long) = "book/$bookId/edit"
}

// navigation/EditBookNavigation.kt - only the body changes (#32 V6)
fun NavController.navigateToEditBook(bookId: Long) {
    navigate(BookshelfDestinations.EditBook.route(bookId)) { launchSingleTop = true }
}

// features/library/ui/editbook/EditBookRoute.kt
@Composable fun EditBookRoute(
    onDone: (EditBookResult) -> Unit,
    onRemoveClick: (bookId: Long) -> Unit,
    viewModel: EditBookViewModel = hiltViewModel(),
)

sealed interface EditBookResult {
    data object Saved : EditBookResult
    data object Cancelled : EditBookResult
    data object NotFound : EditBookResult   // row vanished before or during save
}
```

NavGraph: `dialog(EditBook.route, arguments = [bookId: LongType], dialogProperties = <same as BookDetail>) { EditBookRoute(onDone = { navController.popBackStack(EditBook.route, inclusive = true) }, onRemoveClick = {} /* #34 */) }`. `BookDetailRoute` and its call site do not change. `BookshelfDestinations.EditBook` is referenced only from `navigation/`.

### 4.2 Draft

```kotlin
data class BookDraft(
    val title: String = "", val author: String = "", val publishedYear: Int? = null,
    val rating: Int? = null, val comment: String = "",
    val memoryWhy: String = "", val memoryWhere: String = "", val memoryWhat: String = "",   // new (E4)
    val cover: CoverDraft = CoverDraft.Preset(CoverPreset.Preset1),
)

sealed interface CoverDraft {
    data class Preset(val preset: CoverPreset) : CoverDraft
    data class Image(val draftRef: String) : CoverDraft          // cover-drafts/<uuid>.jpg, discardable
    data class Stored(val storedRef: String) : CoverDraft        // new (E5): covers/<uuid>.jpg, never discarded by an intent
}
```

### 4.3 `UpdateBookUseCase`

```kotlin
sealed interface UpdateBookResult { data object Updated : UpdateBookResult; data object NotFound : UpdateBookResult }

class UpdateBookUseCase @Inject constructor(
    private val repository: LibraryRepository,
    private val coverImages: CoverImageStore,
) {
    suspend operator fun invoke(bookId: Long, draft: BookDraft): UpdateBookResult
}
```

Rules (E6), throwing `IllegalArgumentException` on violation (unreachable from the UI, so a throw is a programming error):
- `title.trim()` non-empty; `author.trim()` (blank → `""`); `publishedYear` `null` or `1..9999`; `rating` `null` or `1..5`; `comment` and each memory `trim()`, blank → `null`.
- Current row via `repository.observeBook(bookId).first()`; `null` → `NotFound`.
- Result `Book` = current row `.copy(title, author, publishedYear, rating, comment, memoryWhy, memoryWhere, memoryWhat, cover)`. Nothing else changes.
- Cover: `Preset(p)` → `Cover.Preset(p)`; `Stored(ref)` → `Cover.Image(ref)` and **requires** the current row's cover to be `Cover.Image(ref)` (else `IllegalStateException`); `Image(draft)` → `commit` → `Cover.Image(newRef)`.
- Order and rollback: E7.

No new repository or DAO method.

### 4.4 UI state and intents

```kotlin
enum class EditBookError { ImageImportFailed, SaveFailed }

data class EditBookUiState(
    val isLoaded: Boolean = false,
    val title: String = "", val author: String = "", val yearText: String = "",
    val rating: Int? = null, val comment: String = "",
    val memoryWhy: String = "", val memoryWhere: String = "", val memoryWhat: String = "",
    val cover: CoverDraft = CoverDraft.Preset(CoverPreset.Preset1),
    val isLentOut: Boolean = false,          // read-only, live (E8)
    val isImportingImage: Boolean = false,
    val isSaving: Boolean = false,
    val error: EditBookError? = null,
    val initialDraft: BookDraft? = null,     // null until loaded (E10)
) {
    val canSave: Boolean get() = isLoaded && title.isNotBlank() && !isSaving && !isImportingImage
    val isDirty: Boolean get() = initialDraft != null && toDraft() != initialDraft
    fun toDraft(): BookDraft
}
```

`bookId` comes from `SavedStateHandle` (`EditBook.ARG_BOOK_ID`). Intents mirror `AddBookViewModel` through `BookFormRules`, with these differences:

| Intent | Edit rule |
|---|---|
| `onMemoryWhyChange` / `onMemoryWhereChange` / `onMemoryWhatChange` | Store as typed, clear `error`. |
| `onPresetSelected(p)` | Previous `Image(draft)` → `discardDraft`. Previous `Stored` → **no file op**. |
| `onImagePicked(uri)` | As Add. When the new import replaces an `Image(draft)`, discard that draft; when it replaces `Stored`, **no file op**. |
| `onRemoveImage()` | `Image(draft)` → `discardDraft`; `Stored` → **no file op**. Cover → `Preset(lastPreset)`. |
| `onSave()` | Single-flight exactly like Add A8 (sync `isSaving = true`, ignored after a dismiss request). `Updated` → emit `Saved`; `NotFound` → emit `NotFound`; exception → `isSaving = false`, `error = SaveFailed`, fields kept. |
| `onDismissRequest()` | Ignored while saving; single-flight. Discard the current `Image(draft)` if any; **never** touch `Stored`. Emit `Cancelled`. |
| repository emits `null` | Emit `NotFound` once (book removed elsewhere); the NavGraph pops Edit, then Detail closes itself (#32 V8). |

`SavedStateHandle` keys: Add's keys plus `memory_why`, `memory_where`, `memory_what`, `cover_stored_ref`, `seeded`. On restore with an `Image(draftRef)` whose file was purged, fall back to the **original** cover (`initialDraft.cover`), not to a preset.

## 5. Quality attributes and fitness functions

| Attribute | How the design serves it | Fitness function (developer implements) |
|---|---|---|
| **Data integrity** | Re-read-and-copy save (E6); single-flight save; commit → update → delete-old order with rollback (E7); `Stored` never deleted by an intent (E5). | **FF-E1** (JVM, `UpdateBookUseCaseTest`): fields outside the form survive (a row with `status = Finished`, `isbn`, `pageCount`, `createdAt` keeps them); `updateBook` false → `NotFound` + `uncommit`; throw → `uncommit` + rethrow; old stored file deleted **only** after success and only when the cover changed; `Stored` unchanged → no file op. **FF-E2** (JVM, `EditBookViewModelTest`): two `onSave()` in one frame → one `updateBook`; Cancel/Remove/preset after a stored cover → zero `delete`/`discardDraft` calls on the stored ref. |
| **Read-only lending (D13)** | No intent, no write path. | **FF-E3** (JVM file scan, sibling of `BookDetailSeamTest`): no file under `ui/editbook/` and not `UpdateBookUseCase.kt` references `LoanDao`, `LoanEntity`, `BookDao` or `addBook`. Compose test in section 7. |
| **Evolvability** | Seams unchanged for Detail; `onRemoveClick` for #34; shared chrome in `bookform/`. | **FF-E4**: `BookshelfDestinations.EditBook` referenced only from `navigation/`; `ui/bookdetail/` and `LibraryScreen.kt`/`LibraryRoute.kt` do not import `…ui.editbook`; extend FF-A4 so `bookform/` imports neither `addbook/` nor `editbook/`. Existing `navigateToEditBook_isReferencedOnlyFromNavigation` stays green. |
| **No regression in Add** (E9 refactor) | Behaviour-preserving extraction. | All existing #31 JVM, Compose, pixel and E2E tests pass **unmodified** (tags passed through). A reviewer rejects any edit to an existing Add test's assertions. |
| **Accessibility** | Section 3 semantics; labels as accessible names; footer wraps. | Compose semantics tests (section 7); the footer test at `fontScale = 2f`, 320dp width asserts all three buttons are displayed and non-overlapping (reuse the FAB non-overlap helper approach from #22). |

## 6. Security and privacy (light threat model)

Local, single-user, no network. New surface: one in-process dialog destination and a new write path into an existing row.

| Flow | Risk | Rule / test hint |
|---|---|---|
| Route arg `bookId` | Another app opens the editor with arbitrary ids. | **No `deepLinks`** on `EditBook`; the activity's exported surface does not change (reviewer: manifest diff empty). Unknown id → `NotFound` → sheet closes. |
| Memory / comment text | Personal, potentially sensitive reflections ("what was I going through") reach logs or crash reports. | No logging of `Book`, `BookDraft`, `EditBookUiState` (`BookLoggingTest` bans `Log`/`println` in `src/main`); exception messages from the use case name only the rule and the offending number, never field text. |
| Stored cover ref through `SavedStateHandle` | A restored or crafted `Stored` ref points at another file, which a later save would then delete. | The use case accepts `Stored(ref)` only when it equals the current row's `Cover.Image` uri (4.3); `delete` already rejects refs outside `covers/` (`CoverRefs`). |
| Cover files | Orphans on crash/failed delete; deleting the wrong file. | E7 order; `delete` only the **old** ref and only after success. QA probes R3/R4. |
| Loans | Accidental `Loan` write or toggle from the form. | FF-E3 + Compose "cannot be toggled" test. |
| Integrity | Double save, stale overwrite of non-form fields. | FF-E1/FF-E2. |

## 7. Test strategy

| AC | Level | Tests |
|---|---|---|
| AC1 pre-fill | JVM (VM) + Compose (androidTest) | VM: a fully populated book seeds every field (title, author, year text, rating, comment, three memories, `Stored`/`Preset` cover); a sparse book (title only, author `""`, all nulls) seeds empty fields and 5 outline stars; a later Room emission does **not** overwrite typed text. Process-death restore from a populated `SavedStateHandle` gives the same state; purged draft → original cover. Compose: the fields show the stored values; the image cover shows the "Custom cover image" row. |
| AC2 memory | JVM (use case) + Compose | Each memory saved independently (one filled, two blank → one value, two `null`); whitespace-only → `null`; trimmed. Compose: three labelled fields under a "Memory" heading with the wireframe placeholders. |
| AC3 read-only lent | Compose + JVM + DAO-backed instrumented | Compose: row has `ToggleableState.On` when lent / `Off` when not, `assertIsNotEnabled()`, `SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick)`; `performClick()` on the row and on the box leaves the state unchanged. VM: no intent exists; `isLentOut` flips when the fake emits a loan / no loan. Instrumented (in-memory Room): insert an open loan while Edit is open → checked; `markReturned` → unchecked; after Save the `loans` table is byte-identical (row count and every column). FF-E3. |
| AC4 save / cancel | JVM + instrumented E2E | VM: `Saved`/`Cancelled`/`NotFound` emission; dirty vs clean dismiss (confirm only when dirty). E2E (`@HiltAndroidTest`): Library → card → Detail → pencil → change title + memory → Save → back on Detail showing the new title and memory, route `book/{id}`; same with Cancel (and ✕, back) after edits + Discard → Detail unchanged and the DB row unchanged. Cover: preset → image save → file under `files/covers/`; image → preset save → old file deleted; image Replace → Cancel → old file still present, draft dir empty. |
| AC5 Remove present | Compose | "Remove book" is displayed, enabled, left of Cancel, text colour = `colorScheme.error`; tapping it calls `onRemoveClick(bookId)` and nothing else (sheet stays open, no DB change). |
| Nav | `BookshelfNavGraphTest` + JVM seam | Pencil → route `book/{id}/edit` with the right id; double tap → one Edit entry; Save/Cancel → back to `book/{id}` (Detail still on the stack, not Home). FF-E4. |
| Regression | existing suites | All #31/#32 tests unchanged and green after the E9 extraction. |

## 8. Work split and tasks

**FRONTEND-ONLY.** One Android app, no backend. `frontend-developer-android`, in build order:

1. **Domain:** extend `BookDraft` (memories) and `CoverDraft` (`Stored`); make `AddBookUseCase`/`AddBookViewModel`/`CoverPicker` handle `Stored` (unreachable in Add, rendered like `Image` in the picker). `UpdateBookUseCase` + `UpdateBookResult` with FF-E1. (AC1, AC2, AC4)
2. **Extract `bookform/` shared pieces** (E9): `BookFormRules`, `BookFormSheet`, `BookFormDiscardDialog`; move `AddBookRoute`/`AddBookViewModel` onto them. Run the full #31 suite unchanged before going on. (AC4 groundwork, no behaviour change)
3. **Navigation:** `BookshelfDestinations.EditBook`, real body of `navigateToEditBook`, NavGraph `dialog` with `onDone` popping `EditBook.route` and `onRemoveClick = {}`; FF-E4; NavGraph tests. (AC4, AC5)
4. **`EditBookUiState` + `EditBookViewModel`**: seed-once from `ObserveBookDetailUseCase`, live `isLentOut`, intents per 4.4, `SavedStateHandle` restore, single-flight save, `NotFound` handling; FF-E2. (AC1–AC4)
5. **UI:** `MemorySection` (bookform), `BorrowingSection` (editbook, section 3.5 semantics), `EditBookRoute` on `BookFormSheet` with footer per 3.6 (Remove / Cancel / Save changes, wrapping), loading state, strings, FF-E3, Compose tests incl. the "cannot be toggled" test and the 2× font / 320dp footer test. (AC1–AC5)
6. **E2E + docs:** the section 7 instrumented flows; CLAUDE.md architecture tree (`ui/editbook/`, the new `bookform/` files); CHANGELOG under `[Unreleased]` at the end of the ticket. (all)

## 9. Risks for QA to probe

| # | Risk | Probe |
|---|---|---|
| R1 | Edit overwrites fields it does not show (status, ISBN, page count, `createdAt`). | Seed a row with `Finished` + ISBN + page count via DB, edit the title, inspect the row. |
| R2 | A Room re-emission (e.g. a loan opening) wipes in-progress typing. | Type in a memory field, insert/return a loan through the DB, check text survives and the checkbox flips. |
| R3 | Stored cover file deleted on Cancel, on Remove-then-Cancel, or on a failed save. | Image book: Remove → Cancel; Replace → Cancel; force a save failure (e.g. delete the row mid-save). `files/covers/` still has the original. |
| R4 | Orphaned files: replaced/removed image not deleted after a successful save; drafts left after cancel. | Device Explorer on `files/covers/` and `cache/cover-drafts/` after each path. |
| R5 | Lent checkbox toggles under touch, TalkBack double-tap, Switch Access or keyboard Enter. | All four input methods; the `loans` table is unchanged. |
| R6 | Double tap on Save writes twice / double tap on pencil stacks two editors. | Rapid taps; back-stack depth. |
| R7 | Process death mid-edit loses edits, re-seeds over them, or loses the stored-image choice. | "Don't keep activities" + `am kill` with every field changed, and separately with a new image picked. |
| R8 | Footer overflow: "Remove book" + "Cancel" + "Save changes" at 320dp, 2× font, landscape, with IME up. | Visual + the automated footer test. |
| R9 | Stacked dialogs: Edit over Detail looks wrong (double scrim), or back from Edit closes Detail too. | Back, ✕, scrim tap from Edit each land on Detail, not Home. |
| R10 | Book removed while Edit is open (possible once #34/#35 ship). | Delete the row via DB with Edit open: Edit closes once, Detail closes once, no crash or loop. |
| R11 | "Remove book" does nothing in this release - expected until #34; must not crash or close the sheet. | Tap it repeatedly, also during save. |
| R12 | Add regressions from the E9 extraction (scrim semantics, IME, discard dialog). | Re-run #31's manual QA checklist on Add. |

## 10. Open items and follow-ups (not blocking)

- **#34** wires `onRemoveClick` (shared confirm dialog per D14), calls `CoverImageStore.delete` after the row delete, and must pop **both** Edit and Detail on success.
- If #34 slips past the release that ships this ticket, the product owner may prefer to hide "Remove book" until it works; this plan follows the AC (button present as a no-op).
- **Lend Book epic** changes how a loan is opened/closed; the Borrowing helper copy ("Changes when you lend or return this book.") assumes that flow exists and may need rewording once it does.
- A "Saved" confirmation (Visual Spec §5.14) can be added later with a Snackbar host inside the Detail sheet if users miss it (E3).
- Visual Spec reconciliation (CLAUDE.md) may restyle labels to the wireframe's external uppercase form across Add and Edit together.
