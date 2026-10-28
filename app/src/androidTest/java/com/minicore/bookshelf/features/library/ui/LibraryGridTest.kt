package com.minicore.bookshelf.features.library.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.designsystem.component.BOOK_COVER_TEST_TAG_PREFIX
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.LibraryFilter
import com.minicore.bookshelf.features.library.domain.LibraryGrid
import com.minicore.bookshelf.features.library.domain.LibraryRows
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** AC1-AC4, G5, G12: the stateless [LibraryScreen]'s chip row and grid. */
class LibraryGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun book(id: Long, title: String = "Book $id", status: BookStatus = BookStatus.Reading) = Book(
        id = id,
        title = title,
        author = "Author",
        isbn = null,
        cover = Cover.Preset(CoverPreset.forBookId(id)),
        genre = null,
        pageCount = null,
        publishedYear = null,
        status = status,
        rating = null,
        synopsis = null,
        createdAt = Instant.EPOCH,
    )

    private fun setContent(
        grid: LibraryGrid,
        onBookClick: (Long) -> Unit = {},
        onFilterSelected: (LibraryFilter) -> Unit = {},
        onRemoveBookRequest: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(
                        uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList()), grid),
                        onBookClick = onBookClick,
                        onFilterSelected = onFilterSelected,
                        onRemoveBookRequest = onRemoveBookRequest,
                    )
                }
            }
        }
    }

    // AC1 (component): all five chips read with the live counts - the screen order is
    // [All, Reading, Finished, WantToRead, LentOut] because `LibraryFilterChips` renders
    // `LibraryFilter.entries` verbatim (proven directly, as a pure enum fact, by
    // `LibraryFilterTest.entries_areInScreenOrder` - a JVM test, not a pixel-bounds measurement,
    // which the scrollable row's clipping makes unreliable for an off-screen chip). Only the
    // selected chip reports selected.
    @Test
    fun chips_renderWithLiveCounts_onlySelectedChipIsSelected() {
        val counts = LibraryCounts(all = 27, reading = 2, finished = 19, wantToRead = 6, lentOut = 3)
        setContent(LibraryGrid(LibraryFilter.All, counts, emptyList()))
        composeRule.waitForIdle()

        // The chip row scrolls horizontally and may not fit every chip on screen at once (plan
        // G10/G11) - assertExists proves the correct text exists, without requiring every chip
        // to be simultaneously visible without scrolling.
        composeRule.onNodeWithText("All · 27").assertExists()
        composeRule.onNodeWithText("Reading · 2").assertExists()
        composeRule.onNodeWithText("Finished · 19").assertExists()
        composeRule.onNodeWithText("Want to read · 6").assertExists()
        composeRule.onNodeWithText("Lent out · 3").assertExists()

        composeRule.onNodeWithTag(libraryFilterChipTag(LibraryFilter.All)).assertIsSelected()
        composeRule.onNodeWithTag(libraryFilterChipTag(LibraryFilter.Reading)).assertIsNotSelected()
        composeRule.onNodeWithTag(libraryFilterChipTag(LibraryFilter.Finished)).assertIsNotSelected()
        composeRule.onNodeWithTag(libraryFilterChipTag(LibraryFilter.WantToRead)).assertIsNotSelected()
        composeRule.onNodeWithTag(libraryFilterChipTag(LibraryFilter.LentOut)).assertIsNotSelected()
    }

    // FF3: every chip meets the 48dp touch-target minimum.
    @Test
    fun everyChip_meetsTheFortyEightDpTouchTarget() {
        val counts = LibraryCounts(all = 27, reading = 2, finished = 19, wantToRead = 6, lentOut = 3)
        setContent(LibraryGrid(LibraryFilter.All, counts, emptyList()))

        for (filter in LibraryFilter.entries) {
            composeRule.onNodeWithTag(libraryFilterChipTag(filter)).assertHeightIsAtLeast(48.dp)
        }
    }

    // FF3: at 2x font scale on a 320dp-wide viewport, every chip label stays on one line and the
    // chip row remains scrollable (mirrors #19/#20's LibraryHeaderTest/LibraryRowsTest pattern).
    // A chip's box is floored at the 48dp touch target (G11), so >= 48dp alone does not tell a
    // single-line chip apart from one that has wrapped to two lines - a wrapped chip at this
    // font scale and padding is comfortably above 60dp, so capping the height here as well pins
    // "single line", not merely "not clipped to zero".
    @Test
    fun chips_atDoubleFontScaleAndNarrowWidth_remainSingleLineAndScrollable() {
        val counts = LibraryCounts(all = 27, reading = 2, finished = 19, wantToRead = 6, lentOut = 3)
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(2f)
                    .then(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 800.dp))),
            ) {
                BookshelfTheme {
                    Surface {
                        LibraryScreen(
                            uiState = LibraryUiState.Content(
                                LibraryRows(emptyList(), emptyList()),
                                LibraryGrid(LibraryFilter.All, counts, emptyList()),
                            ),
                        )
                    }
                }
            }
        }

        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .onFirst()
            .assertExists()

        for (filter in LibraryFilter.entries) {
            val node = composeRule.onNodeWithTag(libraryFilterChipTag(filter)).fetchSemanticsNode()
            assertTrue(
                "chip $filter must not be clipped to zero width at 2x font scale",
                node.size.width > 0,
            )
            composeRule.onNodeWithTag(libraryFilterChipTag(filter)).assertHeightIsAtLeast(48.dp)
            val heightDp = with(composeRule.density) { node.size.height.toDp() }
            assertTrue(
                "chip $filter must stay on one line (height $heightDp is too tall for a single " +
                    "line at 2x font scale, so it likely wrapped)",
                heightDp < 60.dp,
            )
        }
    }

    // AC2 (component): tapping each chip calls onFilterSelected with the matching filter exactly
    // once; re-tapping the already-selected chip is still called, harmlessly.
    @Test
    fun tappingAChip_callsOnFilterSelected_exactlyOnce() {
        val selections = mutableListOf<LibraryFilter>()
        val counts = LibraryCounts(all = 1, reading = 1, finished = 0, wantToRead = 0, lentOut = 0)
        setContent(
            LibraryGrid(LibraryFilter.All, counts, listOf(LibraryBook(book(1), null))),
            onFilterSelected = { selections += it },
        )

        composeRule.onNodeWithTag(libraryFilterChipTag(LibraryFilter.Reading)).performScrollTo().performClick()
        composeRule.onNodeWithTag(libraryFilterChipTag(LibraryFilter.All)).performScrollTo().performClick()

        assertEquals(listOf(LibraryFilter.Reading, LibraryFilter.All), selections)
    }

    // AC3 (component): cover, title, dot kind and the lent caption, scoped per card so #20's
    // rows (showing the same book) can never be confused with the grid (F7).
    @Test
    fun card_showsCoverTitleDotAndLentCaption_whereApplicable() {
        val loan = OpenLoan(loanId = 1, borrowerName = "Priya", loanedAt = Instant.EPOCH, dueAt = LocalDate.now())
        val lentBook = LibraryBook(book(1, "Sapiens", BookStatus.Finished), loan)
        val plainBook = LibraryBook(book(2, "Pachinko", BookStatus.WantToRead), null)
        val counts = LibraryCounts(all = 2, reading = 0, finished = 1, wantToRead = 1, lentOut = 1)
        setContent(LibraryGrid(LibraryFilter.All, counts, listOf(lentBook, plainBook)))

        composeRule.onNode(
            hasTestTag(BOOK_COVER_TEST_TAG_PREFIX + "1") and hasAnyAncestor(hasTestTag(libraryGridCardTag(1))),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithText("Sapiens").assertIsDisplayed()
        composeRule.onNodeWithTag(libraryGridLentCaptionTag(1), useUnmergedTree = true).assertExists()
        composeRule.onNode(
            SemanticsMatcher.expectValue(LibraryStatusDotKindKey, StatusDotKind.Lent)
                and hasAnyAncestor(hasTestTag(libraryGridCardTag(1))),
            useUnmergedTree = true,
        ).assertExists()

        composeRule.onNodeWithText("Pachinko").assertIsDisplayed()
        composeRule.onNodeWithTag(libraryGridLentCaptionTag(2), useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(
            SemanticsMatcher.expectValue(LibraryStatusDotKindKey, StatusDotKind.WantToRead)
                and hasAnyAncestor(hasTestTag(libraryGridCardTag(2))),
            useUnmergedTree = true,
        ).assertExists()

        // A lent card's merged semantics announce the dot's *status* ("Finished"), not "Lent" -
        // the entire reason LibraryStatusDot takes a status param (G6). The dot sets no
        // contentDescription itself; the card's own merged description carries it instead
        // (LibraryGridCard/LibraryStatusDot KDoc).
        composeRule.onNodeWithTag(libraryGridCardTag(1)).assert(hasContentDescription("Finished", substring = true))
    }

    // AC3 (component): the grid title is announced as a heading, so TalkBack lets users jump
    // straight to "Your library".
    @Test
    fun gridTitle_isAHeading() {
        val counts = LibraryCounts(all = 1, reading = 1, finished = 0, wantToRead = 0, lentOut = 0)
        setContent(LibraryGrid(LibraryFilter.All, counts, listOf(LibraryBook(book(1), null))))

        composeRule.onNodeWithTag(LIBRARY_GRID_TITLE_TEST_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    // AC3 (component): the grid is a fixed 3-column layout (plan G10) - cards 0-2 share a top
    // row and card 3 starts the next row below them.
    @Test
    fun grid_laysOutCardsInThreeColumns() {
        val books = (1..4L).map { LibraryBook(book(it, status = BookStatus.Reading), null) }
        val counts = LibraryCounts(all = 4, reading = 4, finished = 0, wantToRead = 0, lentOut = 0)
        setContent(LibraryGrid(LibraryFilter.Reading, counts, books))

        val top0 = composeRule.onNodeWithTag(libraryGridCardTag(1)).fetchSemanticsNode().boundsInRoot.top
        val top1 = composeRule.onNodeWithTag(libraryGridCardTag(2)).fetchSemanticsNode().boundsInRoot.top
        val top2 = composeRule.onNodeWithTag(libraryGridCardTag(3)).fetchSemanticsNode().boundsInRoot.top
        val top3 = composeRule.onNodeWithTag(libraryGridCardTag(4)).fetchSemanticsNode().boundsInRoot.top

        assertEquals(top0, top1, 0.01f)
        assertEquals(top1, top2, 0.01f)
        assertTrue("card 3 (index 3) must start a new row below the first 3", top3 > top2)
    }

    // AC4 (component): tapping grid cards is a no-op that records clicks without crashing; the
    // screen stays displayed afterward with the same cards present.
    @Test
    fun tappingGridCards_recordsClicks_doesNotCrash() {
        val clicked = mutableListOf<Long>()
        val counts = LibraryCounts(all = 2, reading = 2, finished = 0, wantToRead = 0, lentOut = 0)
        setContent(
            LibraryGrid(
                LibraryFilter.All,
                counts,
                listOf(LibraryBook(book(1, "Alpha"), null), LibraryBook(book(2, "Beta"), null)),
            ),
            onBookClick = { clicked += it },
        )

        composeRule.onNodeWithTag(libraryGridCardTag(1)).performClick()
        composeRule.onNodeWithTag(libraryGridCardTag(2)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(1L, 2L), clicked)
        composeRule.onNodeWithTag(libraryGridCardTag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(libraryGridCardTag(2)).assertIsDisplayed()
    }

    // G5, updated for BOOKSHELF-0022 AC1-AC3: a 0-book library hides the chip row, the "Your
    // library" title and the grid entirely, and shows the empty-state panel instead - the
    // #20/#22 "exactly 5 texts" assertion (LibraryScreenTest) must keep passing.
    @Test
    fun emptyLibrary_hidesChipsTitleAndGrid() {
        setContent(LibraryGrid.Empty)

        composeRule.onAllNodesWithTag(LIBRARY_FILTER_CHIPS_TEST_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(LIBRARY_GRID_TITLE_TEST_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag(LIBRARY_EMPTY_STATE_TEST_TAG).assertIsDisplayed()
        // The header's 3 texts (title, tagline, search placeholder) plus the empty-state
        // heading and body - the #20/#22 LibraryScreenTest "exactly 5 texts" assertion for an
        // empty library still holds.
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(5)
    }

    // G5/F13: non-zero total counts but the selected filter matches nothing - chips and title
    // still show, zero cards, no invented empty-state message.
    @Test
    fun nonEmptyLibrary_emptyFilterResult_keepsChipsAndTitle_showsNoCards_andNoMessage() {
        val counts = LibraryCounts(all = 4, reading = 1, finished = 2, wantToRead = 1, lentOut = 0)
        setContent(LibraryGrid(LibraryFilter.LentOut, counts, emptyList()))

        composeRule.onNodeWithTag(LIBRARY_FILTER_CHIPS_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_GRID_TITLE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Lent out · 0").assertExists()
        // AC2/AC3 disambiguation (BOOKSHELF-0022): a non-empty library with a zero-result filter
        // keeps the chips and shows no empty-state panel - that panel is reserved for a truly
        // empty library (0 books total), not "this filter matches nothing".
        composeRule.onAllNodesWithTag(LIBRARY_EMPTY_STATE_TEST_TAG).assertCountEquals(0)
        // The header's 3 texts + the 5 chip labels + the grid title - no card and no invented
        // empty message.
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(9)
    }

    // F14: the rendered card count for a filter matches that filter's chip count digit.
    @Test
    fun renderedCardCount_matchesTheSelectedFilterChipCount() {
        val books = listOf(
            LibraryBook(book(1, status = BookStatus.Reading), null),
            LibraryBook(book(2, status = BookStatus.Reading), null),
        )
        val counts = LibraryCounts(all = 2, reading = 2, finished = 0, wantToRead = 0, lentOut = 0)
        setContent(LibraryGrid(LibraryFilter.Reading, counts, books))

        composeRule.onNodeWithTag(libraryGridCardTag(1)).assertExists()
        composeRule.onNodeWithTag(libraryGridCardTag(2)).assertExists()
    }

    // F1 (AC1): long-pressing a card shows the popover with the remove item, and does not
    // navigate to Detail.
    @Test
    fun longPressingACard_opensMenu_doesNotNavigate() {
        val clicked = mutableListOf<Long>()
        val counts = LibraryCounts(all = 1, reading = 1, finished = 0, wantToRead = 0, lentOut = 0)
        setContent(LibraryGrid(LibraryFilter.All, counts, listOf(LibraryBook(book(1), null))), onBookClick = { clicked += it })

        composeRule.onNodeWithTag(libraryGridCardTag(1)).performTouchInput { longClick() }

        composeRule.onNodeWithTag(LIBRARY_CARD_MENU_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_CARD_MENU_REMOVE_TEST_TAG).assertIsDisplayed()
        assertTrue("a long-press must never also navigate", clicked.isEmpty())
    }

    // F2 (AC1): a plain tap still opens Detail and shows no menu.
    @Test
    fun plainTapOnACard_opensDetail_showsNoMenu() {
        val clicked = mutableListOf<Long>()
        val counts = LibraryCounts(all = 1, reading = 1, finished = 0, wantToRead = 0, lentOut = 0)
        setContent(LibraryGrid(LibraryFilter.All, counts, listOf(LibraryBook(book(1), null))), onBookClick = { clicked += it })

        composeRule.onNodeWithTag(libraryGridCardTag(1)).performClick()

        assertEquals(listOf(1L), clicked)
        composeRule.onAllNodesWithTag(LIBRARY_CARD_MENU_TEST_TAG).assertCountEquals(0)
    }

    // F6 (AC2): tapping "⋮" opens the very same menu tag, and does not navigate to Detail.
    @Test
    fun tappingOverflowButton_opensSameMenu_doesNotNavigate() {
        val clicked = mutableListOf<Long>()
        val removed = mutableListOf<Long>()
        val counts = LibraryCounts(all = 1, reading = 1, finished = 0, wantToRead = 0, lentOut = 0)
        setContent(
            LibraryGrid(LibraryFilter.All, counts, listOf(LibraryBook(book(1), null))),
            onBookClick = { clicked += it },
            onRemoveBookRequest = { removed += it },
        )

        composeRule.onNodeWithTag(libraryGridCardMenuButtonTag(1), useUnmergedTree = true).performClick()

        composeRule.onNodeWithTag(LIBRARY_CARD_MENU_TEST_TAG).assertIsDisplayed()
        assertTrue("tapping the overflow button must never navigate", clicked.isEmpty())

        composeRule.onNodeWithTag(LIBRARY_CARD_MENU_REMOVE_TEST_TAG).performClick()
        assertEquals(listOf(1L), removed)
    }

    // F9 (plan security notes): tapping outside the popover closes it with no side effect - the
    // remove callback is never invoked.
    @Test
    fun tappingOutsideTheOpenMenu_closesIt_noRemoveCallback() {
        val removed = mutableListOf<Long>()
        val counts = LibraryCounts(all = 1, reading = 1, finished = 0, wantToRead = 0, lentOut = 0)
        setContent(
            LibraryGrid(LibraryFilter.All, counts, listOf(LibraryBook(book(1), null))),
            onRemoveBookRequest = { removed += it },
        )

        composeRule.onNodeWithTag(libraryGridCardTag(1)).performTouchInput { longClick() }
        composeRule.onNodeWithTag(LIBRARY_CARD_MENU_TEST_TAG).assertIsDisplayed()

        composeRule.onNodeWithTag(LIBRARY_GRID_TITLE_TEST_TAG).performClick()

        composeRule.onAllNodesWithTag(LIBRARY_CARD_MENU_TEST_TAG).assertCountEquals(0)
        assertTrue(removed.isEmpty())
    }

    // F10/AC4: the "⋮" is its own clickable, role Button, with a content description that names
    // the book.
    @Test
    fun overflowButton_hasClickAction_roleButton_andTitledDescription() {
        val counts = LibraryCounts(all = 1, reading = 1, finished = 0, wantToRead = 0, lentOut = 0)
        setContent(LibraryGrid(LibraryFilter.All, counts, listOf(LibraryBook(book(1, "Sapiens"), null))))

        val node = composeRule.onNodeWithTag(libraryGridCardMenuButtonTag(1), useUnmergedTree = true)
        node.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        node.assert(hasContentDescription("Sapiens", substring = true))
    }

    // F11/AC4: the "⋮" description is absent from the card's own merged semantics (it is a
    // separate focus stop), and the card still exposes a long-click action labelled "Show book
    // actions".
    @Test
    fun overflowButton_isNotMergedIntoCard_cardStillExposesLongClickAction() {
        val counts = LibraryCounts(all = 1, reading = 1, finished = 0, wantToRead = 0, lentOut = 0)
        setContent(LibraryGrid(LibraryFilter.All, counts, listOf(LibraryBook(book(1, "Sapiens"), null))))

        composeRule.onNodeWithTag(libraryGridCardTag(1))
            .assert(notMatching(hasContentDescription("More options", substring = true)))
        composeRule.onNodeWithTag(libraryGridCardTag(1))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
    }

    // F12/K10: two cards' menu state is independent - opening one never opens the other.
    @Test
    fun twoCardsMenuState_isIndependent() {
        val counts = LibraryCounts(all = 2, reading = 2, finished = 0, wantToRead = 0, lentOut = 0)
        setContent(
            LibraryGrid(
                LibraryFilter.All,
                counts,
                listOf(LibraryBook(book(1, "Alpha"), null), LibraryBook(book(2, "Beta"), null)),
            ),
        )

        composeRule.onNodeWithTag(libraryGridCardTag(1)).performTouchInput { longClick() }

        composeRule.onAllNodesWithTag(LIBRARY_CARD_MENU_TEST_TAG).assertCountEquals(1)
        composeRule.onNodeWithTag(libraryGridCardMenuButtonTag(2), useUnmergedTree = true).assertExists()
    }
}

private fun notMatching(matcher: SemanticsMatcher): SemanticsMatcher =
    SemanticsMatcher("NOT (${matcher.description})") { !matcher.matches(it) }
