package com.minicore.bookshelf.features.library.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Stateful wrapper around the stateless [LibraryScreen] (plan R7). */
@Composable
fun LibraryRoute(
    onSearchClick: () -> Unit,
    onBookClick: (bookId: Long) -> Unit,
    onSeeAllLoansClick: () -> Unit,
    onAddBookClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LibraryScreen(
        uiState = uiState,
        modifier = modifier,
        onSearchClick = onSearchClick,
        onBookClick = onBookClick,
        onSeeAllLoansClick = onSeeAllLoansClick,
        onFilterSelected = viewModel::onFilterSelected,
        onAddBookClick = onAddBookClick,
    )
}
