package com.minicore.bookshelf.features.library.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minicore.bookshelf.R

/**
 * Stateful wrapper around the stateless [LibraryScreen] (plan R7).
 *
 * @param showBookAddedSnackbar A12: `true` the moment "add a book" reports a successful save -
 *   `navigation/BookshelfNavGraph.kt` is the only place that knows *why* (it never passes an
 *   `AddBookResult` down here, keeping this route free of any `ui.addbook` import, `FF-A4`). Shown
 *   once per `true` value; the caller is responsible for flipping it back to `false`.
 */
@Composable
fun LibraryRoute(
    onSearchClick: () -> Unit,
    onBookClick: (bookId: Long) -> Unit,
    onSeeAllLoansClick: () -> Unit,
    onAddBookClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBookAddedSnackbar: Boolean = false,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val bookAddedMessage = stringResource(R.string.add_book_added_snackbar)

    LaunchedEffect(showBookAddedSnackbar) {
        if (showBookAddedSnackbar) snackbarHostState.showSnackbar(bookAddedMessage)
    }

    LibraryScreen(
        uiState = uiState,
        modifier = modifier,
        onSearchClick = onSearchClick,
        onBookClick = onBookClick,
        onSeeAllLoansClick = onSeeAllLoansClick,
        onFilterSelected = viewModel::onFilterSelected,
        onAddBookClick = onAddBookClick,
        snackbarHostState = snackbarHostState,
    )
}
