package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Chooses the one repository query [filter] maps to (plan G1) and combines it with the chip
 * counts into one [LibraryGrid] emission (plan G2). `LentOut` always calls
 * [LibraryRepository.observeLentOutBooks] - it never consults [BookStatus] or
 * [LibraryRepository.observeBooks].
 */
class ObserveLibraryGridUseCase @Inject constructor(
    private val repository: LibraryRepository,
) {

    operator fun invoke(filter: LibraryFilter): Flow<LibraryGrid> =
        combine(repository.observeCounts(), booksFor(filter)) { counts, books ->
            LibraryGrid(filter = filter, counts = counts, books = books)
        }

    private fun booksFor(filter: LibraryFilter): Flow<List<LibraryBook>> = when (filter) {
        LibraryFilter.All -> repository.observeBooks(null)
        LibraryFilter.Reading -> repository.observeBooks(BookStatus.Reading)
        LibraryFilter.Finished -> repository.observeBooks(BookStatus.Finished)
        LibraryFilter.WantToRead -> repository.observeBooks(BookStatus.WantToRead)
        LibraryFilter.LentOut -> repository.observeLentOutBooks()
            .map { lentBooks -> lentBooks.map { LibraryBook(it.book, it.loan) } }
    }
}
