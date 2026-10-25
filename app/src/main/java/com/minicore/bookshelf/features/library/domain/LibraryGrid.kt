package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts

/**
 * One emission = one consistent chip-row + grid state (plan G2): the selected [filter], its
 * chip [counts] and the [books] that filter currently matches, in repository order (never
 * re-sorted here).
 */
data class LibraryGrid(
    val filter: LibraryFilter,
    val counts: LibraryCounts,
    val books: List<LibraryBook>,
) {
    companion object {
        val Empty = LibraryGrid(
            filter = LibraryFilter.All,
            counts = LibraryCounts(0, 0, 0, 0, 0),
            books = emptyList(),
        )
    }
}
