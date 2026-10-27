# Plan: #31 Add a book manually

| Field | Value |
|---|---|
| Ticket | [#31](https://github.com/minicore-software/Bookshelf/issues/31), part of epic [#29](https://github.com/minicore-software/Bookshelf/issues/29) (Book Management). Depends on [#30](https://github.com/minicore-software/Bookshelf/issues/30) (PR #38, draft, on this branch's base). |
| Branch | `feature/BOOKSHELF-0031-add-a-book-manually` (based on `feature/BOOKSHELF-0030-extend-book-schema`, not `main`) |
| Stack | Android: Kotlin, Jetpack Compose, Material 3, Navigation Compose 2.10.2, Hilt 2.60.1, Coroutines/Flow, Room 2.8.5 (through #30's repository). Single `app` module. No backend. |
| Agents | `frontend-developer-android` builds it, `frontend-reviewer-android` reviews it |
| Work split | FRONTEND-ONLY |
| UX spec | **Needed** (A13). `ux-specialist` writes it to `docs/specs/` (CLAUDE.md: "feature/UX specs"). This plan fixes the architecture, state shape and rules. The spec fixes interaction details, copy, states and accessibility. |
| API contract | No HTTP or event contract. The binding seams are the **entry seam (section 4.1)**, the **form state and validation rules (section 4.2)**, the **use case (section 4.3)** and the **cover-image store (section 4.4)**. |
| ADR | [ADR-003](../adr/ADR-003-Store-Uploaded-Cover-Images-As-Downscaled-App-Private-Copies.md), Accepted (category: data model, the meaning of `cover_uri`. ADR-002 left this as a follow-up for #31). |
| Status | Ready to build once the UX spec lands (tasks 1–5 do not wait for it) |

---

## 1. Context

- **What exists** (on this branch's base, #30): `Book.cover: Cover` (`Image(uri)` / `Preset(CoverPreset)`), `CoverPreset.Preset1..6` persisted as `preset_1..preset_6`, triggers that reject both columns set, `LibraryRepository.addBook(book): Long` (requires `id == 0L`, `ABORT`), and the S7 mapper rule (blank `comment` → `null`). `BookCover` resolves `Preset` through `coverPalette` (`cover_1_bg`..`cover_6_bg`) and **ignores** `Image.uri` (draws the id-derived colour).
- **The FAB** exists (`LibraryAddBookFab`, #22). Its `onAddBookClick` is hoisted through `LibraryScreen` → `LibraryRoute` → `BookshelfNavGraph`, where it is a no-op `{}`. `BookshelfDestinations` has only `Home`. There is no second destination yet.
- **`Book.author` is `NOT NULL`** (ADR-001), but epic D7 makes it optional. #30 section 11 recommended `""` for "no author" rather than a table rebuild. Nothing in the Library UI renders `author` today (rows and grid show title and cover only), so `""` is invisible there.
- **`BookEntity` KDoc:** non-blank `title`/`author` and the `rating` 1..5 range are enforced "at the write boundary, not in SQL". This ticket is the first write boundary, so it owns those rules (A6).
- **Icons:** `material-icons-core` is not a dependency. Glyphs are drawn with `Canvas` (#19 H6, #22 E6). This ticket follows that rule for ✕, stars and the stepper chevrons.
- **Design sources:** `project/wireframe_images/Add_New_Book_Screen.png` (layout, copy, field order) and `docs/specs/Bookshelf-Visual-Spec.md` §5.10 (sheet), §5.11 (form fields, star input, cover field), §5.14 (toast copy "Added to your shelf"). The Visual Spec is *pending reconciliation* (CLAUDE.md). As in #22, its **structure and behaviour** are adopted and rendered with the **in-code** tokens.
- **Epic decisions in force:** D3 (fields), D7 (title only required), D8 (unset rating = 5 outline stars), D9 (cover XOR), D10 (`want_to_read`), D12 (FAB must not be hard-wired to the manual form).

## 2. Key decisions

| # | Decision | Why / alternatives |
|---|---|---|
| A1 | **The form is a Navigation `dialog` destination** (`add_book/manual`), drawn full-window as a bottom-anchored sheet over a scrim (`DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false, dismissOnClickOutside = false)`). Back, ✕ and the scrim are routed through the ViewModel (A9), never popped by the platform directly. No drag-to-dismiss. | The Library stays visible under the scrim, as Visual Spec §5.10 shows. It has its own back-stack entry, so it gets its own `hiltViewModel` and `SavedStateHandle` (process death) and the Library does not know about it (D12). **Rejected:** M3 `ModalBottomSheet` hosted in `LibraryRoute` (it couples the Library to the form, which defeats D12; drag-to-dismiss can throw away a long draft by accident; tall forms with the IME are known to fight its nested scroll). **Rejected:** a full-screen `composable` destination (the Library disappears; it does not match the sheet design). |
| A2 | **D12 seam: one navigation entry point, `NavController.navigateToAddBook()`**, in `navigation/AddBookNavigation.kt`. Today it navigates to `add_book/manual`. The FAB's `onAddBookClick` calls only this. A future scan/search epic changes the body of this one function (to open a chooser sheet first, for example). The FAB, the Library and the manual form do not change. The route is named `add_book/manual` so the other methods become siblings (`add_book/scan`, `add_book/search`, `add_book/choose`). | That is the smallest seam that removes the hard-wire. **Rejected:** a chooser sheet with one option now (adds UI with nothing to choose). **Rejected:** an `AddBookMethod` registry or strategy interface (nothing to abstract over yet). The other half of the seam is A3. |
| A3 | **The form is a stateless `BookForm(state, callbacks)` seeded from a `BookDraft`.** This ticket always seeds `BookDraft.Empty`. | A future scan/search result opens the same form pre-filled (it hands over a `BookDraft`), and #33 Edit reuses `BookForm` with its extra Memory/Borrowing sections (Visual Spec §5.11: "Book → Memory (optional) → Borrowing"). No nav argument for a seed is added now (YAGNI). The type is the contract. |
| A4 | **Package layout:** `features/library/ui/addbook/` (`AddBookRoute`, `AddBookViewModel`, `AddBookUiState`), `features/library/ui/bookform/` (`BookForm` and its field composables: `StarRatingInput`, `YearStepperField`, `CoverPicker`), `features/library/domain/AddBookUseCase.kt` + `BookDraft.kt`, `core/coverimage/` (`CoverImageStore`, `CoverImageLoader`, the implementations, `LocalCoverImageLoader`), `di/CoverImageModule.kt`. | CLAUDE.md places "add book" under `features/library/ui`. `bookform/` is split from `addbook/` so #33 imports the form without importing Add. The cover-image store is `core/` because `BookCover` (`core/designsystem`) has to draw images and `core` must never import `features` (`LayeringTest`). |
| A5 | **Cover upload is in scope and real** (not stubbed): system Photo Picker (`ActivityResultContracts.PickVisualMedia(ImageOnly)`, no permission, falls back to `ACTION_OPEN_DOCUMENT` on devices without the picker). The picked image is decoded, downscaled and re-encoded as JPEG into an app-private **draft** file. On save the draft moves to `filesDir/covers/` and `cover_uri` stores the relative reference `covers/<uuid>.jpg`. Full reasoning: **ADR-003**. | It is in the ticket's scope line and in D3, so cutting it is a scope change. The cost is bounded: framework `ImageDecoder` (API 28+, minSdk 29), no new dependency. **Rejected:** persisting the picker's `content://` URI (the grant does not survive reliably, the cover disappears when the photo is deleted from the gallery, and the URI is not portable across backup/restore). **Rejected:** stubbing the button (scope change, and the user decides that). Tasks 6–8 are separable. If the product owner wants to split them into a follow-up, they move as a block (section 12). |
| A6 | **The write-boundary rules live in `AddBookUseCase`:** `title.trim()` must be non-empty. `author.trim()`, where blank → `""` (the ADR-001 column stays `NOT NULL`). `publishedYear` is `null` or in `1..9999`. `rating` is `null` or in `1..5`. `comment` blank → `null` (#30 S7 mapper, unchanged). `status = WantToRead`. `createdAt = clock.instant()`. `isbn`, `genre`, `pageCount`, `synopsis` and the memory fields are `null`. A violation throws `IllegalArgumentException`. The ViewModel makes it unreachable, so a throw means a programming error and not a user outcome. | One place that every entry (manual now, scan/search later) goes through. **Rejected:** making `author` nullable (table rebuild on SQLite 3.22 with the `loans` FK and triggers, for a value no screen distinguishes from `""`). **Follow-up for #32:** "Author · Year" must omit an empty author (section 12). |
| A7 | **Form state is one immutable `AddBookUiState` in a `StateFlow`, backed field by field by `SavedStateHandle`.** The cover choice is one sealed field (`CoverDraft.Preset` / `CoverDraft.Image`), so "both" cannot be represented (D9). | Rotation and process death keep the draft. The single sealed field implements AC4 by construction, and the database triggers (ADR-002) are a second line. |
| A8 | **Saving is single-flight.** `onSave()` returns immediately when `!canSave` (this includes `isSaving`). It sets `isSaving = true` synchronously, before launching. On success it emits `Done(Saved(id))`. On failure it clears `isSaving`, keeps every field and sets `error = SaveFailed`. | A double tap must never insert two books (`addBook` has no natural key to dedupe on). |
| A9 | **Cancel, ✕, system back and a scrim tap all call `onDismissRequest()`.** The ViewModel discards the image draft (if any) and emits `Done(Cancelled)`. **Whether a dirty draft asks "Discard this book?" first is a UX-spec decision** (section 3). The state carries `isDirty` so either answer can be built without changing the contract. Nothing is ever written to Room before save, so AC5 holds on every path. | Routing every exit through the ViewModel is what guarantees that no draft file is orphaned and that one rule covers all exits. `dismissOnBackPress = false` + a `BackHandler` stop the dialog destination from popping itself and skipping the cleanup. |
| A10 | **Default cover is `Preset(Preset1)`**, preselected, as the wireframe shows. The swatches render through the existing `coverPalette` (`cover_1_bg`..`cover_6_bg`), per #30 S2/ADR-002. **The palette values are not changed in this ticket.** | The caller and ADR-002 settled the keys and their mapping. Swapping in Visual Spec §1.4's hex values (which the wireframe swatches show) is the palette-reconciliation pass, a resources-only change with no data impact. Known consequence: the swatches will not match the wireframe's colours until then (section 12). |
| A11 | **`BookCover` draws `Cover.Image` from the stored file** through a `CoverImageLoader` supplied by `LocalCoverImageLoader` (a `CompositionLocal`, provided in `MainActivity` from Hilt). The default value is a no-op loader that returns `null`, which keeps the existing id-derived colour. Decoding runs off the main thread (`produceState` + `Dispatchers.IO`) into a small in-memory LRU cache, keyed by reference and target size. While loading, or on failure, the colour placeholder shows. | Without this a user-picked cover would be invisible everywhere except the form, and the feature would look broken. The no-op default keeps every existing pixel test and preview unchanged, and `core/designsystem` gets no Context or IO. **Rejected:** Coil (a new dependency for local files we have already downscaled. Worth revisiting when remote covers arrive with scan/search, ADR-003). |
| A12 | **After save:** the sheet closes and the new book appears through the existing Room flows. A confirmation ("Added to your shelf", Visual Spec §5.14) is shown only if the UX spec asks for it. If it does, the mechanism is a `previousBackStackEntry.savedStateHandle["book_added"] = id` result that `LibraryRoute` consumes once and shows as a Snackbar. | It keeps the Library's ViewModel out of the Add flow. The UX spec decides whether the message exists, and what happens when the active filter hides the new book (section 3). |
| A13 | **UX spec needed: YES.** | This is a new user-facing flow with five custom input types (stepper, tap-star rating with a clear gesture, image-or-swatch cover with import progress and failure, and a gated primary action), plus sheet and IME behaviour, a dirty-dismiss policy and TalkBack semantics. The wireframe is one static frame of the empty state, and the Visual Spec is web-oriented. Neither covers Android states, errors or accessibility. |

## 3. What the UX spec must cover (for `ux-specialist`, mode spec)

Write it to `docs/specs/31-add-book-manually.md`. Bind to the state contract in section 4.2. Ask the architect before deviating from it.

1. **Sheet behaviour on Android** (A1): max height, top corners, header (title "New book" + ✕), scrolling body, a footer that stays pinned above the IME, edge-to-edge insets, landscape and 320dp width, and 2× font scale.
2. **Dismiss policy** (A9): does a dirty draft (any field changed, or an image picked) ask "Discard this book?" on Cancel / ✕ / back / scrim? Copy for it. Does the scrim dismiss at all? AC5 only requires that nothing is persisted.
3. **Title gating:** how the disabled "Add to shelf" button communicates why (hint, label, nothing), and that a whitespace-only title counts as empty (A6). Field focus on open (autofocus title + IME up, or not).
4. **Year stepper:** typing (digits only, max 4), what the first +/− does on an empty field (the plan's default seeds the current year, section 4.2), bounds `1..9999`, long-press repeat or not, and how "0" is treated (the plan's default: it is treated as empty). TalkBack actions for increment/decrement.
5. **Star rating** (D8, AC3): the unset look (5 outline stars) versus 1 star, tapping the current value to clear it (Visual Spec §5.11), the touch-target size (≥ 48dp per star, or a row-level target), and the semantics (one slider-like control with `stateDescription` "No rating" / "3 of 5 stars", or 5 toggles).
6. **Cover field** (D9, AC4): swatch selection indication, image-set state (thumbnail + Replace / Remove, Visual Spec §5.11), what Remove returns to (the last preset, or Preset1), whether a swatch tap is available while an image is set, import-in-progress state, import-failure message, and the content descriptions for the swatches ("Cover colour 1 of 6, selected").
7. **Save:** in-progress state, save-failure message (A8), and post-save feedback and its copy (A12), including the case where the active Library filter (Reading, Finished, Lent out) hides the newly added `want_to_read` book.
8. **Comment:** min/max visible lines and the IME action. There is no length cap (epic: none added).
9. Accessibility pass: focus order, labels (the uppercase labels must not be the only accessible name), error announcement, and contrast with the in-code tokens.

## 4. Contract (binding)

### 4.1 Entry seam (D12)

```kotlin
// navigation/BookshelfDestinations.kt
data object AddBookManual : BookshelfDestinations("add_book/manual")

// navigation/AddBookNavigation.kt
/** The only way into "add a book" (epic D12). Today: the manual form. A future epic may open a chooser here. */
fun NavController.navigateToAddBook() = navigate(BookshelfDestinations.AddBookManual.route) { launchSingleTop = true }

// navigation/BookshelfNavGraph.kt
composable(Home.route) { LibraryRoute(..., onAddBookClick = { navController.navigateToAddBook() }) }
dialog(AddBookManual.route, dialogProperties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
    AddBookRoute(onDone = { result -> navController.popBackStack() /* + A12 result if the spec asks */ })
}
```

`LibraryScreen` / `LibraryRoute` / `LibraryAddBookFab` signatures are **unchanged** (`onAddBookClick: () -> Unit`). `launchSingleTop` stops a fast double tap on the FAB from stacking two forms.

### 4.2 Form state and validation

```kotlin
// features/library/domain/BookDraft.kt  (pure Kotlin, A3)
data class BookDraft(
    val title: String = "", val author: String = "", val publishedYear: Int? = null,
    val rating: Int? = null, val comment: String = "", val cover: CoverDraft = CoverDraft.Preset(CoverPreset.Preset1),
) { companion object { val Empty = BookDraft() } }

sealed interface CoverDraft {
    data class Preset(val preset: CoverPreset) : CoverDraft
    /** A downscaled app-private draft file produced by CoverImageStore.importDraft (not yet a stored cover). */
    data class Image(val draftRef: String) : CoverDraft
}

// features/library/ui/addbook/AddBookUiState.kt
data class AddBookUiState(
    val title: String, val author: String,
    val yearText: String,               // raw field text, digits only, length <= 4
    val rating: Int?,                   // null = unset (D8), else 1..5
    val comment: String,
    val cover: CoverDraft,              // exactly one of image/preset (D9)
    val isImportingImage: Boolean,
    val isSaving: Boolean,
    val error: AddBookError?,           // ImageImportFailed | SaveFailed, cleared on next edit/attempt
) {
    val publishedYear: Int? get() = yearText.toIntOrNull()?.takeIf { it in 1..9999 }   // "0" -> null
    val canSave: Boolean get() = title.isNotBlank() && !isSaving && !isImportingImage
    val isDirty: Boolean get() = toDraft() != BookDraft.Empty
}
sealed interface AddBookResult { data class Saved(val bookId: Long) : AddBookResult; data object Cancelled : AddBookResult }
```

ViewModel intents and their rules:

| Intent | Rule |
|---|---|
| `onTitleChange(s)` / `onAuthorChange(s)` / `onCommentChange(s)` | Store as typed (no trimming while editing). Trim at save (A6). |
| `onYearTextChange(s)` | Accept only if `s` is all digits and `length <= 4`, otherwise ignore the change. |
| `onYearStep(+1 / -1)` | Empty or `0` → current year (from the injected `Clock`). Otherwise add or subtract 1, clamped to `1..9999`. |
| `onRatingTap(n)` | `n in 1..5`. `rating = if (rating == n) null else n` (tapping the current value clears it). |
| `onPresetSelected(p)` | `cover = Preset(p)`. If the previous cover was an `Image` draft, discard that draft file (AC4: picking one clears the other). |
| `onImagePicked(uri)` | `isImportingImage = true` → `CoverImageStore.importDraft(uri)` → `cover = Image(ref)` and discard the previous draft, if any. On failure: keep the previous cover, `error = ImageImportFailed`. A `null` uri (picker cancelled) changes nothing. |
| `onRemoveImage()` | Discard the draft, `cover = Preset(lastPreset)`, where `lastPreset` is the last preset selected (default Preset1). The UX spec may override which preset. |
| `onSave()` | A8. Calls `AddBookUseCase(draft)`. |
| `onDismissRequest()` | A9. Discard the draft image and emit `Cancelled`. Ignored while `isSaving`. |

`SavedStateHandle` keys: `title`, `author`, `year_text`, `rating`, `comment`, `cover_preset` (the persisted literal), `cover_draft_ref`, `last_preset`. On restore, if `cover_draft_ref` points to a file that no longer exists (the cache was purged), fall back to `Preset(lastPreset)` silently.

### 4.3 `AddBookUseCase`

```kotlin
class AddBookUseCase @Inject constructor(
    private val repository: LibraryRepository, private val coverImages: CoverImageStore, private val clock: Clock,
) {
    /** A6 rules. Returns the new book id. Throws IllegalArgumentException on a rule violation (programming error). */
    suspend operator fun invoke(draft: BookDraft): Long
}
```

Order, binding:
1. Validate (A6).
2. `CoverDraft.Image(draftRef)` → `ref = coverImages.commit(draftRef)` → `Cover.Image(ref)`. `Preset(p)` → `Cover.Preset(p)`.
3. `repository.addBook(Book(id = 0, …, status = WantToRead, createdAt = clock.instant()))`.
4. If step 3 throws and step 2 committed a file, call `coverImages.delete(ref)`, then rethrow. No orphan file and no half-saved book.

### 4.4 `CoverImageStore` / `CoverImageLoader` (`core/coverimage`, details in ADR-003)

```kotlin
interface CoverImageStore {
    /** Decode `sourceUri` (any content:// or file:// image), downscale to <= 600 px on the long edge, re-encode as JPEG (q 80)
     *  onto an opaque background into cacheDir/cover-drafts/<uuid>.jpg. Returns the draft ref. Throws CoverImportException. */
    suspend fun importDraft(sourceUri: String): String
    suspend fun discardDraft(draftRef: String)                 // idempotent, never throws
    /** Move the draft into filesDir/covers/<uuid>.jpg. Returns the stored ref "covers/<uuid>.jpg". */
    suspend fun commit(draftRef: String): String
    suspend fun delete(storedRef: String)                      // idempotent; also #34's cleanup hook
}
interface CoverImageLoader { suspend fun load(ref: String, maxSizePx: Int): ImageBitmap? }   // null = draw the colour
val LocalCoverImageLoader = staticCompositionLocalOf<CoverImageLoader> { NoOpCoverImageLoader }
```

- Stored refs match `^covers/[0-9a-f-]{36}\.jpg$`, and draft refs match the same pattern under `cover-drafts/`. Resolution rejects anything else, so there is no path traversal and no foreign URIs. A non-matching `cover_uri` (none exist today) loads `null` and keeps the colour fallback.
- All file IO and decoding runs on `Dispatchers.IO`, injected for tests.
- Backup: `covers/` is **included in `device-transfer`** and **excluded from `cloud-backup`** (and from the pre-API-31 `full-backup-content`), see ADR-003. After a cloud restore, an image cover falls back to its id-derived colour.

## 5. Security and privacy (light threat model)

| Flow | Risk | Rule / test hint |
|---|---|---|
| Picker → app (untrusted image bytes) | A decompression bomb or huge image causes OOM. Corrupt or non-image data. | Decode with `ImageDecoder` and set the target size from the **header** before allocating (`OnHeaderDecodedListener`), so the full-resolution bitmap is never allocated. Catch every decode failure → `ImageImportFailed`. QA: a 50 MP JPEG, a 0-byte file, a renamed `.txt`. |
| Image metadata | EXIF GPS or device data from the photo is kept on the device and in backups. | Re-encoding through `Bitmap.compress` writes no EXIF. Test: import a JPEG with GPS EXIF, and the stored file has no GPS tags (`android.media.ExifInterface`). Orientation is applied before the encode (`ImageDecoder` honours EXIF rotation). |
| `cover_uri` → file path | A crafted value (import or bug) reads outside `covers/`. | Strict ref regex, and resolution only under `filesDir`. JVM test with `../`, absolute paths and `content://` → rejected. |
| Draft text (comment) | Personal text reaches logs. | `BookLoggingTest` already bans `Log`/`println` in `src/main`. No exception message may contain draft content. |
| Entry points | None new: no exported component, no permission, no network. The Photo Picker needs no storage permission. | Reviewer: the manifest diff is empty except backup rules. No `READ_MEDIA_IMAGES` or `READ_EXTERNAL_STORAGE`. |
| Integrity | A double save gives a duplicate book. Both covers set. | A8 single-flight. The `CoverDraft` sealed type plus the ADR-002 triggers. |

## 6. Quality attributes and fitness functions

| Attribute | How the design serves it | Fitness function (developer implements) |
|---|---|---|
| **Data integrity** | Single-flight save, write-boundary rules in one use case, commit-then-insert with rollback, nothing persisted before save. | **FF-A1** (JVM): `AddBookUseCaseTest`: status is always `WantToRead`; rule violations throw; on repository failure the committed cover is deleted. **FF-A2** (JVM): `AddBookViewModelTest`: two `onSave()` calls without suspension in between give exactly one `addBook` call. |
| **Evolvability (D12, #33 reuse)** | One entry function and a stateless `BookForm` seeded by `BookDraft`. | **FF-A3** (JVM, file scan like `LayeringTest`): `AddBookManual` is referenced only in `navigation/`, and `features/library/ui/LibraryScreen.kt`/`LibraryRoute.kt` do not import `…ui.addbook`. **FF-A4**: `bookform/` does not import `addbook/` (so #33 can reuse it). |
| **Privacy / security** | EXIF stripped, refs confined to `covers/`, no logging. | **FF-A5** (JVM): ref-resolution tests (section 5). **FF-A6** (instrumented): GPS-EXIF import test. `BookLoggingTest` unchanged and green. |
| **Accessibility** | Spec-driven semantics for stars, stepper and swatches. Gated button state is announced. | Compose semantics tests per section 8. Contrast cases added to `LibraryContrastTest` for any new token pairing the UX spec introduces. |
| **Performance** | Imports are downscaled to ≤ 600 px. Decode off the main thread. LRU cache. | **FF-A7** (instrumented): an imported 4000×3000 image gives a stored file with a long edge ≤ 600 px and a size under 200 KB. |

## 7. Out of scope

- Scan/search entry, a chooser sheet (D12 leaves the seam only), ISBN, genre, page count, synopsis.
- The Memory and Borrowing sections (#33).
- View/Edit/Delete (#32–#35). **Deleting the cover file when a book is removed is #34's** (`CoverImageStore.delete` is the hook).
- Palette reconciliation of `cover_N_bg` with Visual Spec §1.4 (A10).
- Showing the author anywhere new.
- An image cropper or editor, and a camera capture source.

## 8. Test strategy

| AC | Level | Test |
|---|---|---|
| AC1: "Add to shelf" gated on title, everything else optional | JVM (`AddBookUiState`/VM) + Compose component | `canSave` is false for `""` and `"   "` and true for `"a"`. Compose: the button `assertIsNotEnabled()` → type a title → `assertIsEnabled()`. Saving with only a title persists `author = ""`, year/rating/comment `null`, cover `Preset1`. |
| AC2: always `want_to_read` | JVM (use case) + instrumented E2E | FF-A1. E2E (`@HiltAndroidTest`, in-memory Room through `applyBookshelfConfig()`): FAB → type a title → save → back on Library, the book is in the grid, and the "Want to read" chip count went up by 1. |
| AC3: unset rating ≠ 1 star | JVM + Compose + pixel | VM: tapping 3 then 3 again → `null`. Compose: `stateDescription` differs between unset and 1. Pixel test (same style as `LibraryGridPixelTest`): unset shows 5 outline stars, and 1 star shows one filled star, with different bitmaps. Persisted `rating` is `null` versus `1`. |
| AC4: cover exclusivity | JVM (VM, use case) + instrumented | VM: preset → image → preset ends as `Preset` and the image draft is discarded (the fake store records it). Image → Remove → `Preset(lastPreset)`. Use case maps to exactly one `Cover`. Instrumented: the saved row has exactly one of `cover_uri`/`cover_color` non-null, and the file exists under `files/covers/`. |
| AC5: Cancel discards | JVM + instrumented E2E | VM: `onDismissRequest` → `Cancelled`, no `addBook` call, draft discarded. E2E: fill every field + pick an image (test URI) → Cancel → the Library's book count is unchanged and `cache/cover-drafts/` is empty. The same for ✕ and system back (`Espresso.pressBack()`). |
| Image pipeline | Instrumented (`FileCoverImageStoreTest`) | Downscale (FF-A7), EXIF strip (FF-A6), PNG with alpha → opaque JPEG, corrupt input → `CoverImportException`, `commit` moves the file, `delete`/`discardDraft` are idempotent. |
| `BookCover` renders images | Instrumented pixel | With a fake `LocalCoverImageLoader` returning a solid bitmap, an `Image` cover draws it in both Row and Grid. With the default no-op loader, every existing pixel test is unchanged. |
| Process death / rotation | JVM | Rebuild the VM from a populated `SavedStateHandle` → identical state. A missing draft file → falls back to the preset. |
| Seam | JVM | FF-A3, FF-A4. `BookshelfNavGraphTest`: FAB click → current route is `add_book/manual`. A double click leaves one entry on the back stack. |

The picker UI itself is not automated: `AddBookRoute` takes the picker launch as a lambda, and tests drive `onImagePicked(uri)` directly. QA covers the real picker manually.

## 9. Work split and tasks

**FRONTEND-ONLY:** one Android app with no backend. All the work is in the `app` module. **UX spec first** for tasks 9–10. Tasks 1–8 can start immediately.

`frontend-developer-android`, in build order:

1. `BookDraft`, `CoverDraft`, `AddBookUseCase` (A6, 4.3) with a `CoverImageStore` interface and a JVM fake, plus FF-A1. (AC1, AC2, AC4)
2. Navigation: `AddBookManual`, `navigateToAddBook()`, an empty `dialog` destination wired from the FAB, FF-A3 and the nav test. (D12, AC5 groundwork)
3. `AddBookUiState` + `AddBookViewModel` (4.2 intents, single-flight save, `SavedStateHandle`), with JVM tests, FF-A2. (AC1–AC5)
4. `StarRatingInput` (Canvas stars, clear-on-same-tap) with component and pixel tests. (AC3)
5. `YearStepperField` (digits filter, step rules). (AC1, optional fields)
6. `core/coverimage`: `FileCoverImageStore` (import / commit / discard / delete, ref regex), `FileCoverImageLoader` (LRU), `CoverImageModule`, FF-A5–A7. Backup rules update (ADR-003). (AC4)
7. `LocalCoverImageLoader` provided in `MainActivity`. `BookCover` draws `Cover.Image` via the loader with a colour fallback, plus pixel tests. Update the "uri is ignored" KDoc. (AC4, visible result)
8. `CoverPicker` (swatches via `coverPalette`, Photo Picker launch, image-set row with Replace/Remove). (AC4)
9. `BookForm` (stateless, `bookform/`) + `AddBookRoute` (sheet chrome, insets, `BackHandler`, the scrim, the A9 dismiss policy from the UX spec), FF-A4. (AC1, AC5)
10. Post-save feedback per the UX spec (A12), the E2E tests in section 8, the CLAUDE.md architecture tree (`core/coverimage/`, `features/library/ui/addbook|bookform/`), and the CLAUDE.md domain note that a stored `coverUri` is an app-private ref (ADR-003). (all)

## 10. Risks for QA to probe

| # | Risk | Probe |
|---|---|---|
| R1 | A duplicate book from a double tap on "Add to shelf", or a double FAB tap stacking two forms. | Rapid double and triple taps. Back stack depth afterwards. |
| R2 | Back gesture / ✕ / scrim skipping the ViewModel and leaving an orphan draft file, or popping during save. | Every exit path with an image picked. Check `cache/cover-drafts/` with Device Explorer. Back during an in-flight save. |
| R3 | OOM or ANR on a huge, HEIC or corrupt image. | 50 MP JPEG, HEIC (API 29 device), animated GIF/WebP, 0-byte file, a cloud-only photo (Google Photos) that downloads slowly. |
| R4 | Process death mid-draft loses fields or points to a purged draft. | "Don't keep activities" plus `adb shell am kill` with all fields filled and an image picked, then return. |
| R5 | The IME covers the comment field or the footer button. Landscape. 2× font. 320dp. | Manual on small and large devices, gesture and 3-button navigation. |
| R6 | Unset rating looks identical to 1 star, or the clear gesture fails under TalkBack. | TalkBack walk-through of the whole form. |
| R7 | A new book is invisible because a filter (Reading, Finished, Lent out) is active, and the user thinks the save failed. | Save with each chip selected. Compare with the behaviour the UX spec chose. |
| R8 | The image cover renders as a colour block on the Library after save. | Pick an image → save → Row and Grid cells, and after an app restart. |
| R9 | Cloud restore shows colour placeholders for image covers (accepted, ADR-003), but must not crash. | Backup/restore with `bmgr`, or delete `files/covers/` manually and relaunch. |
| R10 | Whitespace-only title saved, or title/author saved untrimmed. | `"   "` keeps the button disabled. `"  Dune  "` → `"Dune"`. |

## 11. ADR

[ADR-003](../adr/ADR-003-Store-Uploaded-Cover-Images-As-Downscaled-App-Private-Copies.md), Accepted. It decides what `cover_uri` holds (an app-private relative reference to a downscaled, EXIF-free copy, not the picker's URI), the file lifecycle (draft → commit → delete), the backup scope, and that no image-loading library is added. It qualifies as a data-model decision: once users have rows, changing the reference format needs a data migration. ADR-002 explicitly deferred it to #31. **No ADR index exists** (same as #30), so none was created.

The other decisions here (dialog destination, the D12 entry function, form state, use-case rules) follow existing patterns and are recorded in this plan only.

## 12. Open items and follow-ups (not blocking)

- **Splitting the cover upload** (tasks 6–8) into its own ticket is possible without touching the others if the product owner wants a smaller first PR. The form would then ship swatches-only for one release. The architect's recommendation is to keep it in #31 (it is in the AC scope line).
- **#32 View:** render "Author · Year" without a leading separator when the author is `""` and/or the year is `null` (A6).
- **#33 Edit:** reuse `BookForm` and `BookDraft`. A replaced or removed image must `delete` the old stored file only **after** `updateBook` succeeds.
- **#34 Remove:** call `CoverImageStore.delete(ref)` after the row delete succeeds.
- **Palette reconciliation:** the swatches use `cover_N_bg`, not the wireframe's colours (A10). Resources-only change, tracked by the CLAUDE.md "pending reconciliation" note.
- **Remote covers** (future scan/search lookup): adding an `https` ref shape and an image library (Coil) is the expected follow-up. ADR-003 leaves room for it.
