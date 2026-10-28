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
import com.minicore.bookshelf.features.library.ui.removebook.RemoveBookDialog

/**
 * Stateful wrapper around the stateless [LibraryScreen] (plan R7).
 *
 * @param bookAddedEventId A12: the new book's id the moment "add a book" reports a successful
 *   save, else `null` - `navigation/BookshelfNavGraph.kt` is the only place that knows *why* (it
 *   never passes an `AddBookResult` down here, keeping this route free of any `ui.addbook`
 *   import, `FF-A4`). A fresh id every save makes the [LaunchedEffect] below naturally one-shot
 *   per save - unlike a `Boolean`, nothing needs to (or may) reset this value *before* the
 *   Snackbar is shown, which previously raced with and cancelled the Snackbar mid-flight
 *   (critical finding #5). It must, however, be cleared *after* the Snackbar has shown via
 *   [onBookAddedSnackbarShown], else an Activity recreation (rotation/config-change/process-death
 *   restore) replays the same non-null id and reshows the Snackbar (critical finding round 2).
 * @param onBookAddedSnackbarShown Invoked once the "added to your shelf" Snackbar has been shown
 *   for [bookAddedEventId], so the caller can clear the underlying saved-state value.
 */
@Composable
fun LibraryRoute(
    onSearchClick: () -> Unit,
    onBookClick: (bookId: Long) -> Unit,
    onSeeAllLoansClick: () -> Unit,
    onAddBookClick: () -> Unit,
    modifier: Modifier = Modifier,
    bookAddedEventId: Long? = null,
    onBookAddedSnackbarShown: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val removePrompt by viewModel.removePrompt.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val bookAddedMessage = stringResource(R.string.add_book_added_snackbar)
    val removeFailedMessage = stringResource(R.string.remove_book_failed)

    LaunchedEffect(bookAddedEventId) {
        if (bookAddedEventId != null) {
            snackbarHostState.showSnackbar(bookAddedMessage)
            onBookAddedSnackbarShown()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                LibraryEvent.RemoveFailed -> snackbarHostState.showSnackbar(removeFailedMessage)
            }
        }
    }

    LibraryScreen(
        uiState = uiState,
        modifier = modifier,
        onSearchClick = onSearchClick,
        onBookClick = onBookClick,
        onSeeAllLoansClick = onSeeAllLoansClick,
        onFilterSelected = viewModel::onFilterSelected,
        onAddBookClick = onAddBookClick,
        onRemoveBookRequest = viewModel::onRemoveRequested,
        snackbarHostState = snackbarHostState,
    )

    val prompt = removePrompt
    if (prompt != null) {
        RemoveBookDialog(
            bookTitle = prompt.title,
            borrowerName = prompt.borrowerName,
            onConfirm = viewModel::onRemoveConfirmed,
            onDismiss = viewModel::onRemoveDismissed,
        )
    }
}
