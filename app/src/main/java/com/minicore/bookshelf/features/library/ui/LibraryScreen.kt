package com.minicore.bookshelf.features.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.features.library.domain.LibraryFilter
import com.minicore.bookshelf.features.library.domain.LibraryRows

const val LIBRARY_BODY_TEST_TAG = "library_body"

/**
 * The Library home screen. Shows the fixed wordmark header, then a scrolling body with the
 * currently-reading and lent-out rows (plan R10/R11, BOOKSHELF-0020), the filter chips and the
 * full library grid (plan G5/G10, BOOKSHELF-0021).
 *
 * @param uiState required: [LibraryUiState.Loading] renders nothing; an empty row or an empty
 *   library is hidden entirely rather than shown with no cards (plan R11/G5). When
 *   `grid.counts.all == 0` the chip row, the "Your library" title and the grid are all hidden;
 *   otherwise they always show, even when the selected filter matches nothing (plan G5).
 * @param onBookClick a stub until Book Detail ships (epic #17 D18) - defaults to a no-op so
 *   previews and simple call sites do not need to pass one. Grid-card taps reuse it (plan G12).
 * @param onSeeAllLoansClick a stub until My Loans ships (epic #17 D19).
 * @param onFilterSelected called with the tapped chip's [LibraryFilter] - defaults to a no-op.
 */
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onBookClick: (bookId: Long) -> Unit = {},
    onSeeAllLoansClick: () -> Unit = {},
    onFilterSelected: (LibraryFilter) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        LibraryHeader(onSearchClick = onSearchClick)

        val content = uiState as? LibraryUiState.Content
        val rows = content?.rows
        val grid = content?.grid

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(LIBRARY_BODY_TEST_TAG),
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
                    )
                }
                fullSpanItem(key = "grid_bottom") {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

private fun LazyGridScope.fullSpanItem(
    key: String,
    content: @Composable () -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Preview(name = "Library")
@Composable
private fun LibraryScreenPreview() {
    BookshelfTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibraryScreen(uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())))
        }
    }
}
