package com.minicore.bookshelf.features.library.ui

import com.minicore.bookshelf.features.library.domain.LibraryRows

/** The Library screen's view state (plan R7). #21 adds chip/grid fields to [Content]. */
sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Content(val rows: LibraryRows) : LibraryUiState
}
