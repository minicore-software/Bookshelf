# Plan: #20 Currently-reading and lent-out rows

| Field | Value |
|---|---|
| Ticket | [#20](https://github.com/minicore-software/Bookshelf/issues/20), part of epic [#17](https://github.com/minicore-software/Bookshelf/issues/17) (Library Screen). Depends on [#18](https://github.com/minicore-software/Bookshelf/issues/18) (merged) and builds below [#19](https://github.com/minicore-software/Bookshelf/issues/19)'s header (merged). |
| Branch | `feature/BOOKSHELF-0020-currently-reading-lent-out-rows` |
| Stack | Android: Kotlin, Jetpack Compose, Material 3, Hilt 2.60.1, Coroutines/Flow, Room (read through #18's repository). Single `app` module. No backend. |
| Agents | `frontend-developer-android` builds it, `frontend-reviewer-android` reviews it |
| Work split | FRONTEND-ONLY |
| UX spec | Not needed. See R12. |
| API contract | No HTTP or event contract. Binding seams: #18's `LibraryRepository` (consumed unchanged), the new domain types in section 4, and the composable callbacks in R9. |
| ADR | None. See section 11. |
| Status | Ready to build |

---

## 1. Context

- **Screen today** (`features/library/ui/LibraryScreen.kt`, #19): a stateless `Column(fillMaxSize, background, safeDrawingPadding)` holding only `LibraryHeader(onSearchClick)`. `BookshelfNavGraph` mounts `LibraryScreen(onSearchClick = {})` on `Home`. There is no ViewModel yet.
- **Data** (`features/library/domain/LibraryRepository.kt`, #18 plan section 5): `observeCurrentlyReading(): Flow<List<LibraryBook>>` (`status == Reading`, `created_at DESC`) and `observeLentOutBooks(): Flow<List<LentBook>>` (`INNER JOIN` on the open loan, `due_at ASC`, so the most urgent loan comes first). `OpenLoan.dueAt` is a `LocalDate`. The repository deliberately does **not** compute overdue: the caller derives it from `dueAt` and the current date taken from an injected `java.time.Clock` (#18 plan section 5, repository KDoc).
- **Design source** `project/Main.dc.html`, "Library" board, scrollable content below the header:
  - Section header row: `padding 16px 20px 4px` (reading) / `18px 20px 4px` (lent), `space-between`, `align-items: baseline`. Title in Fraunces 600 18px ink. Trailing label in Inter 600 12px forest: "2 books" (reading, plain text) and "See all · 3" (lent, a link).
  - Row: `display:flex; gap:14px; padding:12px 20px; overflow-x:auto`.
  - Card: `width:132px`, column gap 8px. Cover `132×188`, radius 8px, shadow `0 4px 10px rgba(35,31,32,.12)`, padding 12px, title anchored bottom-left in Fraunces *italic* 14px, line-height 1.25, cream (`#EDE9DC`/`#F6EBDD`). The mock's covers are solid colour blocks (`#3F6D63`, `#A8562E`, `#B99A2E`, `#5C6B8A`, and `#6B4A63`, `#7A8559` in the grid). There are no cover images anywhere in the mock.
  - Reading card: the cover, then a progress bar and a "64% · p.212 of 331" caption. **Both are removed** (D11/D16).
  - Lent card: the cover at `opacity:0.55`, then a column with gap 2px: "With Priya" (Inter 600 11.5px `#4A4640`) and the caption. Upcoming: "Due in 3 days" in Inter 400 11px terracotta `#C1693C`. Overdue: "Overdue 2 days" in Inter **600** 11px `#9B2C2C`.
  - In the mock, the header is fixed and the content below it scrolls vertically (`flex:1; overflow-y:auto`).
- **Related sources:** `project/Loans.dc.html` (My Loans, not built) adds a third, muted tier ("Due in 11 days · Nov 29" in `#8A8274`). `docs/specs/Bookshelf-Visual-Spec.md` §5.8 gives the due-text rule `Overdue Nd` / `Due today` / `Due in Nd` (≤7 days) / `Due MMM D, YYYY`, and §5.8/§1.4 dim borrowed covers to `.55` and define user-picked cover presets (future).
- **Epic decisions in force:** D9 read-only, D10 "lent" is an overlay, D11/D16 no progress UI, D15 lent-card opacity "exactly as mocked", D17 borrower and due/overdue caption kept, D18 card taps are no-ops, D19 "See all" visible and a no-op.
- **Precedents:** #10 L1 and #19 H1 (screens use the CLAUDE.md tokens until the Visual Spec reconciliation). #19 H4/H6 (where a mock colour fails WCAG AA for small text, use an AA-passing token and record the deviation). #10 review rule (no `Color(0x…)` and no `FontFamily(...)` outside `core/designsystem`). The only fonts bundled are Fraunces SemiBold and Inter Regular.
- **No image-loading library** is a dependency, and nothing writes `Book.coverUri` yet (there is no Add Book screen, D9), so `coverUri` is always `null` in practice.
- **No Hilt test infrastructure:** there is no `hilt-android-testing`, no custom runner, and `BookshelfNavGraphTest` uses a plain `createComposeRule()` (a non-Hilt `ComponentActivity`).

## 2. Key decisions

| # | Decision | Why / alternatives |
|---|---|---|
| R1 | **Cover rendering: a token-driven placeholder only. No image-loading library this ticket.** A new `BookCover(bookId, title, coverUri, modifier, dimmed = false)` in `core/designsystem/component/` always draws the placeholder (colour block plus title). `coverUri` is accepted but ignored, and the KDoc says so: "image loading arrives with the first ticket that writes `coverUri`". | Nothing can produce a non-null `coverUri` this epic, and the mock itself shows colour-block covers, so a placeholder is what the design draws, not a fallback. Adding Coil now means a new dependency (plus OkHttp/Okio transitively) and a lifecycle, caching and permissions decision (content:// vs file URIs, persisted URI grants) for data that does not exist. That decision belongs to the Add Book/scanner epic, which is when the first `coverUri` gets written, and it deserves its own ADR then (external library, category 1). **Rejected:** add Coil now (speculative, untestable without real URIs). **Rejected:** a `painterResource` stock-cover drawable (a new asset with no design source). **Cost of reversing:** one `if (coverUri != null) AsyncImage(...) else Placeholder(...)` branch inside `BookCover`. No call site changes. |
| R2 | **Placeholder colour is picked from a fixed six-colour palette by `bookId`** (`Math.floorMod(bookId, 6)`), never by title. Pairs, based on the mock's six cover colours. Each foreground is cream `#F6EBDD` or ink `#231F20`, whichever passes AA: <br>1 `#3F6D63` / cream (5.0:1) · 2 `#A0522C` / cream (4.8:1) · 3 `#B99A2E` / **ink** (6.0:1) · 4 `#5C6B8A` / cream (4.55:1) · 5 `#6B4A63` / cream (6.4:1) · 6 `#8A9566` / **ink** (5.1:1). | The colour is stable across renames and identical in #20's rows and #21's grid (CLAUDE.md: identity is ID-based). The mock uses six colours. With the mock's cream on all six, three pairs fail AA for a 14sp title (`#A8562E` 4.4, `#B99A2E` 2.3, `#7A8559` 3.35). Two backgrounds are adjusted minimally (`#A8562E`→`#A0522C`, `#7A8559`→`#8A9566`), and two pairs switch to ink text. This is the #19 H4 precedent. The cover title is the **only** visible title in both rows, so it has to be readable. **Rejected:** the Visual Spec §1.4 presets. They are a user-chosen field that does not exist in the schema, and they use the not-yet-adopted palette (#10 L1). They become the natural replacement at reconciliation. **Rejected:** hashing the title (a rename would change the colour). |
| R3 | **Cover title type:** Fraunces SemiBold 14sp, line height 17.5sp (1.25), **upright, not italic**. `maxLines = 4`, `TextOverflow.Ellipsis`. Bottom-start aligned inside 12dp padding. Scales with font size. | Only an upright SemiBold Fraunces is bundled. Requesting `FontStyle.Italic` would make Compose synthesize a skewed oblique, the same silent-synthesis trap as #10 R2/#19 R2. Bundling a Fraunces italic instance (a new `tools/fraunces-font` run, a new file, new tests) for one decorative overlay is not worth it in this ticket. The reconciliation (Roboto Slab) changes this text anyway. `maxLines` stops a 2× font scale from overflowing the fixed 188dp cover. Recorded deviation from the mock. |
| R4 | **Due/overdue rule** (pure domain function, section 4), with `days = ChronoUnit.DAYS.between(today, dueAt)`: <br>`days < 0` → **Overdue**: "Overdue 1 day" / "Overdue N days" <br>`days == 0` → **Due today**: "Due today" <br>`1 ≤ days ≤ 7` → **Due soon**: "Due in 1 day" / "Due in N days" <br>`days > 7` → **Due later**: "Due Nov 29" (localized month-day skeleton `MMMd`; `MMMdyyyy` when `dueAt.year != today.year`). <br>**Colour is two-state, as the AC says.** Overdue → `overdue` token, Inter **SemiBold**. Every other state → `due_upcoming` token, Inter Regular. | The copy matches the mock ("Due in 3 days", "Overdue 2 days", in its long form) and uses the Visual Spec §5.8 thresholds for the cases the mock does not show (today, more than 7 days). "Due today" is **not** overdue: `dueAt` is a calendar day (#18 S3), so the book is due by the end of that day. The AC says "color-coded: upcoming vs. overdue", so there are exactly two colours. `Loans.dc.html`'s third, muted tier (more than 7 days) is a My Loans design and is left to that epic. The overdue state also differs in **weight and wording**, so colour is never the only signal (WCAG 1.4.1). This is a small, reversible call (one `when` and two tokens), so it is made here rather than escalated. |
| R5 | **Colour tokens.** New in `colors.xml`: `ink_soft` `#4A4640` (borrower name; also the colour #21's chips and grid titles use), `due_upcoming` `#A4532C`, `overdue` `#9B2C2C`, `cover_cream` `#F6EBDD`, and `cover_1_bg` … `cover_6_bg`. **`due_upcoming` is a deliberate deviation** from the mock's terracotta `#C1693C`, which is **3.4:1 on paper and fails AA** for 11sp text. `#A4532C` is the same hue darkened to 4.7:1. `overdue` (6.5:1) and `ink_soft` (8.1:1) pass as mocked. M3 mapping: `error = overdue`. The tokens without an M3 role go in a new `@Immutable BookshelfExtendedColors` (`inkSoft`, `dueUpcoming`, `overdue`, `coverPalette: List<CoverColors>`), provided by `BookshelfTheme` through a `staticCompositionLocalOf` and read as `BookshelfTheme.extendedColors`. | This is the same AA rule as #19 H4/H6. The `terracotta` token itself (`#C1693C`, for fills and accents) is not added here because nothing in this ticket fills with it. Forcing these into unrelated M3 roles (`tertiary`, `secondary`) would mislead later tickets. An extended-colours local is the standard Compose pattern and keeps tokens single-sourced in `colors.xml` (#10 L3). **Rejected:** hex literals in the feature (review rule). |
| R6 | **Bundle Inter SemiBold** (`res/font/inter_semibold.ttf`, static, from the same pinned `rsms/inter` v4.1 zip as `extras/ttf/Inter-SemiBold.ttf`). `Inter = FontFamily(Font(inter_regular, Normal), Font(inter_semibold, SemiBold))`. New typography overrides: `titleMedium` = Fraunces SemiBold 18sp (section titles), `titleSmall` = Fraunces SemiBold 14sp / 17.5sp (cover title), `labelMedium` = Inter SemiBold 12sp (trailing labels), `labelSmall` = Inter Regular 11sp, line height 14sp (due caption; overdue uses `.copy(fontWeight = SemiBold)`). Borrower = `labelSmall.copy(fontSize = 11.5.sp, fontWeight = SemiBold)`, defined as a named `TextStyle` in `Type.kt` (`LibraryTextStyles.borrower`), so there is no ad-hoc font code in the feature. | The mock uses weight 600 for four elements here (both trailing labels, the borrower and the overdue caption), and the weight is what makes overdue stand out without relying on colour (R4). With only Regular declared, `FontWeight.SemiBold` would be **synthesized** (a fake bold, #19 R2). It is the same upstream release and the same OFL licence file (`Inter-OFL.txt` covers the family), and #21's active chip needs it too. Not a new vendor. **Rejected:** use Regular everywhere (overdue then differs only by colour and wording). |
| R7 | **Layering and state.** `LibraryRoute` (stateful, in `features/library/ui/`) calls `hiltViewModel<LibraryViewModel>()` and `collectAsStateWithLifecycle()`, then calls the stateless `LibraryScreen(uiState, onSearchClick, onBookClick, onSeeAllLoansClick, modifier)`. `LibraryViewModel` (`@HiltViewModel`) injects `ObserveLibraryRowsUseCase` and exposes `StateFlow<LibraryUiState>` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)`. `ObserveLibraryRowsUseCase` (`features/library/domain`) combines `observeCurrentlyReading()`, `observeLentOutBooks()` and `CurrentDateProvider.observe()`, maps each `LentBook` to `LentOutRow(book, borrowerName, dueStatus)`, and emits `LibraryRows`. | This follows CLAUDE.md's data flow (UI → ViewModel (StateFlow) → Use Cases → Repository). The use case contains real logic (combining three flows and deriving the due status), so it is not a pass-through. The overdue derivation stays in domain and is JVM-testable, and composables only turn a `LoanDueStatus` into a string. A stateless `LibraryScreen` keeps every Compose test Hilt-free (state in, callbacks out). `WhileSubscribed(5_000)` survives rotation and restarts the date ticker when the app returns from background (R8). **Rejected:** having the ViewModel call the repository directly (it skips the documented use-case layer and puts date logic in the ViewModel). **Rejected:** computing overdue in a composable with `LocalDate.now()` (untestable, and it hides time in UI). #21 extends the same `LibraryViewModel`/`LibraryUiState` with chips and the grid. |
| R8 | **Clock injection and midnight rollover.** `di/ClockModule`: `@Provides fun provideClock(): Clock = Clock.systemDefaultZone()`, **unscoped** (a fresh clock, with the current default zone, each time a ViewModel is created). `CurrentDateProvider @Inject constructor(private val clock: Clock)` (`features/library/domain`): `fun observe(): Flow<LocalDate>` emits `LocalDate.now(clock)`, then `delay`s until the next local midnight (`today.plusDays(1).atStartOfDay(clock.zone)` minus now, at least 1 ms), emits again, and so on, with `distinctUntilChanged()`. | The #18 repository KDoc explicitly warns that a flag computed once goes stale at midnight. With the ticker, a screen left open overnight turns "Due today" into "Overdue 1 day" at 00:00 without a reload. `Clock.systemDefaultZone()` freezes the zone at creation, so an unscoped provider plus `WhileSubscribed` re-collection picks up a zone change the next time the user returns to the app. Tests inject `Clock.fixed(...)` or a mutable test clock. **Rejected:** a `@Singleton` clock (it would freeze the zone for the process lifetime). **Rejected:** a `TIME_TICK`/`DATE_CHANGED` broadcast receiver (more moving parts for the same result). Known limit: a time-zone change *while the screen stays in the foreground* is not picked up until the next re-collection (risk Q5). |
| R9 | **Stub wiring (D18/D19).** `LibraryScreen` gains `onBookClick: (bookId: Long) -> Unit = {}` and `onSeeAllLoansClick: () -> Unit = {}`, following #19 H7's `onSearchClick` pattern. Each card is `Modifier.clickable(role = Role.Button, onClickLabel = "Open book")` with merged semantics. "See all · N" is `clickable(role = Role.Button, onClickLabel = "See all loans")` with `minimumInteractiveComponentSize()` (48dp target; the 12sp label alone is about 16dp tall). `BookshelfNavGraph` passes `{}` for all three with a `// stub until Book Detail / My Loans ship (epic #17 D18/D19)` comment. | The click is hoisted, so the no-op is testable (a counting lambda) and the later navigation tickets only change `BookshelfNavGraph`. The ripple makes the stub feel intentional (same reasoning as #19 H7). |
| R10 | **Layout.** `LibraryScreen` = `Column(fillMaxSize, background, safeDrawingPadding)` { `LibraryHeader` (unchanged, fixed); `LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f).testTag(LIBRARY_BODY_TEST_TAG))` }. This ticket adds only **full-span** items (`span = { GridItemSpan(maxLineSpan) }`), keyed and in order: reading header, reading row, lent header, lent row. Each row is a `LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = spacedBy(14.dp))` with `items(key = { it.book.id })`. Section header: `Row(padding(start 20, top 16 (reading) / 18 (lent), end 20, bottom 4), SpaceBetween)`, both texts `alignByBaseline()`. Title text carries `heading()` semantics. Card: `Column(width(132.dp), spacedBy(8.dp))`; lent card adds `Column(spacedBy(2.dp)) { borrower; caption }`. Cover: `size(132.dp, 188.dp)`, `RoundedCornerShape(8.dp)`, `shadow(4.dp, shape)`. Lent cover: `BookCover(dimmed = true)` → `Modifier.alpha(0.55f)` on the whole cover, title included, as mocked. | The mock's header is fixed and its body scrolls (#19 H8). Making the body a 3-column lazy grid **now** means #21 adds its chips (full-span) and grid cells (one span each) to the same lazy container. Otherwise #21 would have to replace a `LazyColumn` (a lazy grid cannot be nested inside a vertical lazy list). With full-span items only, it behaves exactly like a `LazyColumn` today. A horizontal `LazyRow` inside a vertical lazy grid is a supported nesting (different axes). Keys are `book.id` (ID-based identity, stable item state across re-emissions). **Rejected:** `Column` + `verticalScroll` (not lazy, and it breaks when #21's grid grows). Section titles as headings give TalkBack heading navigation. #19's "exactly one heading" test is scoped to `LibraryHeader` and is unaffected. |
| R11 | **Empty rows are hidden.** A row (its header and its `LazyRow`) renders only when its list is non-empty. `LibraryUiState.Loading` renders no rows (the header shows immediately). Counts in the trailing labels are the list sizes: `pluralStringResource(library_reading_count, n)` → "1 book" / "N books"; "See all · N". | A non-empty library with nothing currently reading is common, and an empty "Currently reading" header with nothing under it is broken UI that the mock does not show. #22's AC ("with 0 books, rows hidden") then holds without any extra work; #22 still owns the empty-shelf message. Hiding on `Loading` avoids a flash of headers. Using the list size for "See all · N" is correct because `observeLentOutBooks()` returns exactly the open loans (one per book, #18 S5). Reversible in one condition. Flagged in section 12. |
| R12 | **No UX spec needed.** | `Main.dc.html` pins every layout value (paddings, gaps, card and cover sizes, radii, type sizes, weights, colours, opacity). The only things it leaves open are the copy for states it does not show (today, more than 7 days), which the Visual Spec §5.8 already defines (R4), and the placeholder for a missing cover, which the mock's own colour blocks answer (R1/R2). The judgement calls (R2/R5 AA contrast, R3 upright title, R11 hiding empty rows) are accessibility fixes or one-line reversals, recorded here in the same way #19 recorded its own. There is one populated state, no new flow and no new interaction beyond two stubs. |
| R13 | **Hilt instrumented-test support** (test-only). Add `com.google.dagger:hilt-android-testing` (`hilt` version ref, `androidTestImplementation`) and `kspAndroidTest(libs.hilt.compiler)`. Add an `androidTest` `HiltTestRunner : AndroidJUnitRunner` that instantiates `HiltTestApplication`, and point `testInstrumentationRunner` at it. Annotate every instrumented test that launches `MainActivity` or the nav graph with `@HiltAndroidTest` and add `HiltAndroidRule(this)` at `order = 0` (`MainActivityLaunchTest`, `LibraryScreenPixelTest`, `BookshelfNavGraphTest`). `BookshelfNavGraphTest` switches to `createAndroidComposeRule<HiltTestActivity>()`, a `@AndroidEntryPoint` empty `ComponentActivity` declared in `src/debug/AndroidManifest.xml` and `src/debug/.../HiltTestActivity.kt`. | `BookshelfNavGraph` now creates `LibraryViewModel` through `hiltViewModel()`, which throws in a non-Hilt `ComponentActivity`, so `BookshelfNavGraphTest` breaks without this. This is the standard Hilt testing setup (android-testing skill §0.7). It is from the same vendor and version as the existing Hilt dependency, and it is test-only. It also lets the nav-level test inject a fake repository (`@UninstallModules(LibraryDataModule::class)` + `@BindValue`), so the **populated state** can be tested end to end through the real ViewModel and use case, and #21/#22 reuse it. **Rejected:** a slot parameter on `BookshelfNavGraph` only so tests can bypass `hiltViewModel()` (a production seam for test convenience, and it would have to be repeated for every route). **Rejected:** dropping `BookshelfNavGraphTest` (it guards #19's AC4(b)). Tests that do not touch Hilt (pure Compose, Room DAO tests) run unchanged under `HiltTestApplication`. |

## 3. Font asset: Inter SemiBold

- **File:** `app/src/main/res/font/inter_semibold.ttf`. This is `extras/ttf/Inter-SemiBold.ttf` from the **same** `rsms/inter` `v4.1` release zip that `Type.kt` already cites. Static (no `fvar`), `name` table family "Inter", subfamily "SemiBold".
- **Provenance:** extend the existing Inter KDoc in `Type.kt` with the file name and its SHA-256.
- **Licence:** the existing `app/src/main/assets/licenses/Inter-OFL.txt` covers the family. No new file. Update the README asset note to say "Inter Regular and SemiBold".
- **Tests:** extend `InterFontFileTest` (no `fvar`, name table "Inter"/"SemiBold") and `InterTypefaceTest` (`ResourcesCompat.getFont(R.font.inter_semibold)` is non-null, and a SemiBold sample measures wider than the Regular one in `TextMeasurer` with the same text and size, which proves that the real file was used and that the weight was not synthesized from Regular).

## 4. New types (binding for this ticket and #21)

`features/library/domain/` (pure Kotlin: no Android or Compose imports)

```kotlin
/** Derived per render date; never persisted (repository KDoc: overdue is a presentation concern). */
sealed interface LoanDueStatus {
    val isOverdue: Boolean get() = this is Overdue
    data class Overdue(val days: Int) : LoanDueStatus          // days >= 1
    data object DueToday : LoanDueStatus
    data class DueSoon(val days: Int) : LoanDueStatus          // 1..DUE_SOON_MAX_DAYS
    data class DueLater(val dueAt: LocalDate, val showYear: Boolean) : LoanDueStatus

    companion object {
        const val DUE_SOON_MAX_DAYS = 7
        fun of(dueAt: LocalDate, today: LocalDate): LoanDueStatus = TODO("R4")
    }
}

data class LentOutRow(val book: Book, val borrowerName: String, val dueStatus: LoanDueStatus)
data class LibraryRows(val currentlyReading: List<Book>, val lentOut: List<LentOutRow>)

class CurrentDateProvider @Inject constructor(private val clock: Clock) {
    fun observe(): Flow<LocalDate>                            // R8
}

class ObserveLibraryRowsUseCase @Inject constructor(
    private val repository: LibraryRepository,
    private val currentDate: CurrentDateProvider,
) {
    operator fun invoke(): Flow<LibraryRows>                  // combine(reading, lent, date)
}
```

`features/library/ui/`

```kotlin
sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Content(val rows: LibraryRows) : LibraryUiState   // #21 adds chips/grid fields here
}

@Composable fun LibraryRoute(onSearchClick: () -> Unit, onBookClick: (Long) -> Unit, onSeeAllLoansClick: () -> Unit)
@Composable fun LibraryScreen(
    uiState: LibraryUiState,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onBookClick: (bookId: Long) -> Unit = {},
    onSeeAllLoansClick: () -> Unit = {},
)
```

- `LibraryScreen`'s `uiState` is **required** (no default). The existing `LibraryScreenTest` and `LibraryHeader`-level tests pass `LibraryUiState.Content(LibraryRows(emptyList(), emptyList()))`, so their "exactly 3 texts" assertion still holds (R11).
- The ordering of both lists is the repository's (#18 section 4: reading `created_at DESC`; lent `due_at ASC`, so overdue loans come first). The use case does not re-sort.
- `borrowerName` is shown verbatim, trimmed only for layout (`maxLines = 2`, ellipsis). It is never logged (#18 section 6).
- Compatibility: additive. `LibraryRepository` is unchanged. `LibraryScreen`'s signature changes (the new required `uiState`), but the only callers are `BookshelfNavGraph` (which moves to `LibraryRoute`) and tests, so `!` does not apply.

## 5. File changes

| File | Change |
|---|---|
| `res/font/inter_semibold.ttf` | New (section 3). |
| `res/values/colors.xml` | Add `ink_soft`, `due_upcoming`, `overdue`, `cover_cream`, and `cover_1_bg`…`cover_6_bg` (R2, R5), under a `<!-- Library rows (BOOKSHELF-0020) -->` comment. |
| `res/values/strings.xml` | `library_currently_reading_title` "Currently reading", `library_lent_out_title` "Lent out", plurals `library_reading_count` ("%d book"/"%d books"), `library_lent_out_see_all` "See all · %d", `library_see_all_click_label` "See all loans", `library_book_click_label` "Open book", `library_loan_borrower` "With %s", `library_loan_due_today` "Due today", plurals `library_loan_due_in` ("Due in %d day"/"Due in %d days"), plurals `library_loan_overdue` ("Overdue %d day"/"Overdue %d days"), `library_loan_due_on` "Due %s". |
| `core/designsystem/Color.kt` | `error = overdue`. New `@Immutable data class BookshelfExtendedColors(inkSoft, dueUpcoming, overdue, coverPalette: List<CoverColors>)` and `data class CoverColors(background, content)`, built from `colorResource`. |
| `core/designsystem/Theme.kt` | Provide `LocalBookshelfExtendedColors`. Add `object BookshelfTheme { val extendedColors @Composable get() }` (or an equivalent accessor next to the existing `BookshelfTheme` function). |
| `core/designsystem/Type.kt` | Two-weight `Inter` family (R6). `titleMedium`, `titleSmall`, `labelMedium` and `labelSmall` overrides. `LibraryTextStyles.borrower`. Inter KDoc provenance for SemiBold. |
| `core/designsystem/component/BookCover.kt` | **New.** `BookCover(bookId: Long, title: String, coverUri: String?, modifier, dimmed: Boolean = false)`. Palette lookup `coverColorsFor(bookId)` with `Math.floorMod`. Test tag `BOOK_COVER_TEST_TAG_PREFIX + bookId`. Previews of all six palette entries and dimmed. Imports nothing from `features` (`LayeringTest`). |
| `features/library/domain/LoanDueStatus.kt`, `LibraryRows.kt`, `CurrentDateProvider.kt`, `ObserveLibraryRowsUseCase.kt` | **New** (section 4). |
| `features/library/ui/LibraryUiState.kt`, `LibraryViewModel.kt`, `LibraryRoute.kt` | **New** (R7). |
| `features/library/ui/LibraryRows.kt` | **New.** `CurrentlyReadingSection`, `LentOutSection`, private `SectionHeader`, `ReadingCard`, `LentCard`, and `@Composable loanDueCaption(status: LoanDueStatus): String` (date via `DateFormat.getBestDateTimePattern(locale, "MMMd"/"MMMdyyyy")` and the current `LocalConfiguration` locale). Test tags: `LIBRARY_READING_ROW_TEST_TAG`, `LIBRARY_LENT_ROW_TEST_TAG`, `LIBRARY_SEE_ALL_LOANS_TEST_TAG`, `libraryReadingCardTag(id)`, `libraryLentCardTag(id)`, `libraryLentCaptionTag(id)`. Previews: populated, 2× font scale, 320dp width, overdue plus due-today plus due-later mix. |
| `features/library/ui/LibraryScreen.kt` | Take `uiState` plus the two new callbacks. Add the `LazyVerticalGrid` body (R10) below `LibraryHeader`. Update the KDoc and preview (sample `Content`). |
| `di/ClockModule.kt` | **New.** Unscoped `Clock` provider (R8). |
| `navigation/BookshelfNavGraph.kt` | `composable(Home) { LibraryRoute(onSearchClick = {}, onBookClick = {}, onSeeAllLoansClick = {}) }` with the stub comment (R9). |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | `hilt-android-testing` (`androidTestImplementation`), `kspAndroidTest(libs.hilt.compiler)`, `testInstrumentationRunner = "com.minicore.bookshelf.HiltTestRunner"` (R13). |
| `androidTest/.../HiltTestRunner.kt`; `src/debug/AndroidManifest.xml` + `src/debug/java/.../HiltTestActivity.kt` | **New** (R13). |
| Tests | Section 8. |
| `README.md` | Inter SemiBold asset note. One line under Testing: instrumented tests run on `HiltTestRunner`. |
| `CLAUDE.md` | Add `core/designsystem/component/` to the architecture tree (the shared `BookCover`). Add `hilt-android-testing` (2.60.1) to the dependency table. |
| `CHANGELOG.md` | One line under `[Unreleased]`, at integration time. |

Not touched: `LibraryRepository` and the Room layer, `LibraryHeader.kt`, splash, `themes.xml`.

## 6. Quality attributes and fitness functions

| Attribute | How the design serves it | Fitness function (developer implements) |
|---|---|---|
| **Correctness of time-derived UI** | Due status is a pure function of `(dueAt, today)` (R4). `today` comes from an injected `Clock` and rolls over at midnight (R8). | **FF1** (JVM, `LoanDueStatusTest`): the boundaries −1 → `Overdue(1)`, 0 → `DueToday`, 1 → `DueSoon(1)`, 7 → `DueSoon(7)`, 8 → `DueLater`, and `showYear` across 31 Dec/1 Jan. Also a leap day, and `Overdue(400)`. **FF2** (JVM, `CurrentDateProviderTest` with `runTest` virtual time and a mutable test `Clock`): it emits today, then advancing to 23:59:59.999 emits nothing, then advancing past 00:00 emits tomorrow. A DST-transition day (for example `Europe/Lisbon`, last Sunday of March) still rolls over exactly once. |
| **Accessibility** | AA-passing text tokens (R2, R5). Overdue is not signalled by colour alone (R4). 48dp "See all" target (R9). Section headings, merged card semantics. `maxLines` guards for font scaling (R3). | **FF3** (JVM, `LibraryContrastTest`): parse `res/values/colors.xml` from disk (the same file-reading style as `LayeringTest`) and assert WCAG contrast ≥ 4.5:1 for `due_upcoming`, `overdue` and `ink_soft` on `paper`, and for every `cover_N_bg` against the foreground `CoverColors` assigns to it. Changing a colour then fails a test, not an audit. **FF4** (instrumented): `See all` has height ≥ 48dp. At `FontScale(2f)` + `ForcedSize(320.dp, 800.dp)`, the borrower and caption of the first lent card are displayed and not zero-width. |
| **Visual fidelity / no font synthesis** | Two real Inter weights, upright Fraunces only (R3, R6), with tokens owned by the theme. | Section 3 tests. **FF5** (instrumented pixel, extend `LibraryScreenPixelTest` or add `LibraryRowsPixelTest`): with a fixed `Content` state, the darkest pixel inside an overdue caption's bounds is within ±10 per channel of `#9B2C2C`, and inside an upcoming caption's bounds of `#A4532C`. A pixel sampled near a lent cover's top-left inner corner (inside the radius, away from the title) is within ±10 of `blend(cover_bg, paper, 0.55)` (proves the dimming). The same sample on a reading card equals `cover_bg` (proves the reading card is not dimmed). |
| **Evolvability** | `BookCover` is a single seam for images (R1). The lazy-grid body is ready for #21 (R10). Navigation is hoisted (R9). The use case is the only place the rows' data is assembled. | Existing `LayeringTest` (core never imports features, which covers `BookCover`). Review rule: no `LocalDate.now()`/`Clock.systemDefaultZone()` outside `ClockModule` (grep in review, or add it to `LayeringTest` as a second JVM check: no `LocalDate.now(` / `Instant.now(` without a `clock` argument under `features/`). |

## 7. Security

No new entry point, input, permission, network access or IPC. The new data shown is `borrowerName` (third-party personal data, already stored and already exposed by #18's read model, with `borrowerContact`/`notes` still excluded). Rules: never log `LentBook`/`LentOutRow`/`LibraryUiState` (data-class `toString()` includes the borrower name), and add no `Log.*`. `hilt-android-testing` is `androidTestImplementation` only and must not appear in a release classpath, and `HiltTestActivity` lives in `src/debug` only (reviewer checks both). Nothing else for QA to probe security-wise.

## 8. Test strategy

Conventions per the android-testing skill and the project's existing tests: JUnit 4, hand-written fakes (no MockK/Turbine, as in #18 section 8), `runTest` virtual time, Compose tests against the **stateless** `LibraryScreen` inside `BookshelfTheme`.

| AC | Level | What |
|---|---|---|
| **AC1: reading row shows covers and titles only for `status == reading`, no progress UI** | JVM (`ObserveLibraryRowsUseCaseTest`, fake `LibraryRepository` backed by `MutableStateFlow`s, fixed clock) | `currentlyReading` equals exactly what `observeCurrentlyReading()` emits, in order. It re-emits when the fake flow changes. |
| AC1 | Component (`LibraryRowsTest`) | A `Content` with 3 reading books: each title is displayed inside `libraryReadingCardTag(id)`. There is no node with `ProgressBarRangeInfo`, and no text containing `%` or `p.`. Section title "Currently reading" is a heading, and the trailing label reads "3 books" (also check the singular "1 book"). |
| **AC2: lent row shows dimmed cover, borrower, colour-coded due/overdue caption** | JVM | FF1. Use-case mapping: `LentBook(dueAt = today − 2)` → `Overdue(2)`, `dueAt = today` → `DueToday`, and so on. FF2 (midnight). `LibraryViewModelTest`: `Loading` first, then `Content` after the fakes emit (`Dispatchers.setMain(StandardTestDispatcher())`). |
| AC2 | Component | Lent cards for overdue, due-today, due-in-3 and due-later books: "With Priya", "Overdue 2 days", "Due today", "Due in 3 days", and "Due <localized date>" are displayed in the right cards (assert with `hasAnyAncestor(hasTestTag(libraryLentCardTag(id)))`). The merged card semantics read title + borrower + caption. |
| AC2 | Instrumented pixel | FF5 (caption colours, dimmed vs not dimmed). |
| **AC3: "See all" visible, both rows scroll horizontally** | Component | "See all · 2" is displayed and has a click action. Both row tags have `SemanticsProperties.HorizontalScrollAxisRange`. With 10 books per row on a 360dp-wide `ForcedSize`, the last card is not displayed, then `performScrollToIndex(9)` (or `performTouchInput { swipeLeft() }`) makes it displayed. FF4. |
| **AC4: populated-state tests; card taps and "See all" are no-ops that don't crash** | (a) Component | `LibraryScreen(onBookClick = { clicked += it }, onSeeAllLoansClick = { seeAll++ })`: clicking a reading card and a lent card records their ids once each, and clicking "See all" gives `seeAll == 1`. Afterwards the screen is still displayed and the node tree is unchanged (the same titles are still present). |
| AC4 | (b) Navigation, Hilt (`BookshelfNavGraphTest`, `@HiltAndroidTest`, `@UninstallModules(LibraryDataModule::class)`, `@BindValue` fake repository with one reading and one lent book) | The populated rows render through the real `LibraryViewModel` and use case. Tapping a card and tapping "See all" leaves `currentDestination.route == Home.route` and the back-stack size unchanged. The existing start-destination and search-bar tests keep passing. |
| AC4 | (c) App-level (`MainActivityLaunchTest`, `@HiltAndroidTest`) | After the splash the activity is `RESUMED` and the header is displayed (real graph). For the "neither row title exists" check (R11 on a fresh install), use `@UninstallModules(LibraryDataModule::class)` + `@BindValue` with an empty fake repository. Do not rely on the device's real `bookshelf.db` being empty, because a developer's debug data would make the test flaky. |
| R11 empty rows | Component | `Content(emptyList(), emptyList())` shows neither section. Reading-only and lent-only states show only their own section. `Loading` shows no section. The existing `LibraryScreenTest` (exactly 3 texts) passes with an empty `Content`. |
| Regression | JVM + instrumented | `LayeringTest`, FF3, the section 3 font tests, `LibraryHeaderTest` (one heading inside the header), and `LibraryScreenPixelTest` (header checks unchanged). |
| Manual | Device | No UI can create books yet (D9). Seed through App Inspection → Database Inspector (`INSERT` into `books`/`loans` on the debug build) with one overdue, one due-today and one due-in-10-days loan, plus two reading books. Check on an API 29–30 and an API 31+ device at max font size and max display size. TalkBack: "Currently reading, heading", then each card "‹title›, button, double-tap to Open book". The lent card reads "‹title›, With ‹name›, Overdue 2 days". Leave the screen open across midnight on an emulator (set the clock to 23:59) and check that "Due today" becomes "Overdue 1 day". |

## 9. Work split and tasks

**FRONTEND-ONLY:** a single Android app with no backend. Everything is in the `app` module, against #18's merged repository contract.

`frontend-developer-android`, in build order:

1. **Domain logic first (TDD):** `LoanDueStatus` + FF1, then `CurrentDateProvider` + FF2, `LibraryRows`/`LentOutRow`, and `ObserveLibraryRowsUseCase` with its JVM test against a fake repository. `di/ClockModule`. (AC1, AC2)
2. **Design tokens:** colours (R2, R5) + `BookshelfExtendedColors` + `error` mapping + FF3. Inter SemiBold asset, KDoc and tests (section 3). Typography overrides (R6). (AC2)
3. **`BookCover`** in `core/designsystem/component` (R1–R3), with previews. (AC1, AC2)
4. **`LibraryUiState`, `LibraryViewModel`, `LibraryRoute`** + `LibraryViewModelTest`. (AC1, AC2)
5. **`LibraryRows.kt`** (sections, cards, `loanDueCaption`) per R4, R9 and R10, with test tags and previews. (AC1–AC3)
6. **`LibraryScreen`**: `uiState` + callbacks + `LazyVerticalGrid` body (R10, R11). `BookshelfNavGraph` → `LibraryRoute` with stub lambdas (R9). Update `LibraryScreenTest` to pass an empty `Content`. (AC1–AC4)
7. **Hilt test infrastructure** (R13): dependency, runner, `HiltTestActivity` (debug), annotate `MainActivityLaunchTest`, `LibraryScreenPixelTest` and `BookshelfNavGraphTest`, and add the populated nav-level test. (AC4)
8. **Component and pixel tests:** `LibraryRowsTest` (AC1–AC4 rows in section 8), FF4, FF5.
9. Run `./gradlew lint test connectedAndroidTest assembleDebug`. Update README and CLAUDE.md (section 5). Attach a screenshot of the populated preview next to the `Main.dc.html` rows to the PR, noting the intended deviations (R2, R3, R5).

## 10. Risks for QA to probe

| # | Risk | Probe |
|---|---|---|
| Q1 | **Off-by-one at the due boundary**: "Due today" shown as overdue, or "Overdue 0 days". | FF1. Manual: a loan with `due_at` = today. |
| Q2 | **Stale caption after midnight** (ticker missing, or a `LocalDate.now()` call in a composable). | FF2, the manual midnight check, and the review rule in section 6. |
| Q3 | **Fake bold or fake italic**: SemiBold synthesized from Regular (asset not wired), or `FontStyle.Italic` added "to match the mock". | Section 3 measurement test, review of `Type.kt`/`BookCover`. Compare "Overdue" against the Regular "Due in": the weight difference must be real. |
| Q4 | **Placeholder colour changes on rename** or differs between this row and #21's grid. | `coverColorsFor(id)` is the only lookup (review). Same book, same colour in both previews. |
| Q5 | **Time-zone change while the screen is in the foreground**: the caption uses the old zone until the screen is re-collected (R8 known limit). | Manual: change the zone with the app open, background it for more than 5 s and return; the caption updates. Without backgrounding it does not, which is accepted and documented. |
| Q6 | **Large font / narrow screen**: borrower or caption clipped, or the cover title overflowing the 188dp cover. | FF4, manual at max font and max display size. The title must ellipsize at 4 lines, the caption may wrap, and the borrower wraps to 2 lines and then ellipsizes. |
| Q7 | **Unreadable title on dimmed lent covers.** At 55% opacity the cover title's contrast drops below AA. This is accepted per D15 (mocked opacity), and the title is still announced by TalkBack. | Visual check, recorded in section 12. Not a defect unless the product owner decides otherwise. |
| Q8 | **Hilt test runner swap breaks unrelated instrumented tests** (a test launching `MainActivity` without `@HiltAndroidTest`). | The full `connectedAndroidTest` run is green. A reviewer checks that every `MainActivity`/nav test has `HiltAndroidRule` at `order = 0`. |
| Q9 | **Lazy-row item state jumping** on re-emission because the keys are not `book.id`. | Review. Component test: re-emit with one book removed and check that the remaining cards keep their positions. |
| Q10 | **Borrower PII in logs** through data-class `toString()`. | Grep the diff for `Log.`/`println`/`Timber`. |
| Q11 | **Merge conflict with #21** on `LibraryScreen`, `LibraryUiState` and `LibraryViewModel` if both are built in parallel. | Sequence #21 after #20, or have #21 rebase onto this branch. The R10 grid body is designed for #21 to extend. |

## 11. ADR

**No ADR needed.** This is a routine feature that follows existing patterns. It adds composables, a ViewModel and a use case in the documented MVVM + use-case layering, tokens and a second weight of an already-bundled font in `core/designsystem`, and a test-only artifact from the existing Hilt framework (the same vendor and version, not a framework change or a new external system). There is no database change (it consumes #18's repository unchanged) and no external API. The decision that *would* need an ADR, adopting an image-loading library, is deliberately deferred (R1) to the ticket that first writes `coverUri`.

## 12. Open items (not blocking)

- **AA deviations from `Main.dc.html`** (same precedent as #19 H4/H6): `due_upcoming` `#A4532C` instead of terracotta `#C1693C`, cover backgrounds `#A0522C`/`#8A9566` instead of `#A8562E`/`#7A8559`, and ink instead of cream text on two cover colours. Each is a one-value revert in `colors.xml` if the product owner prefers the literal mock, but FF3 would then have to be relaxed knowingly.
- **Dimmed lent-cover title contrast** (Q7). Kept per D15. An alternative for a later ticket is to dim only the cover fill and keep the title at full opacity.
- **Upright cover title** (R3). It returns to the design intent at the Visual Spec reconciliation (Roboto Slab), or a Fraunces italic instance can be bundled later.
- **Empty rows hidden** (R11). If the product owner wants an empty "Currently reading" section with a prompt instead, that is #22-style design work.
- **`Loans.dc.html` third due tier** (muted, more than 7 days) is left to the My Loans epic (R4).
- **Image loading** (R1): the ticket that first writes `coverUri` (Add Book / scanner epic) picks the library and writes the ADR. `BookCover` is the only place that changes.
- **Visual Spec reconciliation** (user-deferred since #10): the cover palette becomes the §1.4 presets with the 155° gradient and spine line, the title becomes Roboto Slab, and overdue becomes `--terracotta` 600. These are value swaps in `colors.xml`/`Type.kt` plus a `Brush` in `BookCover`.
