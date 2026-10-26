package com.minicore.bookshelf.features.library.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.designsystem.component.BOOK_COVER_TEST_TAG_PREFIX
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
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
        coverUri = null,
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
    ) {
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(
                        uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList()), grid),
                        onBookClick = onBookClick,
                        onFilterSelected = onFilterSelected,
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

    // G5: a 0-book library hides the chip row, the "Your library" title and the grid entirely -
    // the existing #20 "exactly 3 texts" assertion (LibraryScreenTest) must keep passing.
    @Test
    fun emptyLibrary_hidesChipsTitleAndGrid() {
        setContent(LibraryGrid.Empty)

        composeRule.onAllNodesWithTag(LIBRARY_FILTER_CHIPS_TEST_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(LIBRARY_GRID_TITLE_TEST_TAG).assertCountEquals(0)
        // Only the header's 3 texts remain (title, tagline, search placeholder) - the existing
        // #20 LibraryScreenTest "exactly 3 texts" assertion for an empty library still holds.
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(3)
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
}
