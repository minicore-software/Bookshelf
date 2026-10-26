# Plan: #22 Empty-state Library screen

| Field | Value |
|---|---|
| Ticket | [#22](https://github.com/minicore-software/Bookshelf/issues/22), part of epic [#17](https://github.com/minicore-software/Bookshelf/issues/17) (Library Screen). Depends on [#20](https://github.com/minicore-software/Bookshelf/issues/20) and [#21](https://github.com/minicore-software/Bookshelf/issues/21) (both merged). |
| Branch | `feature/BOOKSHELF-0022-empty-state-library-screen` |
| Stack | Android: Kotlin, Jetpack Compose, Material 3, Hilt 2.60.1, Coroutines/Flow, Room (read through #18's repository, unchanged). Single `app` module. No backend. |
| Agents | `frontend-developer-android` builds it, `frontend-reviewer-android` reviews it |
| Work split | FRONTEND-ONLY |
| UX spec | Not needed. See E10. |
| API contract | No HTTP or event contract. The binding seams are the `LibraryScreen` / `LibraryRoute` signatures and the `isLibraryEmpty` predicate in section 4. |
| ADR | None. See section 11. |
| Status | Ready to build |

---

## 1. Context

- **What the screen does today** (#20 and #21): `LibraryScreen(uiState, modifier, onSearchClick, onBookClick, onSeeAllLoansClick, onFilterSelected)` is a `Column` (background paper, `safeDrawingPadding()`) with the fixed `LibraryHeader`, then one `LazyVerticalGrid(GridCells.Fixed(3))` tagged `LIBRARY_BODY_TEST_TAG`. Its items are: `reading_section` (only if `rows.currentlyReading` is non-empty, #20 R11), `lent_section` (only if `rows.lentOut` is non-empty), and, only when `grid.counts.all > 0`, `filter_chips`, `grid_title`, one cell per book and `grid_bottom` (#21 G5). `Loading` renders the header only.
- **So, with 0 books, the screen already hides the rows, the chips, the title and the grid** and shows nothing under the header. That is the "broken-looking" state this ticket replaces. AC1 and AC3 are therefore already true in behaviour; this ticket adds the message (AC2), the FAB (AC4), and the end-to-end test that pins all of it (AC5).
- **State**: `LibraryUiState.Loading | Content(rows: LibraryRows, grid: LibraryGrid = LibraryGrid.Empty)`. The ViewModel `combine`s the rows flow and the grid flow, `stateIn(WhileSubscribed(5_000), Loading)`, so the first `Content` arrives only after **both** Room queries emitted once. `LibraryCounts.all` is the sum of the three statuses, i.e. the total number of books (#21 section 4).
- **The FAB does not exist yet.** The ticket says "FAB remains visible", but #20 and #21 did not build it (#21 plan section 3: "FAB and bottom navigation: not built in this ticket"), and no other ticket in epic #17 owns it. Epic decisions D4/D15 say the FAB is built as mocked, and D9 makes it a stub. **This ticket builds it** (minimum needed to satisfy AC4). Bottom navigation stays out of scope.
- **Design sources**:
  - `project/Main.dc.html` FAB: `position:absolute; right:20px; bottom:104px` (that is 20px above the 84px bottom nav, which does not exist in the app), `52×52`, `border-radius:50%`, forest `#2F4538` fill, shadow `0 6px 14px rgba(35,31,32,.25)`, a 22px plus glyph stroked paper `#F3EEE3`, width 2, round caps, `aria-label="Add a book"`.
  - The mock has **no empty state**. `docs/specs/Bookshelf-Visual-Spec.md` section 5.15 has one ("No books: `❦` · 'An empty shelf' · primary **+ Add your first book**", surface fill, 22px radius, dashed border, padding 80px 20px, heading Slab 22/600 forest, 14px muted copy). CLAUDE.md flags that spec as *pending reconciliation*: its tokens (`#F5F1E8`, `#4F6F52`, ...) are not the ones the app ships. The ticket says to use the **existing** palette/type tokens. So this plan takes the spec's **structure and copy** (heading "An empty shelf", centred block, 22dp radius, 80dp vertical padding) and renders it with the **in-code** tokens (E4). The glyph and the CTA button are not built (E5).
- **Epic decisions in force**: D9 (read-only, FAB is a stub), D15 (FAB as mocked), D18 (taps are no-ops). **Precedents**: #19 H6 (glyphs hand-drawn with `Canvas`, `material-icons-core` is not a dependency), #19 H7 / #20 R9 (hoisted, defaulted no-op callbacks, stubbed in `BookshelfNavGraph`), #20 R11 / #21 G5 (empty sections are hidden), #20 R13 (Hilt instrumented tests with `@UninstallModules` + `@BindValue`).

## 2. Key decisions

| # | Decision | Why / alternatives |
|---|---|---|
| E1 | **"Empty library" is one pure predicate on `Content`:** `internal val LibraryUiState.Content.isLibraryEmpty: Boolean get() = grid.counts.all == 0 && rows.currentlyReading.isEmpty() && rows.lentOut.isEmpty()`. `Loading` is never empty (it is not `Content`). | `counts.all` is the authoritative book total (one SQL `COUNT`). Including the rows makes the predicate mean "there is nothing at all to show", which (a) is what the ticket's "0 books, 0 reading, 0 lent" says literally, (b) keeps the ~20 #20 tests that build `Content(rows)` with the default `LibraryGrid.Empty` and populated rows **unchanged** (they must not suddenly show an empty-shelf message), and (c) tolerates the one-frame skew between the two Room flows: if the rows flow emits a first book before the counts flow does, the screen shows the row without the message rather than both. In production the FK from `loans` to `books` and `status == reading` ⊂ books make `counts.all == 0` imply empty rows, so the extra clauses never change real behaviour. **Rejected:** `grid.counts.all == 0` alone (breaks #20's test seam and can show the message next to a row for a frame). **Rejected:** a `LibraryUiState.Empty` variant computed in the ViewModel (a third state every `when` must handle, and it splits "what the rows show" from "what the grid shows" across two places for no gain). **Rejected:** `grid.books.isEmpty()` (true for any filter with no match, e.g. "Lent out · 0" in a non-empty library, which #21 G5 deliberately shows without a message). |
| E2 | **Chips stay hidden at 0 books** (the AC allows "inert (0 counts) or hidden"). Rows stay hidden (#20 R11). The "Your library" title stays hidden. No code change for these: #21's `grid.counts.all > 0` condition already does it. | Five chips reading "All · 0", "Reading · 0", ... are noise on a first run, and a tappable filter over nothing invites a tap that changes nothing. Hidden is what #21 G5 already ships and tests (`emptyLibrary_hidesChipsTitleAndGrid`), so this is the zero-churn choice. The empty-state panel has its own heading, so "Your library" would duplicate it. **Rejected:** inert chips (needs a disabled-chip visual that the mock does not define, plus `disabled()` semantics, for no user value). |
| E3 | **The message is a full-span lazy item inside the existing `LazyVerticalGrid`**, key `"empty_state"`, rendered when `content.isLibraryEmpty`, in the slot where `filter_chips` would be. The body keeps its `LIBRARY_BODY_TEST_TAG` and its structure. | It scrolls with the body for free (needed at 2× font scale on short screens), and no new scroll container or branch around the grid is added. Vertical centring in the remaining height is not needed: the spec's own treatment is a block with 80dp vertical padding, which reads as intentional when top-aligned under the header. **Rejected:** replacing the `LazyVerticalGrid` with a centred `Box` when empty (centring plus scrolling needs `BoxWithConstraints` + `heightIn(min = maxHeight)` + `verticalScroll`, and it swaps the body node, so tests that target `LIBRARY_BODY_TEST_TAG` behave differently by state). |
| E4 | **Empty-state visuals, existing tokens only.** New file `features/library/ui/LibraryEmptyState.kt`: `Column(Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colorScheme.surfaceContainerLowest).border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(22.dp)).padding(horizontal = 20.dp, vertical = 80.dp).testTag(LIBRARY_EMPTY_STATE_TEST_TAG), horizontalAlignment = CenterHorizontally, verticalArrangement = spacedBy(8.dp))` { heading `Text(stringResource(library_empty_title), style = typography.titleLarge, color = colorScheme.primary, textAlign = Center, modifier = Modifier.semantics { heading() })`; body `Text(stringResource(library_empty_body), style = typography.bodyMedium, color = colorScheme.onSurfaceVariant, textAlign = Center, modifier = Modifier.widthIn(max = 420.dp))` }. | Paper-bright fill + `line` border + rounded panel is the same treatment as the header's search bar, so it is clearly a designed element, not a hole. `titleLarge` is already Fraunces SemiBold 22sp, which is exactly the spec's "empty-state heading 22/600 forest", and `primary` is the in-app forest. `bodyMedium` is Inter 14sp, the spec's "14px muted copy", and `onSurfaceVariant` is `ink_muted`. Contrast: forest on paper-bright ≈ 9.8:1, ink-muted on paper-bright ≈ 5.9:1 (both AA, FF2). No hex literal, no new `TextStyle`, no new colour. **Rejected:** the spec's dashed border and `--surface #E8E1D4` (new tokens from an unreconciled spec; a dashed border also needs custom drawing). |
| E5 | **Copy:** heading "An empty shelf", body "Books you add will appear here." **No glyph, no CTA button.** | The heading is the spec's own copy and matches the ticket's "empty-shelf message". The body is true today and after Add Book ships, and it does not tell the user to press a button that currently does nothing. The ticket rules out a new illustration, and the `❦` glyph is not guaranteed to be in the bundled Fraunces/Inter subsets (it would fall back to a system font). An "+ Add your first book" button would be a second stub CTA next to the FAB; the epic names the FAB as the implied next step. Copy is a one-string change if the product owner wants other words (section 12). |
| E6 | **The FAB** (new file `features/library/ui/LibraryAddBookFab.kt`): M3 `FloatingActionButton(onClick, modifier.size(52.dp).testTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).semantics { contentDescription = stringResource(library_add_book) }, shape = CircleShape, containerColor = colorScheme.primary, contentColor = colorScheme.onPrimary, elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp))` { private `PlusGlyph(tint = LocalContentColor.current)`: a 22dp `Canvas` with two lines, stroke 2dp, `StrokeCap.Round`, spanning 5..19 of a 24-unit box, like the mock's SVG }. The glyph is decorative (no semantics of its own). | M3 FAB gives the button role, ripple, elevation and a correct focus indication. `size(52.dp)` is the mock's size and still ≥ 48dp. M3's default FAB shape is a rounded square, so `CircleShape` is set explicitly to match the mock. The `Canvas` glyph follows #19 H6 (no `material-icons-core` dependency). Paper on forest is 8.94:1 (already asserted in `LibraryContrastTest`). TalkBack: "Add a book, button". **Rejected:** a `Box().clickable()` circle (re-implements elevation, ripple and role). **Rejected:** adding `material-icons-core` for one plus sign. |
| E7 | **Placement:** `LibraryScreen`'s root becomes a `Box(modifier.fillMaxSize().background(...).safeDrawingPadding())` holding the existing `Column` (header + body, `fillMaxSize()`) and the FAB at `Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp)`. The FAB is **always** rendered: in `Loading`, in the empty state and in the populated state. The `LazyVerticalGrid` gets `contentPadding = PaddingValues(bottom = FabClearance)` with `private val FabClearance = 88.dp` (20dp margin + 52dp FAB + 16dp gap). | The mock's `bottom:104px` is "20px above the bottom nav"; with no bottom nav, 20dp above the safe-drawing edge is the same visual rule, and `safeDrawingPadding()` on the `Box` keeps the FAB clear of the gesture/3-button nav bar and landscape cutouts. "Always" satisfies "remains visible" literally and avoids a pop-in at the Loading→Content handover. A **bottom-only** content padding leaves the horizontal edge-to-edge rows and chips untouched (#21 G10 rejected only *horizontal* padding) and lets the last grid row and the empty-state body scroll out from under the FAB. **Rejected:** M3 `Scaffold(floatingActionButton = ...)` at screen level. It adds its own inset handling on top of the existing `safeDrawingPadding()`, and where the scaffold lives (screen vs app shell) is the bottom-navigation ticket's decision, not this one's. A `Box` overlay is a two-line change that the later shell can lift out. |
| E8 | **No-op wiring**, same pattern as every other stub: `LibraryScreen` gains `onAddBookClick: () -> Unit = {}`. `LibraryRoute` gains a required `onAddBookClick: () -> Unit` and passes it through. `BookshelfNavGraph` passes `onAddBookClick = {}` and extends its stub comment ("stub until search/Add Book/Book Detail/My Loans ship (epic #17 D9, D18, D19)"). | The callback is hoisted to the nav graph, which is the single place the Add Book ticket will edit (`navController.navigate(AddBook.route)`). "Doesn't crash or navigate" is then testable at both levels: the component test sees exactly one callback invocation, the nav test sees the route and back stack unchanged. **Rejected:** a `{}` inside `LibraryScreen` with no parameter (the Add Book ticket would have to change the screen's signature and every test). |
| E9 | **Previews:** update `"Library — empty"` (now shows the panel and FAB), add `"Library — empty, 2x font"` (`fontScale = 2f`) and `"Library — empty, 320dp"` (`widthDp = 320`). The populated preview now also shows the FAB. | Reviewers see the state without running the app, at the extremes that matter for wrapping. |
| E10 | **No UX spec needed** (confirms the ticket). | The ticket and epic settle the open question ("its own micro design pass or reuse existing tokens plainly") in favour of existing tokens. The FAB is fully specified by `Main.dc.html`. The only open choices are copy (E5, taken from the visual spec), the panel treatment (E4, reusing the search bar's tokens) and chips hidden vs inert (E2, the AC allows both). There is no new flow: the screen gains one static message and one button that does nothing yet. Each choice is a string or a condition to reverse. |

## 3. Out of scope

- Add Book screen and real FAB navigation (later epic; D9).
- Bottom navigation, and an app-level `Scaffold` shell.
- An empty-state glyph, illustration or CTA button (E5), and a message for "filter matches nothing" in a non-empty library (#21 G5 / section 12 keeps that as no message).
- Any change to `LibraryRepository`, DAOs, entities, schema, use cases or the ViewModel.
- Reconciling `Bookshelf-Visual-Spec.md` with the in-code tokens (CLAUDE.md "pending reconciliation").

## 4. Changed signatures and new types (binding)

`features/library/ui/`

```kotlin
// LibraryUiState.kt (addition; the sealed interface itself is unchanged)
/** E1: nothing at all to show - 0 books in total and both rows empty. `Loading` is never empty. */
internal val LibraryUiState.Content.isLibraryEmpty: Boolean

// LibraryScreen.kt
@Composable fun LibraryScreen(
    uiState: LibraryUiState,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onBookClick: (bookId: Long) -> Unit = {},
    onSeeAllLoansClick: () -> Unit = {},
    onFilterSelected: (LibraryFilter) -> Unit = {},
    onAddBookClick: () -> Unit = {},          // new, E8
)

// LibraryRoute.kt
@Composable fun LibraryRoute(
    onSearchClick: () -> Unit,
    onBookClick: (bookId: Long) -> Unit,
    onSeeAllLoansClick: () -> Unit,
    onAddBookClick: () -> Unit,               // new, E8
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
)

// LibraryEmptyState.kt (new)
const val LIBRARY_EMPTY_STATE_TEST_TAG = "library_empty_state"
@Composable internal fun LibraryEmptyState(modifier: Modifier = Modifier)

// LibraryAddBookFab.kt (new)
const val LIBRARY_ADD_BOOK_FAB_TEST_TAG = "library_add_book_fab"
@Composable internal fun LibraryAddBookFab(onClick: () -> Unit, modifier: Modifier = Modifier)
```

Render rules in `LibraryScreen` (in this order inside the lazy grid):

| State | Rows | Chips + title + cells | `empty_state` | FAB |
|---|---|---|---|---|
| `Loading` | hidden | hidden | **hidden** | shown |
| `Content`, `isLibraryEmpty` | hidden (empty lists) | hidden (`counts.all == 0`) | **shown** | shown |
| `Content`, `counts.all == 0`, a row non-empty (test seam / one-frame skew) | per row | hidden | hidden | shown |
| `Content`, `counts.all > 0` | per row | shown (#21 G5) | hidden | shown |

Compatibility: additive. `LibraryScreen` gains a defaulted parameter (source-compatible). `LibraryRoute` gains a required parameter; its only production caller is `BookshelfNavGraph` (updated here). `!` does not apply.

## 5. File changes

| File | Change |
|---|---|
| `features/library/ui/LibraryUiState.kt` | Add `isLibraryEmpty` (E1) with KDoc explaining why the rows are part of it. |
| `features/library/ui/LibraryScreen.kt` | Root `Box` + FAB overlay (E7). `onAddBookClick` param (E8). `contentPadding(bottom = FabClearance)`. `empty_state` full-span item (E3). Update KDoc (it currently says an empty library shows nothing). Previews (E9). |
| `features/library/ui/LibraryEmptyState.kt` | **New** (E4, E5). |
| `features/library/ui/LibraryAddBookFab.kt` | **New** (E6), with a private `PlusGlyph` and its own preview. |
| `features/library/ui/LibraryRoute.kt` | Add and pass `onAddBookClick` (E8). |
| `navigation/BookshelfNavGraph.kt` | Pass `onAddBookClick = {}`; extend the stub comment (E8). |
| `res/values/strings.xml` | Under `<!-- Library empty state and FAB (BOOKSHELF-0022) -->`: `library_empty_title` "An empty shelf", `library_empty_body` "Books you add will appear here.", `library_add_book` "Add a book". |
| Tests | Section 8. |
| `CHANGELOG.md` | One line under `[Unreleased]`, at integration time. |

Not touched: domain, data, DI (main), Room, `LibraryHeader.kt`, `LibraryRows.kt`, `LibraryGridSection.kt`, `core/designsystem` (no new token or style), Gradle files (no new dependency), `CLAUDE.md`.

## 6. Quality attributes and fitness functions

| Attribute | How the design serves it | Fitness function (developer implements) |
|---|---|---|
| **Correctness of the first-run state** | One predicate decides (E1); the existing conditions keep hiding rows and chips (E2). | **FF1** (JVM, new `LibraryUiStateTest`): `isLibraryEmpty` truth table: zero counts + empty rows → true; `counts.all = 1` → false; zero counts + one reading row → false; zero counts + one lent row → false. **FF3** (instrumented, the AC5 end-to-end test, section 8). |
| **Accessibility** | Heading semantics on the panel title; AA text tokens; FAB has a label, a button role and a 52dp target; FAB never permanently covers content (bottom content padding); everything scales in `sp` and scrolls. | **FF2** (extend JVM `LibraryContrastTest`): `forest` on `paper_bright` and `ink_muted` on `paper_bright` ≥ 4.5:1. Instrumented: FAB `assertHeightIsAtLeast(48.dp)` / width; at `FontScale(2f)` + `ForcedSize(320.dp, 640.dp)` the heading and body are displayed after `performScrollTo()`. |
| **No regression in the populated screen** | The FAB is an overlay with matching content padding (E7). | **FF4** (component): with ~12 books, `performScrollToNode(hasTestTag(LIBRARY_GRID_BOTTOM_TEST_TAG))`, then the last grid card's `boundsInRoot.bottom` ≤ the FAB's `boundsInRoot.top`. |
| **Evolvability** | Callback hoisted to the nav graph (E8); FAB is a self-contained overlay the future app shell can lift out (E7). | Existing `LayeringTest` (the new files live in `features`, nothing in `core` changes). |

## 7. Security

No new entry point, permission, network access, IPC, persisted data or logging. The empty state renders static strings; the FAB invokes a no-op. The #20/#21 rule still applies to the touched files: never log `LibraryUiState` (its `toString()` can include borrower names). Nothing for QA to probe on the security side.

## 8. Test strategy

Conventions as in #20/#21: JUnit 4, hand-written fakes, component tests against the **stateless** `LibraryScreen` in `BookshelfTheme`, Hilt instrumented tests with `@UninstallModules` (#20 R13).

| AC | Level | What |
|---|---|---|
| **AC1** rows hidden at 0 books | JVM | FF1. |
| AC1 | Component (new `LibraryEmptyStateTest`) | `Content(LibraryRows(emptyList(), emptyList()), LibraryGrid.Empty)`: no node with `LIBRARY_READING_ROW_TEST_TAG` / `LIBRARY_LENT_ROW_TEST_TAG`, no "Currently reading" / "Lent out" titles. |
| **AC2** empty-shelf message instead of an empty grid | Component | Same state: `LIBRARY_EMPTY_STATE_TEST_TAG` displayed; "An empty shelf" displayed with `heading()`; body displayed; no `libraryGridCardTag(*)` node, no "Your library". **Negative cases:** `Loading` → no empty state; `counts.all > 0` (any filter, including "Lent out · 0") → no empty state; `counts.all == 0` with a non-empty row → no empty state. FontScale 2× / 320dp case (FF2 instrumented part). |
| **AC3** chips hidden | Component | No `LIBRARY_FILTER_CHIPS_TEST_TAG`, no `libraryFilterChipTag(*)` for any `LibraryFilter.entries`. |
| **AC4** FAB visible, tap is a no-op | Component | FAB displayed in `Loading`, empty and populated states, with content description "Add a book" and role Button; size ≥ 48dp. `LibraryScreen(onAddBookClick = { clicks++ })`: one `performClick()` → `clicks == 1`; the empty state is still displayed afterwards. FF4. |
| AC4 | Instrumented, Hilt | Covered by the AC5 test below (route and back stack unchanged after tapping the FAB). |
| **AC5** Compose UI test covers the 0-book state end-to-end | Instrumented, Hilt (new `LibraryEmptyStateEndToEndTest`, `HiltTestActivity` hosting `BookshelfNavGraph` with a `TestNavHostController`) | **Real data path, not a fake repository:** `@UninstallModules(DatabaseModule::class)` and provide a `Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java).applyBookshelfConfig().build()` plus its two DAOs from the test (a nested `@Module @InstallIn(SingletonComponent::class)` in the test class, or `@BindValue` fields). `LibraryDataModule` and `RoomLibraryRepository` stay real, so the flow is UI → ViewModel → use cases → repository → DAO on an empty database, i.e. a fresh install. Steps: wait for the empty state; assert AC1–AC3 as above and FAB displayed; record back-stack size; tap the FAB; assert route `Home`, back-stack size unchanged, empty state still displayed (no crash). **Leave-empty transition:** insert one `Reading` book via `BookDao.insert` → wait until the empty state disappears, the "All · 1" chip and the reading row appear. Close the database in `@After`. |
| Updated tests | Component | `LibraryScreenTest.libraryScreen_showsExactlyTheExpectedText`: its state is now the empty library, so the exact text count becomes **5** (title, tagline, placeholder, "An empty shelf", body); assert the two new texts by value. `LibraryGridTest.emptyLibrary_hidesChipsTitleAndGrid`: same change (3 → 5) and its comment; it now also asserts the empty state is displayed. Update the `LibraryScreen` KDoc/comment references to "3 texts". |
| Updated tests | Instrumented, Hilt | `BookshelfNavGraphTest` and `LibraryGridFilteringTest`: no expected change (callback is defaulted in the screen; `BookshelfNavGraph` supplies it to the route). Run them to confirm FAB overlap does not steal any card click (Q2). |
| Regression | All | `./gradlew lint test connectedAndroidTest assembleDebug`. `LibraryScreenPixelTest` corner pixels stay paper (the FAB is ≥ 20dp from every corner). `LibraryRowsTest` (default `LibraryGrid.Empty` with populated rows) shows no empty state (E1). |
| Manual | Device | Fresh install (or clear storage): empty panel under the header, FAB bottom-right, tap FAB → nothing happens. Max font size + display size on API 29 and 35: panel scrolls, FAB never permanently hides text. 3-button nav and gesture nav, portrait and landscape: FAB clears the nav bar. TalkBack: header → "An empty shelf, heading" → body → "Add a book, button". Add a book through the Database Inspector: the panel is replaced live by the row/chips/grid, without restarting. |

## 9. Work split and tasks

**FRONTEND-ONLY.** Single Android `app` module, no backend.

`frontend-developer-android`, in build order:

1. **Predicate (TDD):** `isLibraryEmpty` + FF1. (AC1, AC2)
2. **Strings** and **`LibraryEmptyState.kt`** (E4, E5) with a preview; FF2 in `LibraryContrastTest`. (AC2)
3. **`LibraryAddBookFab.kt`** (E6) with a preview. (AC4)
4. **`LibraryScreen`**: `Box` root, FAB overlay, `contentPadding`, `empty_state` item, `onAddBookClick`, KDoc, previews (E3, E7, E8, E9). (AC1–AC4)
5. **Wiring:** `LibraryRoute` and `BookshelfNavGraph` (E8). (AC4)
6. **Tests:** update `LibraryScreenTest` and `LibraryGridTest` text counts; new `LibraryEmptyStateTest` (component, incl. FF4); new `LibraryEmptyStateEndToEndTest` (Hilt, in-memory Room). (AC1–AC5)
7. Run `./gradlew lint test connectedAndroidTest assembleDebug`. Attach screenshots of the empty preview (1× and 2× font) to the PR.

## 10. Risks for QA to probe

| # | Risk | Probe |
|---|---|---|
| Q1 | **Empty state flashes on cold start for a populated library** (shown before Room's first emission). | `Loading` must not render it (component negative case). Manual: cold start with 20+ books, watch the handover from splash. |
| Q2 | **The FAB steals taps** meant for a grid card underneath it, in tests (`performClick` lands on the FAB) or for users. | FF4. Run the full `connectedAndroidTest` suite; any test that clicks the last cards should still record the card id. Manual: scroll to the end, tap the bottom-right card. |
| Q3 | **Stuck in the empty state** after the first book appears (or the panel lingers next to the new content). | AC5 leave-empty transition step. Manual via the Database Inspector. |
| Q4 | **The empty state appears for a filter with no results** in a non-empty library (e.g. "Lent out · 0"). | Component negative case (`counts.all > 0` → no panel). |
| Q5 | **Large font / short or narrow screen**: heading or body clipped, FAB covering the body with no way to scroll it free. | FF2 instrumented check; manual at max font and display size, landscape. |
| Q6 | **FAB under the system nav bar** (3-button nav, landscape, cutouts). | Manual, both nav modes, both orientations. |
| Q7 | **Stub control announced as actionable**: TalkBack says "Add a book, button" and double-tap does nothing. | Accepted for this epic, the same as search, card taps and "See all" (D9). Not a defect. |
| Q8 | **Hilt test module swap**: uninstalling `DatabaseModule` without providing `BookDao`/`LoanDao` fails at compile time; providing a file-backed DB leaks state between tests. | Review the test module: in-memory DB, closed in `@After`. |

## 11. ADR

**No ADR is needed.** This is a routine UI addition inside an existing feature: one composable message, one stub button, one predicate and one hoisted callback, all following #19–#21's patterns. No framework or structural change (the `Scaffold`/app-shell question is explicitly left to the bottom-navigation ticket, E7), no external API, and no database change.

## 12. Open items (not blocking)

- **The FAB is built in this ticket** although the AC says it "remains" visible: it did not exist after #20/#21 and no other ticket in the epic owns it (section 1). It follows the mock exactly (D15), except its bottom offset, which is 20dp above the safe-drawing edge instead of above a bottom nav that does not exist yet. The bottom-navigation ticket must raise it (or move it into a `Scaffold`) when the nav bar lands.
- **Copy** (E5): "An empty shelf" / "Books you add will appear here." is a proposal; the product owner can change either string. If an "+ Add your first book" CTA is wanted, it belongs with the Add Book ticket, not as another stub here.
- **Chips hidden, not inert** (E2), a choice the AC allows. Switching to inert chips later means changing one condition plus defining a disabled-chip look.
- **Visual-spec reconciliation**: when `Bookshelf-Visual-Spec.md` is reconciled with the in-code palette, the panel may pick up the spec's dashed border and `surface` fill; it only touches `LibraryEmptyState.kt`.
