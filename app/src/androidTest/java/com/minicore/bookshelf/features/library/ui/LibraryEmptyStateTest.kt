package com.minicore.bookshelf.features.library.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.features.library.domain.LibraryFilter
import com.minicore.bookshelf.features.library.domain.LibraryGrid
import com.minicore.bookshelf.features.library.domain.LibraryRows
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * AC1-AC4 (plan `docs/plans/22-empty-state-library-screen.md` section 8): the stateless
 * [LibraryScreen]'s 0-book empty state and the "Add a book" FAB.
 */
class LibraryEmptyStateTest {

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

    // AC1/AC2/AC3: a 0-book library hides the rows, shows the empty-state panel with a heading
    // and body, and shows no grid card and no "Your library" title.
    @Test
    fun zeroBooks_showsEmptyState_hidesRowsTitleAndCards() {
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())))
                }
            }
        }

        composeRule.onNodeWithTag(LIBRARY_READING_ROW_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(LIBRARY_LENT_ROW_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Currently reading").assertDoesNotExist()
        composeRule.onNodeWithText("Lent out").assertDoesNotExist()

        composeRule.onNodeWithTag(LIBRARY_EMPTY_STATE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("An empty shelf").assertIsDisplayed()
        composeRule.onNodeWithText("Books you add will appear here.").assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_GRID_TITLE_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Your library").assertDoesNotExist()
    }

    // AC2: the empty-state heading is announced as a heading.
    @Test
    fun emptyState_headingIsAHeading() {
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())))
                }
            }
        }

        composeRule.onNodeWithText("An empty shelf")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    // AC3: no filter chips container and no chip tag for any filter at 0 books.
    @Test
    fun zeroBooks_hidesAllFilterChips() {
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())))
                }
            }
        }

        composeRule.onAllNodesWithTag(LIBRARY_FILTER_CHIPS_TEST_TAG).assertCountEquals(0)
        for (filter in LibraryFilter.entries) {
            composeRule.onNodeWithTag(libraryFilterChipTag(filter)).assertDoesNotExist()
        }
    }

    // Loading: no empty-state panel, the FAB is still displayed.
    @Test
    fun loadingState_showsNoEmptyState_fabStillDisplayed() {
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(uiState = LibraryUiState.Loading)
                }
            }
        }

        composeRule.onNodeWithTag(LIBRARY_EMPTY_STATE_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).assertIsDisplayed()
    }

    // Frame-skew case (plan E1): counts.all == 0 but one row is non-empty - the row shows, no
    // empty-state panel, no chips.
    @Test
    fun zeroCountsButRowNonEmpty_showsRow_noEmptyStateNoChips() {
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(
                        uiState = LibraryUiState.Content(
                            rows = LibraryRows(currentlyReading = listOf(book(1)), lentOut = emptyList()),
                            grid = LibraryGrid.Empty,
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(LIBRARY_READING_ROW_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_EMPTY_STATE_TEST_TAG).assertDoesNotExist()
        composeRule.onAllNodesWithTag(LIBRARY_FILTER_CHIPS_TEST_TAG).assertCountEquals(0)
    }

    // AC4: the FAB is displayed with the right label, role and touch target.
    @Test
    fun fab_hasContentDescriptionRoleAndMinimumSize() {
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())))
                }
            }
        }

        composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG)
            .assertIsDisplayed()
            .assert(hasContentDescription("Add a book"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    // AC4: tapping the FAB once calls the callback exactly once, does not crash, and the empty
    // state is unchanged afterward.
    @Test
    fun tappingFabOnce_callsCallbackExactlyOnce_emptyStateUnchanged() {
        var clicks = 0
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(
                        uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())),
                        onAddBookClick = { clicks++ },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(1, clicks)
        composeRule.onNodeWithTag(LIBRARY_EMPTY_STATE_TEST_TAG).assertIsDisplayed()
    }

    // Tapping the FAB while Loading (callback wired before the first Content arrives): no
    // crash, callback fires once.
    @Test
    fun tappingFabDuringLoading_doesNotCrash_callsCallbackOnce() {
        var clicks = 0
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(uiState = LibraryUiState.Loading, onAddBookClick = { clicks++ })
                }
            }
        }

        composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(1, clicks)
    }

    // Double-tapping the FAB calls the callback exactly twice, with no crash or duplicate state.
    @Test
    fun doubleTappingFab_callsCallbackExactlyTwice_noCrash() {
        var clicks = 0
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(
                        uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())),
                        onAddBookClick = { clicks++ },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).performClick()
        composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(2, clicks)
    }

    // FF4: with a populated library, after scrolling to the bottom of the grid, the last card's
    // bottom edge never overlaps the FAB's top edge.
    @Test
    fun populatedLibrary_scrolledToBottom_lastCardNeverOverlapsFab() {
        val books = (1..12L).map { LibraryBook(book(it, status = BookStatus.Reading), null) }
        val counts = LibraryCounts(all = 12, reading = 12, finished = 0, wantToRead = 0, lentOut = 0)
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(
                        uiState = LibraryUiState.Content(
                            LibraryRows(emptyList(), emptyList()),
                            LibraryGrid(LibraryFilter.All, counts, books),
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(LIBRARY_BODY_TEST_TAG)
            .performScrollToNode(hasTestTag(LIBRARY_GRID_BOTTOM_TEST_TAG))
        composeRule.waitForIdle()

        val lastCardBottom = composeRule.onNodeWithTag(libraryGridCardTag(12)).fetchSemanticsNode().boundsInRoot.bottom
        val fabTop = composeRule.onNodeWithTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG).fetchSemanticsNode().boundsInRoot.top

        assertEquals(true, lastCardBottom <= fabTop)
    }

    // At 2x font scale on a 320dp-wide, 640dp-tall viewport, the empty-state heading and body are
    // fully readable after scrolling, and are not permanently covered by the FAB.
    @Test
    fun emptyState_atDoubleFontScaleAndNarrowShortScreen_remainsReadableAfterScroll() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(2f)
                    .then(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 640.dp))),
            ) {
                BookshelfTheme {
                    Surface {
                        LibraryScreen(uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())))
                    }
                }
            }
        }

        composeRule.onNodeWithText("An empty shelf").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Books you add will appear here.").performScrollTo().assertIsDisplayed()
    }
}
