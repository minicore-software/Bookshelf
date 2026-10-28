package com.minicore.bookshelf.features.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
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

const val LIBRARY_BODY_TEST_TAG = "library_body"
const val LIBRARY_GRID_BOTTOM_TEST_TAG = "library_grid_bottom"

// E7 (plan `docs/plans/22-empty-state-library-screen.md`): 20dp margin + 52dp FAB + 16dp gap, so
// the grid's content never ends up permanently hidden under the FAB.
private val FabClearance = 88.dp
private val FabMargin = 20.dp

/**
 * The Library home screen. Shows the fixed wordmark header, then a scrolling body with the
 * currently-reading and lent-out rows (plan R10/R11, BOOKSHELF-0020), the filter chips and the
 * full library grid (plan G5/G10, BOOKSHELF-0021), and the "Add a book" FAB overlaid bottom-end
 * (plan E6/E7, BOOKSHELF-0022).
 *
 * @param uiState required: [LibraryUiState.Loading] renders only the header and the FAB. A 0-book
 *   library ([LibraryUiState.Content.isLibraryEmpty]) hides the rows, the chip row and the "Your
 *   library" title, and shows [LibraryEmptyState] instead (plan E1-E3, BOOKSHELF-0022). An empty
 *   row alone is still hidden entirely rather than shown with no cards (plan R11/G5). When the
 *   library is not empty, the chip row, title and grid always show, even when the selected filter
 *   matches nothing (plan G5).
 * @param onBookClick navigates to Book Detail with the tapped book's id (BOOKSHELF-0032) -
 *   defaults to a no-op so previews and simple call sites do not need to pass one. Grid-card taps
 *   reuse it (plan G12).
 * @param onSeeAllLoansClick a stub until My Loans ships (epic #17 D19).
 * @param onFilterSelected called with the tapped chip's [LibraryFilter] - defaults to a no-op.
 * @param onAddBookClick a stub until Add Book ships (epic #17 D9) - defaults to a no-op.
 * @param onRemoveBookRequest called with a grid card's book id, from either the long-press
 *   popover or its "⋮" menu (plan #35 4.1) - defaults to a no-op.
 */
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onBookClick: (bookId: Long) -> Unit = {},
    onSeeAllLoansClick: () -> Unit = {},
    onFilterSelected: (LibraryFilter) -> Unit = {},
    onAddBookClick: () -> Unit = {},
    onRemoveBookRequest: (bookId: Long) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryHeader(onSearchClick = onSearchClick)

            val content = uiState as? LibraryUiState.Content
            val rows = content?.rows
            val grid = content?.grid
            val isEmpty = content?.isLibraryEmpty == true

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(LIBRARY_BODY_TEST_TAG),
                contentPadding = PaddingValues(bottom = FabClearance),
            ) {
                if (rows != null && rows.currentlyReading.isNotEmpty()) {
                    fullSpanItem(key = "reading_section") {
                        CurrentlyReadingSection(
                            books = rows.currentlyReading,
                            onBookClick = onBookClick,
                        )
                    }
                }
                if (rows != null && rows.lentOut.isNotEmpty()) {
                    fullSpanItem(key = "lent_section") {
                        LentOutSection(
                            rows = rows.lentOut,
                            onBookClick = onBookClick,
                            onSeeAllLoansClick = onSeeAllLoansClick,
                        )
                    }
                }
                if (isEmpty) {
                    fullSpanItem(key = "empty_state") {
                        LibraryEmptyState()
                    }
                }
                if (grid != null && grid.counts.all > 0) {
                    fullSpanItem(key = "filter_chips") {
                        LibraryFilterChips(
                            selected = grid.filter,
                            counts = grid.counts,
                            onFilterSelected = onFilterSelected,
                        )
                    }
                    fullSpanItem(key = "grid_title") {
                        LibraryGridTitle()
                    }
                    itemsIndexed(
                        grid.books,
                        key = { _, book -> "grid_book_${book.book.id}" },
                        contentType = { _, _ -> "grid_book" },
                    ) { index, book ->
                        LibraryGridCard(
                            libraryBook = book,
                            column = index % 3,
                            onBookClick = onBookClick,
                            onRemoveBookRequest = onRemoveBookRequest,
                        )
                    }
                    fullSpanItem(key = "grid_bottom") {
                        Spacer(modifier = Modifier.height(10.dp).testTag(LIBRARY_GRID_BOTTOM_TEST_TAG))
                    }
                }
            }
        }

        LibraryAddBookFab(
            onClick = onAddBookClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = FabMargin, bottom = FabMargin),
        )

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private fun LazyGridScope.fullSpanItem(
    key: String,
    content: @Composable () -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Preview(name = "Library — empty")
@Composable
private fun LibraryScreenPreview() {
    BookshelfTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibraryScreen(uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())))
        }
    }
}

// E9: the empty state at 2x font scale on a narrow/short screen - the panel must scroll into
// view and the FAB must never permanently cover it.
@Preview(name = "Library — empty, 2x font", fontScale = 2f)
@Composable
private fun LibraryScreenEmptyDoubleFontScalePreview() {
    BookshelfTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibraryScreen(uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())))
        }
    }
}

@Preview(name = "Library — empty, 320dp", widthDp = 320)
@Composable
private fun LibraryScreenEmptyNarrowPreview() {
    BookshelfTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibraryScreen(uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())))
        }
    }
}

private fun previewScreenBook(id: Long, title: String, status: BookStatus) = Book(
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
    comment = null,
    memoryWhy = null,
    memoryWhere = null,
    memoryWhat = null,
    createdAt = Instant.EPOCH,
)

// Plan section 9 step 8: a populated preview, with chips and grid cells, next to Main.dc.html.
@Preview(name = "Library — populated")
@Composable
private fun LibraryScreenPopulatedPreview() {
    val loan = OpenLoan(loanId = 1, borrowerName = "Priya", loanedAt = Instant.EPOCH, dueAt = LocalDate.now())
    val reading = previewScreenBook(1, "The Hidden Life of Trees", BookStatus.Reading)
    val books = listOf(
        LibraryBook(reading, null),
        LibraryBook(previewScreenBook(2, "Project Hail Mary", BookStatus.Reading), null),
        LibraryBook(previewScreenBook(3, "Braiding Sweetgrass", BookStatus.Finished), null),
        LibraryBook(previewScreenBook(4, "Pachinko", BookStatus.WantToRead), null),
        LibraryBook(previewScreenBook(5, "Sapiens", BookStatus.Finished), loan),
    )
    BookshelfTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibraryScreen(
                uiState = LibraryUiState.Content(
                    rows = LibraryRows(currentlyReading = listOf(reading), lentOut = emptyList()),
                    grid = LibraryGrid(
                        filter = LibraryFilter.All,
                        counts = LibraryCounts(all = 27, reading = 2, finished = 19, wantToRead = 6, lentOut = 3),
                        books = books,
                    ),
                ),
            )
        }
    }
}
