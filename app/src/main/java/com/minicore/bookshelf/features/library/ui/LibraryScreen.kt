package com.minicore.bookshelf.features.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.features.library.domain.LibraryRows

const val LIBRARY_BODY_TEST_TAG = "library_body"

/**
 * The Library home screen. Shows the fixed wordmark header, then a scrolling body with the
 * currently-reading and lent-out rows (plan R10/R11, BOOKSHELF-0020) - #21 extends the same
 * lazy grid with status chips and the full library grid.
 *
 * @param uiState required: [LibraryUiState.Loading] renders no rows; an empty row is hidden
 *   entirely rather than shown with no cards (plan R11).
 * @param onBookClick a stub until Book Detail ships (epic #17 D18) - defaults to a no-op so
 *   previews and simple call sites do not need to pass one.
 * @param onSeeAllLoansClick a stub until My Loans ships (epic #17 D19).
 */
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onBookClick: (bookId: Long) -> Unit = {},
    onSeeAllLoansClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        LibraryHeader(onSearchClick = onSearchClick)

        val rows = (uiState as? LibraryUiState.Content)?.rows

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
