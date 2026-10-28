# Plan: #35 Delete a book from the Library grid

| Field | Value |
|---|---|
| Ticket | [#35](https://github.com/minicore-software/Bookshelf/issues/35), part of epic [#29](https://github.com/minicore-software/Bookshelf/issues/29) (Book Management). Depends on #34, which is merged: `RemoveBookDialog`, `RemoveBookUseCase` and `LibraryRepository.removeBook`. |
| Branch | `feature/BOOKSHELF-0035-remove-from-library-grid` |
| Stack | Android: Kotlin, Jetpack Compose, Material 3, Hilt, Coroutines/Flow, Room. Single `app` module. No backend. |
| Agents | `kotlin-android-developer` builds it, `kotlin-android-reviewer` reviews it |
| Work split | FRONTEND-ONLY (one Android stack, one build round) |
| UX spec | **Needed (L9).** The scope is narrow: the visual design of the "⋮" affordance and of the card action popover. The flow is decided in this plan; the look is not. |
| API contract | There is no HTTP or event contract. The binding seams are the card callbacks (4.1), the `LibraryViewModel` intents and state (4.2) and the route wiring (4.3). |
| ADR | None. There is no schema change, no new dependency (glyphs are hand-drawn, following the #19/#22 precedent) and no external integration. This is a routine UI addition that reuses #34's dialog and use case. |

## 1. Context

#34 shipped the remove flow from Edit: Detail, then the pencil, then "Remove book", then `RemoveBookDialog`, then `EditBookViewModel.onRemoveConfirmed`, then `RemoveBookUseCase`. #35 adds two faster entry points on the Library grid card (`LibraryGridCard` in `features/library/ui/LibraryGridSection.kt`):

- **Long-press** on a card shows a small popover near the card with a "remove" action (epic D6, D18).
- **Overflow "⋮"** on the card is a non-gesture path for TalkBack and switch-access users (epic D20). It opens a menu with the same "remove" action.

Both must end in the **same** `RemoveBookDialog` with the same copy, open-loan warning and cascade (epic D14, D19; AC3).

What exists today:
- `LibraryGridCard` is a `Column` with `.clickable(onClickLabel = "Open book", role = Button)` and `.semantics(mergeDescendants = true) { contentDescription = "<title>, <status>[, Lent to X]" }`. A `LibraryStatusDot` already sits at the cover's `TopEnd`.
- Cells are narrow. Three fixed columns give about 97dp of content width at a 360dp screen width, and about 84dp at 320dp.
- `material-icons-core` is not a dependency. Glyphs are hand-drawn with `Canvas` (`LibraryAddBookFab.PlusGlyph`, the search glyph in `LibraryHeader`).
- `LibraryViewModel` has no write path yet. It only combines the rows flow and the grid flow, and keeps the filter in `SavedStateHandle`.
- On success, #34 shows no Snackbar and no undo (plan #34 R8 and its open points). The Library flows simply drop the card.

Out of scope: the "Currently reading" and "Lent out" row cards (the AC says "Library card", meaning the grid), undo or soft-delete, a success Snackbar, and multi-select.

## 2. Approach (summary)

1. Each grid card gets **one** anchored M3 `DropdownMenu`, the "card actions menu". Its single item is "Remove book", with a trash glyph. Long-press opens it. Tapping the "⋮" opens the *same* menu. So there is one popover, two triggers and one item.
2. The menu item calls `onRemoveBookRequest(bookId)`, which goes up to `LibraryViewModel.onRemoveRequested(bookId)`. The VM snapshots a `RemoveBookPrompt(bookId, title, borrowerName?)` into `SavedStateHandle`.
3. `LibraryRoute` renders `RemoveBookDialog` from that prompt. There is exactly one call site on the Library side. Confirm calls `LibraryViewModel.onRemoveConfirmed()`, which calls `RemoveBookUseCase` (single-flight). Dismiss calls `onRemoveDismissed()`.
4. Long-press comes from replacing `.clickable` with `.combinedClickable(onClick, onLongClick, onClickLabel, onLongClickLabel, role = Button)` on the same node. Tap still opens Detail, so there is no gesture conflict.

## 3. Decisions

| # | Decision | Reason / alternatives |
|---|---|---|
| L1 | **One popover component for both triggers.** It is an M3 `DropdownMenu` anchored to the card, with one `DropdownMenuItem` "Remove book" that has a leading trash glyph. | AC3 asks for "no divergent behaviour". Using one composable for both triggers makes divergence structurally impossible, not just tested against. The AC wording "popover with a remove icon button" is met by an icon-led item in a popover near the card. The text label also makes it unambiguous and announceable. **Rejected:** a custom `Popup` with an icon-only button for long-press plus a separate `DropdownMenu` for "⋮". That means two components, two sets of a11y semantics and two places to keep in sync, and an icon-only destructive button is weaker for comprehension. If the UX spec insists on icon-only for long-press, it must still be the same composable, with a `compact` presentation flag and an identical `onRemoveBookRequest` call. |
| L2 | **Long-press uses `combinedClickable` on the card's existing interactive node.** It replaces `.clickable`, keeping `onClickLabel = library_book_click_label` and `role = Role.Button`, and adds `onLongClickLabel = library_book_long_click_label` ("Show book actions"). | One gesture detector owns both tap and long-press, so they cannot both fire: a long-press never also opens Detail. Scrolling cancels a pending long-press past touch slop, the same as today's tap. Haptic feedback on long-click is the Foundation default; keep it. The long-click action is also exposed to TalkBack ("double-tap and hold"), which adds a second accessible path for free. **Rejected:** `pointerInput { detectTapGestures(onLongPress) }` on top of `.clickable`. Two detectors on one node lose the semantics actions and risk double-firing. |
| L3 | **The "⋮" is a real `IconButton`-equivalent child of the card**, with a 48dp touch target, its own `contentDescription` ("More options for <title>") and `Role.Button`. It is not part of the merged card node. | AC4: it must be its own TalkBack focus stop, reached by swiping right after the card. A child with its own click action is not merged into the parent's `mergeDescendants` node. The title in its label tells the many "More options" buttons apart. **Placement and visuals are the UX spec's call (L9).** Constraint for the spec: the touch target must not shrink the cover or title width at 320dp, and must not overlap or hide `LibraryStatusDot`. The developer's fallback, if no spec lands first: overlay it on the cover's `BottomEnd` with a 28dp visual disc on a paper-at-85% fill and a 48dp touch target inside the cover bounds. |
| L4 | **The menu's `expanded` state is UI-local** (`remember` per card, not saved). | It is a transient popover. Closing it on rotation or recomposition-recycling is expected Android behaviour, and keeping it out of the VM keeps the VM about data, not chrome. **Rejected:** a screen-level `menuForBookId` in the VM. It is more state for no user benefit. |
| L5 | **The pending removal lives in `LibraryViewModel` as a snapshot `RemoveBookPrompt(bookId, title, borrowerName?)` backed by `SavedStateHandle`** under three keys. | This is #34's hand-off note. It survives rotation, as Edit's `rememberSaveable` dialog flag does (parity), and process death. A snapshot, not a value derived from the live grid, avoids a ghost dialog if a stale id is ever reused by `autoGenerate` after a delete. The data cannot change while the modal dialog is up (single user, local DB). The title and `openLoan?.borrowerName` are already on `LibraryBook`, so there is no extra query. **Rejected:** deriving the prompt from `uiState.grid.books`. It is live, but it has the id-reuse edge case and depends on the active filter still containing the book. |
| L6 | **Single-flight remove, the same rule as #34 R6.** `onRemoveConfirmed()` is a no-op when there is no prompt or a remove is already in flight. It clears the prompt and sets `isRemoving` *synchronously*, before any suspend point, and then launches `RemoveBookUseCase(bookId)`. `onRemoveRequested` is ignored while `isRemoving`. | A double-tap on Confirm in the same frame cannot delete twice or show a second outcome. The dialog closes immediately, as in Edit, where `showRemoveConfirm = false` comes before `onRemoveConfirmed()`. |
| L7 | **Outcomes:** `Removed` does nothing beyond what the Room flows already do (the card, counts and rows update), which matches #34 R8: no Snackbar. `NotFound` is a silent no-op, because the book is already gone. Any other exception (rethrow `CancellationException`) sends a one-shot `LibraryEvent.RemoveFailed` that `LibraryRoute` shows as a Snackbar on the existing `SnackbarHostState`. | Edit shows failure inline in the sheet. The Library has no form, so a Snackbar is the existing feedback surface (#31 A12). The copy is the same. To keep it single-sourced, **rename `edit_book_remove_failed` to `remove_book_failed`** and use it from both `EditBookRoute` and `LibraryRoute`. Only the presentation differs (inline vs. Snackbar), never the dialog, the warning or the cascade. |
| L8 | **No new dependency for glyphs.** The "⋮" (three dots) and the trash glyph are hand-drawn `Canvas` composables in `features/library/ui/`, decorative only (`contentDescription = null`). | This follows the `PlusGlyph`/search glyph precedent. Adding `material-icons-core` for two glyphs would be a dependency decision outside this ticket. |
| L9 | **A UX spec is needed, scoped to visuals only.** It covers: "⋮" placement, size, colour and contrast on arbitrary cover images; the popover's styling against Visual-Spec section 7.3 ("Popover": `--bg`, 14px radius, shadow-3, danger item in terracotta, so remember the AA fix in `docs/ux/decisions.md` 2026-10-27); the menu item copy ("Remove book" is proposed, reusing `edit_book_remove`'s wording); and whether the popover anchors to the card or to the touch point. | The flow and a11y are standard and decided here. But this adds a new visible affordance to every card on the home screen. Neither the `project/` canvas nor the Visual Spec has anything for it, and CLAUDE.md forbids inventing design details that were not provided. The narrow 3-column cell (about 84dp at 320dp) next to the existing status dot makes placement a real design question, not a pattern lookup. |

## 4. Seams (binding for the developer; report any deviation)

### 4.1 Card (`LibraryGridSection.kt`)

```kotlin
fun LibraryGridCard(
    libraryBook: LibraryBook,
    column: Int,
    onBookClick: (Long) -> Unit,
    onRemoveBookRequest: (Long) -> Unit = {},   // new; default keeps previews and tests compiling
    modifier: Modifier = Modifier,
)
```

- Wrap the existing `Column` in a `Box`, so the `DropdownMenu` anchors to the whole card. The `Column` keeps `padding(gridCellPadding(column))`, then `combinedClickable(...)` (L2), then the merged semantics, then `testTag(libraryGridCardTag(id))`, in that order.
- `var menuExpanded by remember(book.id) { mutableStateOf(false) }`. `onLongClick` and the "⋮" `onClick` both set it to `true`.
- The `DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false })` has one `DropdownMenuItem`: the text is `stringResource(R.string.library_card_remove)`, the leading icon is the trash glyph, and `onClick = { menuExpanded = false; onRemoveBookRequest(book.id) }`.
- The "⋮" is a child of the card, outside the merged text, with `contentDescription = stringResource(R.string.library_card_more_options, book.title)`, `Role.Button`, and a minimum 48dp touch target. Its position follows the UX spec (L3/L9).
- New test tags: `libraryGridCardMenuButtonTag(bookId)` = `"library_grid_card_menu_button_$bookId"`, `LIBRARY_CARD_MENU_TEST_TAG` = `"library_card_menu"`, and `LIBRARY_CARD_MENU_REMOVE_TEST_TAG` = `"library_card_menu_remove"`.
- `LibraryScreen` gets `onRemoveBookRequest: (Long) -> Unit = {}` and passes it to every `LibraryGridCard`.

New strings (final copy may be adjusted by the UX spec):

| Key | Value |
|---|---|
| `library_book_long_click_label` | `Show book actions` |
| `library_card_more_options` | `More options for %1$s` |
| `library_card_remove` | `Remove book` |
| `remove_book_failed` | renamed from `edit_book_remove_failed`, value unchanged |

### 4.2 `LibraryViewModel`

```kotlin
data class RemoveBookPrompt(val bookId: Long, val title: String, val borrowerName: String?)
sealed interface LibraryEvent { data object RemoveFailed : LibraryEvent }

// new constructor dependency
private val removeBookUseCase: RemoveBookUseCase

val removePrompt: StateFlow<RemoveBookPrompt?>   // from SavedStateHandle keys
val events: Flow<LibraryEvent>                   // Channel(BUFFERED).receiveAsFlow(), same as EditBookViewModel.onDone

fun onRemoveRequested(bookId: Long)   // looks the book up in the current Content.grid.books; no-op if absent or isRemoving
fun onRemoveDismissed()               // clears the prompt
fun onRemoveConfirmed()               // L6 single-flight; L7 outcomes
```

- `LibraryUiState` is **unchanged**. The prompt is a separate flow, so existing `Content(...)` test seams stay valid.
- `isRemoving` is a private VM field. It does not need to be saved: after process death no remove is in flight.
- The VM must not import anything from `ui.editbook` or `ui.bookform`.

### 4.3 Route (`LibraryRoute.kt`)

- Collect `removePrompt`. When it is non-null, render `RemoveBookDialog(bookTitle = p.title, borrowerName = p.borrowerName, onConfirm = viewModel::onRemoveConfirmed, onDismiss = viewModel::onRemoveDismissed)`. This is the **only** `RemoveBookDialog` call site in the Library feature, and the dialog stays untouched.
- `LaunchedEffect(Unit) { viewModel.events.collect { RemoveFailed -> snackbarHostState.showSnackbar(removeFailedMessage) } }`.
- Pass `onRemoveBookRequest = viewModel::onRemoveRequested` to `LibraryScreen`.
- Navigation is unchanged. No nav-graph edit is needed.

## 5. Quality attributes and fitness functions

| Attribute | How the design serves it | Fitness function |
|---|---|---|
| **Consistency (AC3)** | One dialog composable, one use case, one popover for both triggers. | **FF-L1** (JVM, extend `EditBookSeamTest`): across `src/main`, `RemoveBookDialog(` is called only from `EditBookRoute.kt` and `LibraryRoute.kt`, `RemoveBookUseCase` is injected only into `EditBookViewModel` and `LibraryViewModel`, and `R.string.remove_book_title/body/open_loan_warning` are referenced only from `ui/removebook/`. **FF-L2**: `LibraryRoute`, `LibraryScreen`, `LibraryGridSection` and `LibraryViewModel` do not import `ui.editbook` or `ui.bookform` (extends the existing FF-E4/FF-R3). |
| **Accessibility (AC4)** | "⋮" is its own focus stop with a title-specific label. The card exposes the click and long-click actions with labels. The menu item is text-labelled. | Instrumented semantics test: the "⋮" node `hasClickAction`, has role Button, its content description contains the title, and it is *not* merged into the card node (`onNodeWithTag(menuButtonTag, useUnmergedTree = false)` finds it). The card node has `SemanticsActions.OnLongClick` with label "Show book actions". |
| **Integrity** | Single-flight confirm. The cascade and cover-file cleanup stay inside `RemoveBookUseCase` (ADR-001, ADR-003). | VM unit test: two `onRemoveConfirmed()` calls in the same frame call the use case once. |
| **Layout stability** | The "⋮" must not change the cell's content width or hide the status dot. | Extend `GridCellPaddingTest` or `LibraryGridTest`: at a 320dp width and 2x font scale, the title node's width is unchanged with and without the button, and the status-dot bounds do not intersect the "⋮" bounds. Re-run `LibraryGridPixelTest`. If the "⋮" overlays the cover, `gridCover_isNeverDimmed` must sample a point outside the button. |

## 6. Test strategy (per AC)

| AC | Level | What |
|---|---|---|
| AC1 long-press popover | Instrumented Compose (`LibraryGridTest`) | `performTouchInput { longClick() }` on the card shows `LIBRARY_CARD_MENU_TEST_TAG` with the remove item. `onBookClick` is **not** invoked. A plain `performClick()` still invokes `onBookClick` and shows no menu. Back or an outside tap dismisses the menu with no prompt. |
| AC2 overflow | Instrumented Compose | Clicking the "⋮" opens the same menu tag, and `onBookClick` is not invoked. |
| AC3 same dialog, same behaviour | VM unit (`LibraryViewModelTest`, fake repo + real `RemoveBookUseCase` with a fake `CoverImageStore`) + instrumented E2E | Unit: `onRemoveRequested(id)` produces a prompt with title and borrower (lent book) or `null` (not lent). Dismiss clears the prompt and never calls the repository. Confirm calls `removeBook(id)` once and clears the prompt. `NotFound` emits no event. A throw emits `RemoveFailed`, and a later request still works. Single-flight. E2E (Hilt + real DB, pattern of `EditBookEndToEndTest`): for **each** of the two triggers, a lent book with an image cover shows the dialog with the `REMOVE_BOOK_OPEN_LOAN_WARNING_TEST_TAG` warning; confirming makes the card leave the grid and the "Lent out" row, the loan row is gone, the cover file is deleted, and the counts drop. Cancel leaves everything unchanged. |
| AC4 TalkBack | Instrumented semantics + manual TalkBack pass | Semantics as in FF (section 5). Manual: swipe-navigate a card; expected order is the card ("Title, Status, Lent to X", "double-tap to Open book, double-tap and hold to Show book actions"), then "More options for Title, Button". Activating it opens the menu with focus on "Remove book". |
| Rotation / process death | Instrumented | With the dialog open, `StateRestorationTester` or activity recreation shows the dialog again with the same title and warning. |

## 7. Security and privacy notes

- Everything is local and single-user, with no new data flow or permission. The only "attacker" is an accidental user action. The defences are the second confirm step (D18) and single-flight (L6). Dismiss, back and scrim must truly be no-ops (test).
- The borrower name appears in the dialog, as it does in Edit and on the card caption. Do not log titles or borrower names in the failure path; log the exception type only, if at all.
- The delete is irreversible by design (D19). The new entry points make deleting faster, so the confirm step must never be skipped by any trigger. No "don't ask again" option.

## 8. Task list (single `kotlin-android-developer` round, in build order)

0. Wait for or read the UX spec (L9) for the "⋮" placement and popover styling. If it is not available, build to the fallback in L3 and flag it in the hand-off.
1. **Strings and glyphs.** Add the strings in 4.1. Rename `edit_book_remove_failed` to `remove_book_failed` and update `EditBookRoute` (AC3). Add hand-drawn `MoreVertGlyph` and `TrashGlyph` (decorative) (AC1, AC2).
2. **Card.** Switch `LibraryGridCard` to `combinedClickable` (L2). Add the "⋮" child (L3) and the shared `DropdownMenu` (L1, L4). Add `onRemoveBookRequest`. Add test tags. Update previews: one with the menu expanded, plus 2x font at 320dp (AC1, AC2, AC4).
3. **Screen.** Thread `onRemoveBookRequest` through `LibraryScreen` (AC1, AC2).
4. **ViewModel.** Inject `RemoveBookUseCase`. Add `RemoveBookPrompt`, `LibraryEvent`, the `SavedStateHandle`-backed `removePrompt`, `onRemoveRequested`, `onRemoveDismissed` and `onRemoveConfirmed` (L5, L6, L7). Unit tests per section 6 (AC3).
5. **Route.** Add the single `RemoveBookDialog` call site and the failure Snackbar collector (AC3).
6. **Fitness functions.** FF-L1 and FF-L2 in `EditBookSeamTest` or a new `LibraryRemoveSeamTest` (AC3).
7. **Instrumented tests.** Grid gesture and menu tests, semantics tests, two-trigger E2E, and the rotation test. Re-run the pixel tests (AC1 to AC4).
8. **Docs.** Add a CHANGELOG entry. If the CLAUDE.md architecture tree mentions the Library grid's interactions, add a one-line note.

## 9. Risks (for QA to probe)

| # | Risk | Probe |
|---|---|---|
| K1 | A long-press also fires a tap, so Detail opens behind the popover. | Long-press, then release: there is no navigation. Long-press, then drag off the card: no menu and no navigation. |
| K2 | A long-press during a fling or scroll opens the menu by mistake. | Scroll the grid fast, starting the touch on a card. No menu should open. |
| K3 | The "⋮" steals card taps or shrinks the hit area, so tapping near the cover corner opens the menu instead of Detail. | Tap the cover centre, the title and the caption: Detail opens. Tap the "⋮": the menu opens. Test at 320dp and 2x font. |
| K4 | TalkBack merges the "⋮" into the card, or announces it as an unlabelled "Button". | A manual TalkBack pass (AC4) plus the semantics test. Also check Switch Access and keyboard (Tab/Enter) reach the "⋮". |
| K5 | Double confirm, or a request while a remove is in flight, deletes twice or shows a stale dialog. | Rapid double-tap on Remove. Long-press another card right after confirming. |
| K6 | Removing the last book, or the last book in the active filter, leaves a broken screen. | Remove the only book: the empty state (E1) appears and the FAB is still visible. With the "Lent out" filter, remove the only lent book: the chip count reads 0 and the grid is empty but the chips stay (G5). |
| K7 | A dialog restored after rotation or process death shows the wrong book, or a ghost dialog appears for a newly added book. | Rotate with the dialog open. Then dismiss, add a book and confirm no dialog appears. |
| K8 | The "⋮" has poor contrast on dark or light image covers and on the preset palettes. | The UX spec defines it. Verify against all presets plus a white and a black image cover (WCAG 1.4.11, 3:1 for non-text). |
| K9 | The menu opens off-screen for cards in the last row or near the FAB. | Long-press a card in the bottom row, partly under the FAB: the menu must flip above the card and stay fully visible. |
| K10 | An item recycled by the lazy grid keeps `menuExpanded` for a different book. | `remember(book.id)` keys the state. Open the menu, scroll it away and back, and check that no stray menu appears. |

## 10. Open points (non-blocking)

- The UX spec (L9) may change the "⋮" placement, the popover anchoring (card vs. touch point) and the item copy. The flow, state ownership and seams in section 4 stay binding.
- A TalkBack custom action "Remove book" directly on the card node was considered and not added. It would be a fourth entry point beyond the AC. Revisit if accessibility testing asks for fewer steps.
- The "Currently reading" and "Lent out" row cards get no delete entry point in this ticket.
