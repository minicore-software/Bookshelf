package com.minicore.bookshelf.features.library.ui

import com.minicore.bookshelf.features.library.domain.LibraryGrid
import com.minicore.bookshelf.features.library.domain.LibraryRows

/**
 * The Library screen's view state (plan R7). [Content.grid] defaults to [LibraryGrid.Empty] so
 * that an empty grid renders nothing (plan G4/G5) - production code (the ViewModel) always
 * passes a real [LibraryGrid].
 */
sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Content(val rows: LibraryRows, val grid: LibraryGrid = LibraryGrid.Empty) : LibraryUiState
}
