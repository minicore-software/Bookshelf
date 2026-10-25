# Plan: #21 Library grid and filter chips

| Field | Value |
|---|---|
| Ticket | [#21](https://github.com/minicore-software/Bookshelf/issues/21), part of epic [#17](https://github.com/minicore-software/Bookshelf/issues/17) (Library Screen). Depends on [#18](https://github.com/minicore-software/Bookshelf/issues/18) (merged). Builds below [#20](https://github.com/minicore-software/Bookshelf/issues/20)'s rows (merged). |
| Branch | `feature/BOOKSHELF-0021-library-grid-filter-chips` |
| Stack | Android: Kotlin, Jetpack Compose, Material 3, Hilt 2.60.1, Coroutines/Flow, Room (read through #18's repository). Single `app` module. No backend. |
| Agents | `frontend-developer-android` builds it, `frontend-reviewer-android` reviews it |
| Work split | FRONTEND-ONLY |
| UX spec | Not needed. See G14. |
| API contract | No HTTP or event contract. The binding seams are #18's `LibraryRepository` (used unchanged), the new domain types in section 4, and the `LibraryScreen` callbacks. |
| ADR | None. See section 11. |
| Status | Ready to build |

---

## 1. Context

- **What the screen does today** (#20): `LibraryScreen(uiState, onSearchClick, onBookClick, onSeeAllLoansClick)` is a `Column` with a fixed `LibraryHeader` and then a `LazyVerticalGrid(GridCells.Fixed(3))` body tagged `LIBRARY_BODY_TEST_TAG`. The body currently holds only **full-span** items: `reading_section` and `lent_section`. Each section is hidden when its list is empty, and `Loading` renders neither. #20 R10 made the body a 3-column lazy grid on purpose, so that this ticket can add chips (full-span) and grid cells (span 1) to the same lazy container.
- **State today**: `LibraryUiState.Loading | Content(rows: LibraryRows)`. `LibraryViewModel(ObserveLibraryRowsUseCase)` builds its state with `stateIn(WhileSubscribed(5_000), Loading)`. `LibraryRoute` collects the state and passes it to the stateless screen. `BookshelfNavGraph` passes `{}` stubs to every callback.
- **The repository already has everything this ticket needs** (#18, `features/library/domain/LibraryRepository.kt`). No data-layer change is required:
  - `observeBooks(status: BookStatus? = null): Flow<List<LibraryBook>>` serves "All" (`null`) and the three status chips. It runs `LEFT JOIN loans ... AND returned_at IS NULL`, so every `LibraryBook.openLoan` is already filled in when the book is lent. That is what the grid's "Lent to X" caption needs. Order is `created_at DESC, id DESC`.
  - `observeLentOutBooks(): Flow<List<LentBook>>` serves the "Lent out" chip. It is an `INNER JOIN` on the open loan, ordered by `due_at ASC, id ASC`. Its KDoc already says "Lent out row and chip".
  - `observeCounts(): Flow<LibraryCounts>` provides `all`, `reading`, `finished`, `wantToRead` and `lentOut`. `lentOut` is `COUNT(DISTINCT book_id)` over open loans and does not depend on status. `all` is the sum of the three statuses. It is `distinctUntilChanged`.
  - The `index_loans_book_id_returned_at` index covers both join predicates.
- **Design source**: `project/Main.dc.html`, "Library" board, after the lent-out row:
  - **Chip row**: `display:flex; gap:8px; padding:18px 20px 10px; overflow-x:auto`. Each chip has `padding:7px 16px; border-radius:20px; font-size:13px`. The **selected** chip has a forest `#2F4538` fill, paper `#F3EEE3` text and weight 600. An **unselected** chip has a paper-bright `#FBF8F1` fill, a 1px line `#E3DCC9` border and ink-soft `#4A4640` text at weight 400. The labels are "All · 27", "Reading · 2", "Finished · 19", "Want to read · 6" and "Lent out · 3".
  - **Section title** "Your library": `padding:12px 20px 8px`, Fraunces 600, 18px.
  - **Grid**: `grid-template-columns: repeat(3, minmax(0,1fr)); gap:14px; padding:0 20px 24px`. Each card is a column with gap 6px. The cover has `aspect-ratio:3/4.3`, `border-radius:7px` and shadow `0 2px 6px rgba(35,31,32,.1)`. **The grid covers have no title overlay.** The title sits below the cover in Inter 11.5px ink-soft `#4A4640`, line-height 1.3. Lent books add "Lent to Priya" in Inter 10px terracotta `#C1693C`. Grid covers are **not** dimmed. The one lent book in the mock's grid is drawn at full opacity.
  - **Status dot**: `position:absolute; top:6px; right:6px; width:9px; height:9px; border-radius:50%; border:2px solid`. There is no `box-sizing`, so the outer diameter is **13px** (a 9px fill plus a 2px ring on each side).
- **The status-dot colour mapping, taken from the mock** (the ticket asked for this to be pulled from `Main.dc.html` before implementation):

  | Dot | Fill | Ring (2px) | Mock evidence |
  |---|---|---|---|
  | `reading` | `#3F7A5B` (green) | paper `#F3EEE3` | "The Hidden Life of Trees" and "Project Hail Mary", the same two books as the "Currently reading" row and "Reading · 2" |
  | `finished` | `#C9A227` (gold) | paper `#F3EEE3` | "Klara and the Sun", "Braiding Sweetgrass". CLAUDE.md: "gold (rating/finished)" |
  | `want_to_read` | `#9B8F7A` (muted taupe) | paper `#F3EEE3` | "Pachinko" (the only remaining status) |
  | lent overlay | paper `#F3EEE3` (hollow) | terracotta `#C1693C` | "Sapiens", the only grid card with a "Lent to Priya" caption |

- **An inconsistency in the mock's sample data**: "Klara and the Sun" is lent to Diego in the "Lent out" row, but its grid card shows a finished dot and no caption. The ticket's AC is explicit ("'Lent to X' caption for books with an open loan"), so the rule (G6) wins over that one sample card. This is recorded so that no one "fixes" the implementation to match the mock.
- **Epic decisions in force**: D9 (read-only), D10 ("lent" is an overlay used for the chip and the grid dot), D15 (chips and grid built exactly as mocked), D18 (card taps are no-ops). **#20 precedents**: R1 (placeholder covers only, `BookCover` is the single image seam), R2 (the cover colour comes from `bookId`), R5 (extended colours, and an AA-passing darker text colour where the mock fails), R6 (Inter Regular and SemiBold are both bundled, so no weight is synthesized), R9 (hoisted no-op callbacks), R11 (empty sections are hidden), R13 (Hilt instrumented-test infrastructure with a `@BindValue` fake repository).

## 2. Key decisions

| # | Decision | Why / alternatives |
|---|---|---|
| G1 | **Each chip maps to exactly one repository query, chosen in a new domain use case.** `ObserveLibraryGridUseCase(filter)`: `All` → `observeBooks(null)`. `Reading`, `Finished` and `WantToRead` → `observeBooks(status)`. `LentOut` → `observeLentOutBooks().map { it.map { lent -> LibraryBook(lent.book, lent.loan) } }`. **`Book.status` is never consulted for `LentOut`.** | The AC says the lent-out chip uses the open-loan join, and #18 built `observeLentOutBooks()` for exactly that purpose. Putting the mapping in one pure-Kotlin `when` makes the rule JVM-testable with a fake that records which method was called (FF1). Mapping `LentBook` to `LibraryBook` lets the grid render a single type, and `openLoan` is non-null for every lent item, so the caption and dot rules (G6) apply unchanged. **Rejected:** `observeBooks(null).filter { it.openLoan != null }`. It is also join-based, but it duplicates the query #18 designated for the chip, and it pulls the whole library into memory to filter it. |
| G2 | **The use case emits the filter, the counts and the books together:** `LibraryGrid(filter, counts, books)` = `combine(repository.observeCounts(), booksFor(filter))`. | The chips and the grid come from one emission, so the highlighted chip, its count and the visible books can never disagree for a frame. Counts update live because `observeCounts()` re-emits whenever `books` or `loans` changes (AC1). **Rejected:** a separate `ObserveLibraryCountsUseCase` that only passes the call through. It is one more class, and it allows chip and grid emissions to drift apart. |
| G3 | **The selected filter lives in `LibraryViewModel` and is backed by `SavedStateHandle`:** `savedStateHandle.getStateFlow(KEY_FILTER, LibraryFilter.All)`, `fun onFilterSelected(filter: LibraryFilter) { savedStateHandle[KEY_FILTER] = filter }`. The grid flow is `filter.flatMapLatest { observeLibraryGrid(it) }` (opt in to `ExperimentalCoroutinesApi`). `uiState = combine(observeLibraryRows(), gridFlow) { rows, grid -> Content(rows, grid) }`, with the same `stateIn(WhileSubscribed(5_000), Loading)` as before. | The ViewModel already survives rotation. `SavedStateHandle` also restores the chip after process death at almost no cost: `@HiltViewModel` injects it, and an enum is `Serializable`. `flatMapLatest` cancels the previous filter's Room query when the user taps another chip, so quick taps cannot let an old query win. `stateIn` keeps the last `Content` while the new filter's first emission is pending, so the screen never falls back to `Loading` on a chip tap. **The selected chip is read from `grid.filter` (the emitted value), not from the raw state.** The cost is a highlight delay of one Room round-trip, a few milliseconds on a local DB, in exchange for consistency (G2). **Rejected:** `rememberSaveable` filter state in the composable plus a filter parameter on the ViewModel (that splits state ownership). **Rejected:** a plain `MutableStateFlow` (the selection is lost on process death). |
| G4 | **`LibraryUiState.Content(rows: LibraryRows, grid: LibraryGrid = LibraryGrid.Empty)`.** `LibraryGrid.Empty` = `LibraryGrid(LibraryFilter.All, LibraryCounts(0,0,0,0,0), emptyList())`. The ViewModel always passes a real `grid`. | The default keeps the roughly 20 existing `Content(rows)` call sites in #20's tests compiling and semantically unchanged, because an empty grid renders nothing (G5). That avoids mechanical churn across tests this ticket does not otherwise touch. The KDoc states that production code must pass `grid`. **Rejected:** a nullable `grid` (it gives a test-only meaning to `null` in production state). |
| G5 | **Visibility.** When `counts.all == 0`, the chip row, the "Your library" title and the grid cells are **all hidden**, as is everything under `Loading`. When `counts.all > 0`, the chips and the title always render. A filter that matches nothing (for example "Lent out · 0") renders the chips and the title with **no cells and no message**. Chips with a count of 0 stay enabled. | This follows #20 R11 (empty sections are hidden). It keeps the existing "exactly 3 texts" test for an empty library valid without changes, and it leaves #22 a clean slot for the empty-shelf message. #22's AC allows chips to be "inert (0 counts) or hidden". This ticket picks hidden because it is the natural extension of R11, and #22 can flip it with one condition. Showing no message for an empty filter avoids inventing copy the mock does not contain, and the chip's own "· 0" already explains the empty grid. Recorded in section 12. |
| G6 | **The status dot and caption rule** (pure function, `features/library/ui/LibraryStatusDot.kt`): `fun LibraryBook.statusDotKind(): StatusDotKind = if (openLoan != null) Lent else when (book.status) { Reading -> Reading; Finished -> Finished; WantToRead -> WantToRead }`. Every book with `openLoan != null` gets the lent dot **and** "Lent to ‹borrowerName›", under every chip, not only "Lent out". The dot's `contentDescription` is always the book's **status** label ("Reading", "Finished" or "Want to read"), including for lent books. | D10: lent is an overlay that replaces the dot. The mock's Sapiens card shows the lent dot instead of a status dot. Visually that hides the underlying status, so the semantics keep it. TalkBack reads "Sapiens, Finished, Lent to Priya, button". Lent status is carried by the caption text, so the overlay does not depend on colour alone. An exhaustive `when` over `BookStatus` makes a future status a compile error. **Rejected:** two dots, or status plus ring (not in the mock, D15). |
| G7 | **Colour tokens.** New entries in `colors.xml` under `<!-- Library grid (BOOKSHELF-0021) -->`: `status_reading` `#3F7A5B`, `status_want_to_read` `#9B8F7A`, `gold` `#C9A227`, `terracotta` `#C1693C`. The last two are the CLAUDE.md palette tokens, and this is their first use. `BookshelfExtendedColors` gains `statusReading`, `statusFinished` (= `gold`), `statusWantToRead`, `lentAccent` (= `terracotta`, used for the lent dot's ring) and `lentCaption` (= `R.color.due_upcoming` `#A4532C`). The chips use the existing M3 roles: selected fill `primary` (forest), selected text `onPrimary` (paper), unselected fill `surfaceContainerLowest` (paper-bright), border `outlineVariant` (line), text `extendedColors.inkSoft`. | **The "Lent to X" caption deviates from the mock on purpose.** Terracotta `#C1693C` on paper is **3.39:1** and fails AA for 10sp text. `due_upcoming` `#A4532C` is the same hue darkened to 4.71:1. This is exactly #20 R5's fix for the same terracotta-as-small-text case, so the existing token is reused rather than duplicated, and it is already covered by FF3. **The dot fills keep the mock's values** even though two of them fall below WCAG 1.4.11's 3:1 non-text contrast against their paper ring (finished 2.09:1, want-to-read 2.75:1). The ticket explicitly instructs pulling the four values from the mock, the dot is decorative support for information that is also exposed as text (semantics, and the chip filter), and the lent overlay is told apart by shape (hollow ring) and text, not only by hue. Recorded in section 12. Chip text passes AA: paper on forest is 8.94:1, ink-soft on paper-bright is 8.83:1. |
| G8 | **Typography.** Add three named styles to `LibraryTextStyles` in `Type.kt`: `chip` = Inter Normal 13sp (the selected chip uses `.copy(fontWeight = SemiBold)`, a real bundled weight, #20 R6). `gridTitle` = Inter Normal 11.5sp, line height 15sp. `gridLentCaption` = Inter Normal 10sp, line height 13sp. The "Your library" title reuses `titleMedium` (Fraunces SemiBold 18sp, as in #20's section titles). | The mock's sizes are not M3 slots, and #20 established `LibraryTextStyles` for exactly this purpose. No `FontFamily` or `TextStyle` literal goes in the feature (the #10 review rule). Every size is in `sp`, so it scales with font size. |
| G9 | **`BookCover` gains a variant instead of being forked:** `enum class BookCoverVariant { Row, Grid }`, with a parameter `variant: BookCoverVariant = BookCoverVariant.Row`. `Row` keeps today's behaviour (132×188dp, radius 8dp, shadow 4dp, title overlay). `Grid` uses `fillMaxWidth().aspectRatio(3f / 4.3f)`, radius 7dp, shadow 2dp and **no title overlay**. Palette lookup, `dimmed` and the test tag are unchanged. | It stays the single cover seam (#20 R1), so the image-loading branch will still go in one place later, and the same book keeps the same colour in the rows and the grid (R2/Q4). The defaults keep every #20 call site unchanged. **Rejected:** having the caller override the size with a modifier. `BookCover` applies `.size(132, 188)` after the caller's modifier, so the outcome would depend on constraint-ordering subtleties, and the title overlay still could not be removed. **Rejected:** a second `GridBookCover` composable (it duplicates the palette and the future image branch). |
| G10 | **Layout inside the existing `LazyVerticalGrid`** (no new scroll container, no `contentPadding` on the grid). After `lent_section` comes: <br>full-span `filter_chips`: a `Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp), spacedBy(8.dp))` (the top and bottom are the mock's 18/10 minus the 8dp that each 48dp touch target adds, G11) <br>full-span `grid_title`: "Your library", `padding(start 20, top 12, end 20, bottom 8)`, `heading()` semantics <br>one span-1 item per book, `items(books, key = { "grid_book_${it.book.id}" }, contentType = { "grid_book" })` <br>full-span `grid_bottom`: `Spacer(height = 10.dp)` (the mock's 24dp bottom padding minus the cells' 14dp) <br>**Column gutters come from per-cell padding by column index**, `column = index % 3` (valid because every item before the cells is full-span, so cell 0 always starts a line): col 0 `start 20dp, end 8/3dp`; col 1 `start 34/3dp, end 34/3dp`; col 2 `start 8/3dp, end 20dp`; every cell `bottom 14dp`. With equal `W/3` cells this gives exactly 20 \| w \| 14 \| w \| 14 \| w \| 20. Put the constants in one `internal fun gridCellPadding(column: Int): PaddingValues` with a JVM test (FF4). | `contentPadding(horizontal = 20.dp)` on the grid would also inset the full-span `LazyRow`s and the chip row, which have to scroll edge to edge as in the mock. Grid-level `horizontalArrangement`/`verticalArrangement` spacing would add gaps between #20's full-span sections and change their layout. **Rejected:** chunking the books into rows of 3 inside full-span items (keys would shift on every insert, and laziness and item state would work per row, not per book). **Rejected:** a negative-margin `Modifier.layout` on full-span rows (fragile, and it relies on drawing outside measured bounds). Keys are ID-based and prefixed so that they never collide with section keys. A lazy item keeps its saveable state by key, so the chip row's `rememberScrollState` survives scrolling. Chips are **not sticky**: in the mock they scroll with the content. |
| G11 | **The chip is a small private composable, not M3 `FilterChip`.** `LibraryFilterChip(label, selected, onClick)` = `Box(Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(20.dp)).background(...).border(if (!selected) 1.dp line else none).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp))` with a single-line `Text` (`maxLines = 1`, `softWrap = false`). The row has `selectableGroup()`. | This is a **single-choice** filter. M3 `FilterChip` uses checkbox-style toggle semantics ("checked / not checked"), which is wrong for a mutually exclusive choice, and its internal label padding, height and default checkmark icon would all need overriding to match the mock. `selectable` with `Role.RadioButton` inside a `selectableGroup` is the standard Compose single-choice pattern: TalkBack says "Reading · 2, selected, radio button, 2 of 5". `minimumInteractiveComponentSize` gives a 48dp touch target, about 31dp visually as mocked, and G10 absorbs the extra 8dp of layout height. **Rejected:** `FilterChip` with overridden semantics (merging a role into a component that sets its own is fragile). |
| G12 | **Grid card.** `Column(Modifier.then(gridCellPadding(col)).clickable(role = Role.Button, onClickLabel = stringResource(library_book_click_label)) { onBookClick(id) }.semantics(mergeDescendants = true).testTag(libraryGridCardTag(id)), spacedBy(6.dp))` { `Box { BookCover(variant = Grid); LibraryStatusDot(kind, Modifier.align(TopEnd).padding(top = 6.dp, end = 6.dp)) }`; title `Text(gridTitle, inkSoft, maxLines = 2, Ellipsis)`; if lent, caption `Text(stringResource(library_grid_lent_to, borrowerName), gridLentCaption, lentCaption, maxLines = 1, Ellipsis)` }. `LibraryStatusDot`: `Box(size(13.dp).clip(CircleShape).background(ring).padding(2.dp).clip(CircleShape).background(fill))`, where for status dots ring = paper and fill = status colour, and for lent ring = terracotta and fill = paper. Semantics: `contentDescription` = status label (G6), plus a test-only `SemanticsPropertyKey<StatusDotKind>("LibraryStatusDot")` so component tests can assert the kind without pixels. | Click handling and labels match #20's cards (R9), and `onBookClick` is reused, so `BookshelfNavGraph`'s stub already covers grid taps (D18). The dot size and offset are the mock's CSS outer box (section 1). Cover colour comes from `bookId` via `BookCover` (#20 R2). |
| G13 | **Wiring.** `LibraryScreen` gains `onFilterSelected: (LibraryFilter) -> Unit = {}` (the defaulted-callback pattern from #19 H7 and #20 R9). `LibraryRoute` passes `viewModel::onFilterSelected`. `BookshelfNavGraph` needs **no change**, because the filter is internal to the screen and grid taps reuse `onBookClick = {}`. | The filter is screen state, not navigation, so the nav graph stays the only place the later Book Detail ticket edits. |
| G14 | **No UX spec needed.** | `Main.dc.html` fixes every chip and grid value: paddings, gaps, radii, aspect ratio, sizes, weights, colours and the dot geometry. The one open item the ticket flags, the dot colour table, is a lookup in the mock, and it is done in section 1 with evidence for each colour. The remaining calls are the AA fix for the caption (G7, #20 precedent), the dot contrast that stays as mocked (G7, recorded), the empty-filter rendering (G5), the hidden chips for an empty library (G5, which #22 owns), and the mock's Klara inconsistency (section 1, where the AC wins). Each is an accessibility rule, a precedent or a one-line reversal. This ticket adds no new flow, and the only new interaction is single-choice chips, a standard pattern. |

## 3. Out of scope

- An empty-shelf message, an illustration, or a decision on inert chips for a 0-book library: #22.
- FAB and bottom navigation (the mock's 92px bottom padding exists for them): not built in this ticket.
- Sticky chips, sort controls, search filtering, multi-select filters, and animated item reordering (`animateItem`): none of these are in the mock or the AC.
- Book Detail navigation: card taps stay `onBookClick = {}` (D18).
- Image loading: still deferred (#20 R1). `BookCover`'s `Grid` variant ignores `coverUri` just as `Row` does.

## 4. New and changed types (binding)

`features/library/domain/` (pure Kotlin: no Android or Compose imports)

```kotlin
/** One chip. Order of entries = chip order on screen. */
enum class LibraryFilter { All, Reading, Finished, WantToRead, LentOut }

/** Chip badge for [filter]. `LentOut` -> [LibraryCounts.lentOut] (open-loan count, not a status). */
fun LibraryCounts.countFor(filter: LibraryFilter): Int

/** One emission = one consistent chip-row + grid state (G2). */
data class LibraryGrid(
    val filter: LibraryFilter,
    val counts: LibraryCounts,
    val books: List<LibraryBook>,   // repository order, never re-sorted
) {
    companion object { val Empty: LibraryGrid }  // All, zero counts, empty list
}

class ObserveLibraryGridUseCase @Inject constructor(private val repository: LibraryRepository) {
    operator fun invoke(filter: LibraryFilter): Flow<LibraryGrid>   // G1, G2
}
```

`features/library/ui/`

```kotlin
sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Content(val rows: LibraryRows, val grid: LibraryGrid = LibraryGrid.Empty) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeLibraryRows: ObserveLibraryRowsUseCase,
    observeLibraryGrid: ObserveLibraryGridUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val uiState: StateFlow<LibraryUiState>
    fun onFilterSelected(filter: LibraryFilter)
}

enum class StatusDotKind { Reading, Finished, WantToRead, Lent }
internal fun LibraryBook.statusDotKind(): StatusDotKind            // G6

@Composable fun LibraryScreen(
    uiState: LibraryUiState,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onBookClick: (bookId: Long) -> Unit = {},
    onSeeAllLoansClick: () -> Unit = {},
    onFilterSelected: (LibraryFilter) -> Unit = {},
)
```

`core/designsystem/component/`

```kotlin
enum class BookCoverVariant { Row, Grid }
@Composable fun BookCover(bookId: Long, title: String, coverUri: String?, modifier: Modifier = Modifier,
                          dimmed: Boolean = false, variant: BookCoverVariant = BookCoverVariant.Row)
```

- Ordering is the repository's: `created_at DESC` for All and the status chips, `due_at ASC` for Lent out (the most urgent loan first, the same order as #20's lent row). The use case does not re-sort. Switching between "Finished" and "Lent out" therefore changes the order rule as well as the set. That is intended and recorded (Q7).
- A lent book appears under its status chip **and** under "Lent out", in both cases with the lent dot and caption. `counts.all` is the sum of the three statuses, and `lentOut` is independent (`LibraryCounts` KDoc).
- `borrowerName` is shown verbatim, ellipsized to one line. It is never logged.
- Compatibility: additive. `LibraryRepository`, the DAOs and the schema are unchanged. `LibraryUiState.Content` and `LibraryScreen` change in a source-compatible way (defaulted parameters). `!` does not apply.

## 5. File changes

| File | Change |
|---|---|
| `features/library/domain/LibraryFilter.kt` | **New.** `LibraryFilter` and `LibraryCounts.countFor`. |
| `features/library/domain/LibraryGrid.kt` | **New.** `LibraryGrid` and `Empty`. |
| `features/library/domain/ObserveLibraryGridUseCase.kt` | **New** (G1, G2). |
| `features/library/ui/LibraryUiState.kt` | `Content` gains `grid` (G4). Update the KDoc. |
| `features/library/ui/LibraryViewModel.kt` | Inject the grid use case and `SavedStateHandle`. Add the filter flow, `flatMapLatest`, `combine` and `onFilterSelected` (G3). |
| `features/library/ui/LibraryRoute.kt` | Pass `onFilterSelected = viewModel::onFilterSelected`. |
| `features/library/ui/LibraryScreen.kt` | Add the `onFilterSelected` param. Add the `filter_chips`, `grid_title`, grid-cell and `grid_bottom` items (G5, G10). Update the KDoc and add a populated preview. |
| `features/library/ui/LibraryGridSection.kt` | **New.** `LibraryFilterChips`, private `LibraryFilterChip`, `LibraryGridTitle`, `LibraryGridCard`, `gridCellPadding(column)`, `@Composable filterLabel(filter, count)`. Test tags: `LIBRARY_FILTER_CHIPS_TEST_TAG`, `libraryFilterChipTag(filter)`, `LIBRARY_GRID_TITLE_TEST_TAG`, `libraryGridCardTag(id)`, `libraryGridLentCaptionTag(id)`, `libraryGridStatusDotTag(id)`. Previews: populated with all four dot kinds, a 2× font scale, a 320dp width, and the "Lent out" filter selected. (The file is named differently from the domain `LibraryGrid` to avoid two `LibraryGrid` files.) |
| `features/library/ui/LibraryStatusDot.kt` | **New.** `StatusDotKind`, `statusDotKind()`, `LibraryStatusDot` composable, the semantics key (G6, G12). |
| `core/designsystem/component/BookCover.kt` | Add the `variant` param and `BookCoverVariant` (G9). Add a `Grid` preview. Update the KDoc (it already says "from #21, the library grid"). |
| `core/designsystem/Color.kt` | Five new `BookshelfExtendedColors` fields (G7). |
| `core/designsystem/Type.kt` | `LibraryTextStyles.chip`, `gridTitle`, `gridLentCaption` (G8). |
| `res/values/colors.xml` | `status_reading`, `status_want_to_read`, `gold`, `terracotta` (G7). |
| `res/values/strings.xml` | Under `<!-- Library grid (BOOKSHELF-0021) -->`: `library_filter_all` "All · %d", `library_filter_reading` "Reading · %d", `library_filter_finished` "Finished · %d", `library_filter_want_to_read` "Want to read · %d", `library_filter_lent_out` "Lent out · %d", `library_grid_title` "Your library", `library_grid_lent_to` "Lent to %s", `library_status_reading` "Reading", `library_status_finished` "Finished", `library_status_want_to_read` "Want to read". |
| Tests | Section 8. This includes updating `FakeLibraryRepository` in `BookshelfNavGraphTest` (Q1) and the ViewModel test's fake. |
| `CHANGELOG.md` | One line under `[Unreleased]`, at integration time. |

Not touched: `LibraryRepository`, the DAOs, entities and schema, `LibraryHeader.kt`, `LibraryRows.kt` (the #20 sections), `BookshelfNavGraph.kt`, `di/`, Gradle files (no new dependency: `SavedStateHandle` already comes through the existing lifecycle/Hilt artifacts), and `CLAUDE.md` (no structural change).

## 6. Quality attributes and fitness functions

| Attribute | How the design serves it | Fitness function (developer implements) |
|---|---|---|
| **Correctness of the filter** (lent = join, not status) | One `when` in the use case chooses the query (G1). Chips and grid come from one emission (G2). | **FF1** (JVM, `ObserveLibraryGridUseCaseTest`, hand-written fake `LibraryRepository` that records calls and is backed by `MutableStateFlow`s): `LentOut` calls `observeLentOutBooks()` and **never** `observeBooks(...)`. A `Finished` book with an open loan is in the `LentOut` result, and a `Reading` book without a loan is not. Each status filter calls `observeBooks(thatStatus)`, and `All` calls `observeBooks(null)`. A new counts emission re-emits `LibraryGrid` with the same filter. `countFor` maps every enum entry (iterate `LibraryFilter.entries`). |
| **State consistency and resilience to rapid taps** | `flatMapLatest` plus "selected chip = `grid.filter`" (G3). | **FF2** (JVM, `LibraryViewModelTest`, `StandardTestDispatcher`): `Loading` comes first, then `Content(grid.filter = All)`. After `onFilterSelected(Finished)` and then `onFilterSelected(LentOut)` without advancing, only `LentOut` content is emitted, and the state never returns to `Loading`. A new ViewModel built from a `SavedStateHandle(mapOf(KEY_FILTER to LentOut))` starts on `LentOut`. |
| **Accessibility** | Single-choice radio semantics and a 48dp target on the chips (G11). Text tokens are AA, and the caption deviation is deliberate (G7). Status stays in the semantics for lent books (G6). The grid title is a heading. `maxLines` guards protect against font scaling. | **FF3** (extend JVM `LibraryContrastTest`): assert ≥ 4.5:1 for `paper` on `forest` (selected chip) and `ink_soft` on `paper_bright` (unselected chip). `due_upcoming` on paper, now also the lent caption, is already asserted. Add a comment in the test saying that the dot fills are intentionally not asserted (G7). Instrumented: every chip has `assertHeightIsAtLeast(48.dp)`, and at `FontScale(2f)` with `ForcedSize(320.dp, 800.dp)` every chip label is one line and the chip row scrolls. |
| **Visual fidelity** | Mock values are tokenized (G7–G10). Gutter math lives in one helper (G10). | **FF4** (JVM, `GridCellPaddingTest`): for each column, the start and end paddings combine to a 20dp outer margin and a 14dp gap between columns: `end(c) + start(c+1) == 14`, `start(0) == end(2) == 20`, and `start(c) + end(c)` is the same for every column, so the content widths are equal. **FF5** (instrumented pixel, extend `LibraryRowsPixelTest` or add `LibraryGridPixelTest`): with a fixed `Content` holding one book of each dot kind, the centre pixel of each dot is within ±10 per channel of `#3F7A5B`, `#C9A227`, `#9B8F7A` and paper (lent), and a pixel on the lent dot's ring is within ±10 of `#C1693C`. The selected chip's fill pixel matches forest. A grid cover pixel is not dimmed: it equals `cover_bg`. |
| **Evolvability** | `BookCover` is still the single image seam (G9). The filter is one enum and one `when`. #22 controls visibility through one condition (G5). | The existing `LayeringTest` (the `BookCover` variant stays free of `features` imports). The exhaustive `when` over `BookStatus` and `LibraryFilter` makes a new value a compile error. |

## 7. Security

There is no new entry point, permission, network access, IPC or persisted data. The only new user-visible data is `borrowerName` in the grid caption. It is third-party personal data that #18's read model and #20's row already expose. The rules from #20 section 7 still apply: never log `LibraryGrid`, `LibraryBook` or `LibraryUiState` (a data class's `toString()` includes the borrower name), and add no `Log.*`, `println` or `Timber` call. `SavedStateHandle` stores only the `LibraryFilter` enum, never book or loan data. There is nothing further for QA to probe on the security side.

## 8. Test strategy

The conventions are #20's: JUnit 4, hand-written fakes (no MockK or Turbine), `runTest` virtual time, component tests against the **stateless** `LibraryScreen` inside `BookshelfTheme`, and Hilt instrumented tests with `@UninstallModules(LibraryDataModule::class)` + `@BindValue` (#20 R13).

| AC | Level | What |
|---|---|---|
| **AC1: chips show live counts from the repository** | JVM | FF1 (counts re-emission, `countFor`). FF2 (the ViewModel passes the counts through). |
| AC1 | Component (`LibraryGridTest`) | With `Content(rows, LibraryGrid(All, LibraryCounts(27, 2, 19, 6, 3), books))`, the five chips read exactly "All · 27", "Reading · 2", "Finished · 19", "Want to read · 6" and "Lent out · 3", in that order (compare the left bounds). Only the `All` chip is `assertIsSelected()`, and the others are `assertIsNotSelected()`. |
| AC1 | Instrumented, Hilt (`LibraryGridFilteringTest`, `@HiltAndroidTest`, `HiltTestActivity` hosting `LibraryRoute`, `@BindValue` fake repository whose counts are a `MutableStateFlow`) | **Live**: the test pushes new counts into the fake (`lentOut` 1 → 2), and the chip text updates to "Lent out · 2" without any interaction. |
| **AC2: tapping a chip filters by the correct query** | JVM | FF1 (query selection). FF2 (`onFilterSelected` changes `grid.filter`). |
| AC2 | Component | `performClick` on each chip calls `onFilterSelected` with the matching `LibraryFilter` exactly once. Clicking the already-selected chip still calls it, which is harmless. |
| AC2 | Instrumented, Hilt (`LibraryGridFilteringTest`), **the end-to-end chip-filtering test the AC requires** | The fake is status-aware: `observeBooks(status)` filters its seed list, `observeLentOutBooks()` returns only books with a loan, and counts are derived from the seed. Seed: R1 (Reading, no loan), F1 (Finished, **lent to Priya**), F2 (Finished), W1 (WantToRead). Steps: tap "Lent out" → only F1's grid card exists. Tap "Finished" → F1 and F2 exist, F1 still shows "Lent to Priya" and the lent dot kind. Tap "Want to read" → only W1. Tap "All" → all four. Check each step with one `libraryGridCardTag(id)` per book (`assertExists` / `assertDoesNotExist`), or with a custom `SemanticsMatcher` on the test-tag prefix, so that #20's rows (which show the same books) do not interfere. |
| **AC3: grid renders cover, title, status dot and the "Lent to X" caption where applicable** | JVM | `statusDotKind()`: all three statuses without a loan, and every status **with** a loan → `Lent` (G6). FF4 (gutters). |
| AC3 | Component | For each book, inside `libraryGridCardTag(id)`: the cover tag `BOOK_COVER_TEST_TAG_PREFIX + id` exists (use `hasAnyAncestor`, because the same cover tag can also exist in #20's rows), the title text is displayed, and the dot's `LibraryStatusDot` semantics value equals the expected kind. "Lent to Priya" exists **only** in the lent book's card. A book with no loan has no node with `libraryGridLentCaptionTag(id)`. The merged card semantics read title + status + caption. **3 columns**: cards 0–2 share the same top and cards 3–5 sit below them. The grid title "Your library" is a heading. |
| AC3 | Instrumented pixel | FF5 (dot colours, ring, no dimming in the grid, selected chip fill). |
| **AC4: Compose UI tests cover chip filtering; card taps are no-ops that don't crash** | Component | `LibraryScreen(onBookClick = { clicked += it })`: clicking two grid cards records their ids once each. Afterwards the screen is still displayed and the same cards are present. |
| AC4 | Instrumented, Hilt (extend `BookshelfNavGraphTest`) | With a populated fake, tapping a grid card and tapping a chip both leave `currentDestination.route == Home.route` and the back-stack size unchanged. |
| G5 visibility | Component | `Content(rows, LibraryGrid.Empty)` shows no chip row, no "Your library" and no cards. The existing `LibraryScreenTest` assertion of exactly 3 texts passes **unchanged**. `Loading` shows no chips. When `counts.all > 0` and the books list is empty under `LentOut`, the chips and the title show and there are zero cards. |
| Regression | All | #20's `LibraryRowsTest`, `LibraryRowsPixelTest`, `LibraryScreenTest`, `LibraryHeaderTest`, `LayeringTest`, `LibraryContrastTest` and `BookCover` previews all still pass with the `Row` default. |
| Manual | Device | Seed through the Database Inspector: 10 or more books across all statuses, two of them lent, one of those finished. Check at max font and display size on API 29–30 and on API 31+. TalkBack: "Reading · 2, not selected, radio button" on the chips, and "Sapiens, Finished, Lent to Priya, button, double-tap to Open book" on a card. Return a loan in the Inspector (`UPDATE loans SET returned_at = ...`) while the "Lent out" chip is selected: the card disappears and the count drops without a reload. Rotate, and kill the process from the developer options ("Don't keep activities" or `adb shell am kill`): the selected chip is restored. |

## 9. Work split and tasks

**FRONTEND-ONLY.** This is a single Android app with no backend. Everything is in the `app` module, against #18's merged repository contract, which is used unchanged.

`frontend-developer-android`, in build order:

1. **Domain (TDD):** `LibraryFilter` + `countFor`, `LibraryGrid`, `ObserveLibraryGridUseCase` + FF1. (AC1, AC2)
2. **ViewModel:** `SavedStateHandle` filter, `flatMapLatest`, `combine`, `onFilterSelected`. `LibraryUiState.Content.grid`. `LibraryRoute` wiring. FF2. Update the fake in `LibraryViewModelTest`. (AC1, AC2)
3. **Design tokens:** colours and extended colours (G7), `LibraryTextStyles` (G8), FF3. (AC3)
4. **`BookCover` `Grid` variant** (G9), with a preview. (AC3)
5. **`LibraryStatusDot.kt`** (G6, G12) with the JVM test for `statusDotKind()`. **`LibraryGridSection.kt`**: chips (G11), title, card (G12), `gridCellPadding` + FF4. (AC1–AC3)
6. **`LibraryScreen`**: the `onFilterSelected` param, the new lazy items and the visibility rules (G5, G10). Previews. (AC1–AC4)
7. **Tests:** `LibraryGridTest` (component), `LibraryGridFilteringTest` (Hilt, end to end), the `BookshelfNavGraphTest` extension with the fake made status-aware (Q1), FF5. (AC1–AC4)
8. Run `./gradlew lint test connectedAndroidTest assembleDebug`. Attach a screenshot of the populated preview next to the `Main.dc.html` chips and grid to the PR, noting the intended deviations (G7 caption colour, and the section 1 Klara rule).

## 10. Risks for QA to probe

| # | Risk | Probe |
|---|---|---|
| Q1 | **Duplicate nodes**: the same book appears in #20's rows and in the grid, so `onNodeWithText(title)` / `onNodeWithTag(book_cover_id)` matches two nodes and existing or new tests fail or assert against the wrong node. `BookshelfNavGraphTest`'s current `FakeLibraryRepository` returns the reading list for `observeBooks(any)` and zero counts, so it will render the grid (or hide it) inconsistently. | Make the fake status-aware with real counts. Scope every grid assertion to `libraryGridCardTag(id)` with `hasAnyAncestor`. Run the full `connectedAndroidTest` suite. |
| Q2 | **Lent filter falls back to status** (for example someone "simplifies" it to `observeBooks(...)` or filters by a status). | FF1 (fails if `observeBooks` is called for `LentOut`). E2E: a *Finished* lent book appears under "Lent out". |
| Q3 | **The chip highlight and the grid disagree** for a frame, or quick taps leave an older filter's books on screen. | FF2. Manual: rapid taps across all five chips. |
| Q4 | **Counts drift from the grid**: "Lent out · N" ≠ number of cards, or the status counts don't sum to "All". | Component and E2E: with each chip selected, the number of cards equals that chip's count. Manual after returning a loan in the Inspector. |
| Q5 | **Gutter rounding**: unequal gaps or an asymmetric margin at odd widths (320, 360, 393, 411dp). | FF4. A visual check at those widths: the 20dp margins and 14dp gaps look even, and no card is clipped. |
| Q6 | **Large font / narrow screen**: chip labels wrap or clip, a grid title overflows into the next row, or the caption pushes cards out of alignment. | The FF3 instrumented checks. Manual at max font: chips stay on one line and the row scrolls, titles ellipsize at 2 lines, and the caption at 1 line. Rows may differ in height. That is acceptable, and the grid aligns the tops of each line. |
| Q7 | **Ordering surprise**: "Lent out" is ordered by due date (most urgent first) and the others by date added. | This is intended (section 4). QA confirms the behaviour, and the product owner may decide otherwise later (section 12). |
| Q8 | **Selection lost** on rotation or process death, or **restored to a filter that is now empty**. | The manual check in section 8 and FF2's `SavedStateHandle` case. An empty restored filter shows the chips and no cards (G5), and it must not crash. |
| Q9 | **Lazy key collision** between grid cells and section items, or between two books. | Review: keys are `"grid_book_$id"`, and section keys are distinct literals. A component test re-emits with a book removed and the remaining cards keep their positions. |
| Q10 | **Dot kinds are hard to tell apart for low-vision users** (finished and want-to-read fall below 3:1 against the ring). | This is accepted as mocked (G7). TalkBack still announces the status. Recorded in section 12. It is not a defect unless the product owner decides otherwise. |
| Q11 | **Borrower PII in logs.** | Grep the diff for `Log.`, `println` and `Timber`. |

## 11. ADR

**No ADR is needed.** This is a routine feature addition that follows the patterns set in #20: a domain use case, ViewModel state, composables in `features/library/ui`, design tokens in `core/designsystem`, and a variant parameter on an existing shared component. There is no database change (#18's repository and schema are used unchanged), no new dependency or framework (`SavedStateHandle` is part of the existing lifecycle/Hilt stack), and no external API. None of the three ADR categories apply.

## 12. Open items (not blocking)

- **The "Lent to X" caption colour deviates from the mock** (G7): `#A4532C` (the `due_upcoming` token) instead of terracotta `#C1693C`, which is 3.39:1 and fails AA for text. This is the same precedent as #19 H4/H6 and #20 R5. Reverting it means changing one token, and FF3 would have to be relaxed knowingly.
- **The dot fills stay as mocked** although finished (2.09:1) and want-to-read (2.75:1) fall below WCAG 1.4.11's 3:1 non-text contrast against the paper ring (G7, Q10). If the product owner wants them to pass, the cheapest fix is darker fills in `colors.xml` (only values change).
- **The mock's sample data contradicts itself** (section 1): "Klara and the Sun" is lent in the row but shown as un-lent in the grid. The implementation follows the AC: every open loan gets the lent dot and caption.
- **For a 0-book library, the chips, title and grid are hidden** (G5). #22 owns the final call ("inert or hidden") and the empty-shelf message. It is one condition in `LibraryScreen`.
- **A filter that matches nothing shows no message** (G5). If the product owner wants copy there (for example "Nothing lent out right now"), that is a small follow-up, best decided alongside #22.
- **Ordering differs by chip** (Q7): the Lent out chip uses `due_at ASC`, the other chips `created_at DESC`. Making Lent out use date-added order would be a one-line `sortedByDescending` in the use case, but it would contradict the lent row's order.
