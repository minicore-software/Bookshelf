package com.minicore.bookshelf.features.library.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.features.library.domain.LentOutRow
import com.minicore.bookshelf.features.library.domain.LibraryRows
import com.minicore.bookshelf.features.library.domain.LoanDueStatus
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class LibraryRowsTest {

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

    private fun setContent(rows: LibraryRows, onBookClick: (Long) -> Unit = {}, onSeeAllLoansClick: () -> Unit = {}) {
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(
                        uiState = LibraryUiState.Content(rows),
                        onBookClick = onBookClick,
                        onSeeAllLoansClick = onSeeAllLoansClick,
                    )
                }
            }
        }
    }

    // F9: only reading books -> reading row shown, lent header+row absent entirely.
    @Test
    fun onlyReadingBooks_showsReadingRow_noLentRow() {
        setContent(LibraryRows(currentlyReading = listOf(book(1)), lentOut = emptyList()))

        composeRule.onNodeWithTag(LIBRARY_READING_ROW_TEST_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag(LIBRARY_LENT_ROW_TEST_TAG).assertCountEquals(0)
        composeRule.onNodeWithText("Lent out").assertDoesNotExist()
    }

    // F10: only lent books -> lent row shown, reading header+row absent.
    @Test
    fun onlyLentBooks_showsLentRow_noReadingRow() {
        val lentRow = LentOutRow(book(1), "Priya", LoanDueStatus.DueSoon(3))
        setContent(LibraryRows(currentlyReading = emptyList(), lentOut = listOf(lentRow)))

        composeRule.onNodeWithTag(LIBRARY_LENT_ROW_TEST_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag(LIBRARY_READING_ROW_TEST_TAG).assertCountEquals(0)
        composeRule.onNodeWithText("Currently reading").assertDoesNotExist()
    }

    // F12: 3 reading books -> only those 3 titles, no progress bar/percentage/page-count text.
    @Test
    fun readingRow_showsOnlyCoversAndTitles_noProgressUi() {
        val books = listOf(book(1, "Alpha"), book(2, "Beta"), book(3, "Gamma"))
        setContent(LibraryRows(currentlyReading = books, lentOut = emptyList()))

        composeRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeRule.onNodeWithText("Beta").assertIsDisplayed()
        composeRule.onNodeWithText("Gamma").assertIsDisplayed()

        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(0)
        composeRule.onAllNodes(hasText("%", substring = true)).assertCountEquals(0)
        composeRule.onAllNodes(hasText("p.", substring = true)).assertCountEquals(0)
    }

    // F13: lent card, borrower "Priya", dueAt = today - 2 -> "With Priya", "Overdue 2 days".
    @Test
    fun lentCard_overdue_showsBorrowerAndOverdueCaption() {
        val lentRow = LentOutRow(book(1, "Loaned Book"), "Priya", LoanDueStatus.Overdue(2))
        setContent(LibraryRows(currentlyReading = emptyList(), lentOut = listOf(lentRow)))

        composeRule.onNode(
            hasTestTag(libraryLentCardTag(1)) and hasText("With Priya", substring = true)
        ).assertIsDisplayed()
        composeRule.onNode(
            hasTestTag(libraryLentCardTag(1)) and hasText("Overdue 2 days", substring = true)
        ).assertIsDisplayed()
    }

    // F14: lent card dueAt = today + 3 -> "Due in 3 days" in the upcoming (non-overdue) state.
    @Test
    fun lentCard_dueSoon_showsDueInDaysCaption() {
        val lentRow = LentOutRow(book(2, "Soon Book"), "Sam", LoanDueStatus.DueSoon(3))
        setContent(LibraryRows(currentlyReading = emptyList(), lentOut = listOf(lentRow)))

        composeRule.onNode(
            hasTestTag(libraryLentCardTag(2)) and hasText("Due in 3 days", substring = true)
        ).assertIsDisplayed()
    }

    // F15: lent card dueAt = today -> "Due today".
    @Test
    fun lentCard_dueToday_showsDueTodayCaption() {
        val lentRow = LentOutRow(book(3, "Today Book"), "Ana", LoanDueStatus.DueToday)
        setContent(LibraryRows(currentlyReading = emptyList(), lentOut = listOf(lentRow)))

        composeRule.onNode(
            hasTestTag(libraryLentCardTag(3)) and hasText("Due today", substring = true)
        ).assertIsDisplayed()
    }

    // F16: "See all" has a click action and meets the 48dp touch target.
    @Test
    fun seeAll_hasClickAction_andMeetsTouchTarget() {
        val lentRow = LentOutRow(book(1), "Priya", LoanDueStatus.DueSoon(1))
        setContent(LibraryRows(currentlyReading = emptyList(), lentOut = listOf(lentRow)))

        composeRule.onNodeWithTag(LIBRARY_SEE_ALL_LOANS_TEST_TAG)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    // F17: rows are horizontally scrollable - the 10th of 10 cards is not displayed initially,
    // then performScrollToIndex(9) makes it displayed.
    @Test
    fun readingRow_isHorizontallyScrollable() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 800.dp))) {
                BookshelfTheme {
                    Surface {
                        LibraryScreen(
                            uiState = LibraryUiState.Content(
                                LibraryRows(
                                    currentlyReading = (0 until 10).map { book(it.toLong(), "Book $it") },
                                    lentOut = emptyList(),
                                ),
                            ),
                        )
                    }
                }
            }
        }

        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .assertCountEquals(1)

        composeRule.onNodeWithTag(libraryReadingCardTag(9)).assertIsNotDisplayed()

        composeRule.onNodeWithTag(LIBRARY_READING_ROW_TEST_TAG).performScrollToIndex(9)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(libraryReadingCardTag(9)).assertIsDisplayed()
    }

    // F18: tapping a reading card, a lent card and "See all" each fire their callback exactly
    // once with the right id (cards) or once (See all); the screen is unchanged afterwards.
    @Test
    fun tappingCardsAndSeeAll_firesCallbacksOnce_doesNotCrash() {
        val clicked = mutableListOf<Long>()
        var seeAllCount = 0
        val readingBook = book(1, "Reading Book")
        val lentRow = LentOutRow(book(2, "Lent Book"), "Priya", LoanDueStatus.DueSoon(1))

        setContent(
            LibraryRows(currentlyReading = listOf(readingBook), lentOut = listOf(lentRow)),
            onBookClick = { clicked += it },
            onSeeAllLoansClick = { seeAllCount++ },
        )

        composeRule.onNodeWithTag(libraryReadingCardTag(1)).performClick()
        composeRule.onNodeWithTag(libraryLentCardTag(2)).performClick()
        composeRule.onNodeWithTag(LIBRARY_SEE_ALL_LOANS_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertCountEqualsOnce(clicked, 1L)
        assertCountEqualsOnce(clicked, 2L)
        org.junit.Assert.assertEquals(1, seeAllCount)

        composeRule.onNodeWithText("Reading Book").assertIsDisplayed()
        composeRule.onNodeWithText("Lent Book").assertIsDisplayed()
    }

    // F25: 2x font scale + a narrow forced size - the lent card's borrower and caption are
    // still displayed with non-zero width, not silently clipped.
    @Test
    fun lentCard_atDoubleFontScaleAndNarrowWidth_remainsUsable() {
        val lentRow = LentOutRow(book(1, "A Book"), "A Long Borrower Name", LoanDueStatus.Overdue(2))
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(2f)
                    .then(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 800.dp)))
            ) {
                BookshelfTheme {
                    Surface {
                        LibraryScreen(
                            uiState = LibraryUiState.Content(
                                LibraryRows(currentlyReading = emptyList(), lentOut = listOf(lentRow)),
                            ),
                        )
                    }
                }
            }
        }

        // useUnmergedTree=true: the card merges its descendants' semantics (for TalkBack), so
        // the borrower and caption are checked as individual unmerged nodes here to assert
        // their own sizes, not the card's overall merged size.
        val borrowerNode = composeRule.onNode(
            hasText("With A Long Borrower Name", substring = true),
            useUnmergedTree = true,
        )
        borrowerNode.assertIsDisplayed()
        assertFalse(borrowerNode.fetchSemanticsNode().size.width == 0)

        val captionNode = composeRule.onNodeWithTag(libraryLentCaptionTag(1), useUnmergedTree = true)
        captionNode.assertIsDisplayed()
        assertFalse(captionNode.fetchSemanticsNode().size.width == 0)
    }

    // F26: removing a book from the lent list and re-emitting Content must not jump/re-key the
    // remaining card - identity is by book.id, proven by the un-removed card staying displayed.
    @Test
    fun lentRow_recomposedWithOneBookRemoved_remainingCardStaysDisplayed() {
        var rows by mutableStateOf(
            LibraryRows(
                currentlyReading = emptyList(),
                lentOut = listOf(
                    LentOutRow(book(1, "First"), "A", LoanDueStatus.DueSoon(1)),
                    LentOutRow(book(2, "Second"), "B", LoanDueStatus.DueSoon(2)),
                ),
            )
        )
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(uiState = LibraryUiState.Content(rows))
                }
            }
        }

        composeRule.onNodeWithTag(libraryLentCardTag(2)).assertIsDisplayed()

        rows = rows.copy(lentOut = rows.lentOut.filter { it.book.id != 1L })
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(libraryLentCardTag(2)).assertIsDisplayed()
        composeRule.onNodeWithTag(libraryLentCardTag(1)).assertDoesNotExist()
    }

    private fun assertCountEqualsOnce(list: List<Long>, value: Long) {
        org.junit.Assert.assertEquals(1, list.count { it == value })
    }

    private fun string(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    // Plan R4/R10: the section title carries heading semantics, both in the reading row (plain
    // trailing label) and the lent row (clickable "See all" trailing label).
    @Test
    fun sectionTitles_carryHeadingSemantics() {
        val lentRow = LentOutRow(book(1), "Priya", LoanDueStatus.DueSoon(1))
        setContent(LibraryRows(currentlyReading = listOf(book(2)), lentOut = listOf(lentRow)))

        composeRule.onNode(
            hasText(string(R.string.library_currently_reading_title)) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText(string(R.string.library_lent_out_title)) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        ).assertIsDisplayed()
    }

    // Plan R11: "1 book" for a single reading book.
    @Test
    fun readingSection_oneBook_showsSingularTrailingLabel() {
        setContent(LibraryRows(currentlyReading = listOf(book(1)), lentOut = emptyList()))

        composeRule.onNodeWithText("1 book").assertIsDisplayed()
    }

    // Plan R11: "3 books" for three reading books.
    @Test
    fun readingSection_threeBooks_showsPluralTrailingLabel() {
        setContent(
            LibraryRows(
                currentlyReading = listOf(book(1), book(2), book(3)),
                lentOut = emptyList(),
            )
        )

        composeRule.onNodeWithText("3 books").assertIsDisplayed()
    }

    // Plan R11/AC3: the lent row's trailing label is "See all · N" with the lent-out count.
    @Test
    fun lentSection_trailingLabel_showsSeeAllWithCount() {
        val rows = listOf(
            LentOutRow(book(1), "A", LoanDueStatus.DueSoon(1)),
            LentOutRow(book(2), "B", LoanDueStatus.DueSoon(2)),
        )
        setContent(LibraryRows(currentlyReading = emptyList(), lentOut = rows))

        composeRule.onNodeWithText("See all · 2").assertIsDisplayed()
    }

    // R11: LibraryUiState.Loading renders neither row, not just an empty Content.
    @Test
    fun loadingState_showsNeitherRow() {
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(uiState = LibraryUiState.Loading)
                }
            }
        }

        composeRule.onAllNodesWithTag(LIBRARY_READING_ROW_TEST_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(LIBRARY_LENT_ROW_TEST_TAG).assertCountEquals(0)
    }

    // F17 counterpart for the lent row: the row itself is horizontally scrollable, not just the
    // reading row - a regression that made only one row scrollable would otherwise go unnoticed.
    @Test
    fun lentRow_isHorizontallyScrollable() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 800.dp))) {
                BookshelfTheme {
                    Surface {
                        LibraryScreen(
                            uiState = LibraryUiState.Content(
                                LibraryRows(
                                    currentlyReading = emptyList(),
                                    lentOut = (0 until 10).map {
                                        LentOutRow(book(it.toLong(), "Book $it"), "Name $it", LoanDueStatus.DueSoon(1))
                                    },
                                ),
                            ),
                        )
                    }
                }
            }
        }

        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            .assertCountEquals(1)

        composeRule.onNodeWithTag(libraryLentCardTag(9)).assertIsNotDisplayed()

        composeRule.onNodeWithTag(LIBRARY_LENT_ROW_TEST_TAG).performScrollToIndex(9)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(libraryLentCardTag(9)).assertIsDisplayed()
    }

    // Plan R4: DueLater renders "Due <localized date>" - the ICU-skeleton-into-java.time path
    // that no other test exercises. Locale forced to en-US so the formatted output is asserted
    // exactly, not just "doesn't crash".
    @Test
    fun lentCard_dueLater_showsFormattedDateCaption_withoutYear() {
        val lentRow = LentOutRow(book(1, "Later Book"), "Priya", LoanDueStatus.DueLater(LocalDate.of(2026, 11, 29), showYear = false))
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("en-US"))) {
                BookshelfTheme {
                    Surface {
                        LibraryScreen(
                            uiState = LibraryUiState.Content(
                                LibraryRows(currentlyReading = emptyList(), lentOut = listOf(lentRow)),
                            ),
                        )
                    }
                }
            }
        }

        composeRule.onNode(
            hasTestTag(libraryLentCardTag(1)) and hasText("Due Nov 29", substring = true)
        ).assertIsDisplayed()
    }

    // Plan R4: DueLater across a year boundary includes the year in the caption.
    @Test
    fun lentCard_dueLaterAcrossYearBoundary_showsYearInCaption() {
        val lentRow = LentOutRow(book(2, "Next Year Book"), "Sam", LoanDueStatus.DueLater(LocalDate.of(2027, 1, 5), showYear = true))
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("en-US"))) {
                BookshelfTheme {
                    Surface {
                        LibraryScreen(
                            uiState = LibraryUiState.Content(
                                LibraryRows(currentlyReading = emptyList(), lentOut = listOf(lentRow)),
                            ),
                        )
                    }
                }
            }
        }

        composeRule.onNode(
            hasTestTag(libraryLentCardTag(2)) and hasText("2027", substring = true)
        ).assertIsDisplayed()
    }
}
