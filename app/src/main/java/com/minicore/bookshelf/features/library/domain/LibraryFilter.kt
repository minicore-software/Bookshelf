package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.features.library.domain.model.LibraryCounts

/** One chip. Order of entries = chip order on screen (plan section 4). */
enum class LibraryFilter { All, Reading, Finished, WantToRead, LentOut }

/**
 * Chip badge for [filter] (plan section 4). `LentOut` reads [LibraryCounts.lentOut] - the
 * open-loan count, never a [com.minicore.bookshelf.core.model.BookStatus].
 */
fun LibraryCounts.countFor(filter: LibraryFilter): Int = when (filter) {
    LibraryFilter.All -> all
    LibraryFilter.Reading -> reading
    LibraryFilter.Finished -> finished
    LibraryFilter.WantToRead -> wantToRead
    LibraryFilter.LentOut -> lentOut
}
