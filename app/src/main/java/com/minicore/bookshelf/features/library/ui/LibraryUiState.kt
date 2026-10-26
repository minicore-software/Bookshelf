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

/**
 * E1 (plan `docs/plans/22-empty-state-library-screen.md`): nothing at all to show - 0 books in
 * total ([LibraryGrid.counts]' [com.minicore.bookshelf.features.library.domain.model.LibraryCounts.all])
 * and both rows empty. Including the rows (not `counts.all == 0` alone) keeps this predicate
 * false for the #20/#21 test seam that builds [LibraryUiState.Content] with populated rows and
 * the default [LibraryGrid.Empty], and tolerates a one-frame skew between the rows and counts
 * Room flows. [LibraryUiState.Loading] is never empty - it is not a [LibraryUiState.Content].
 */
internal val LibraryUiState.Content.isLibraryEmpty: Boolean
    get() = grid.counts.all == 0 && rows.currentlyReading.isEmpty() && rows.lentOut.isEmpty()
