package com.minicore.bookshelf.features.library.domain

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines the repository's currently-reading and lent-out flows with the current render date
 * to build [LibraryRows] (plan R7). Order is the repository's own, never re-sorted here (plan
 * section 4).
 */
class ObserveLibraryRowsUseCase @Inject constructor(
    private val repository: LibraryRepository,
    private val currentDate: CurrentDateProvider,
) {

    operator fun invoke(): Flow<LibraryRows> = combine(
        repository.observeCurrentlyReading(),
        repository.observeLentOutBooks(),
        currentDate.observe(),
    ) { reading, lent, today ->
        LibraryRows(
            currentlyReading = reading.map { it.book },
            lentOut = lent.map {
                LentOutRow(
                    book = it.book,
                    borrowerName = it.loan.borrowerName,
                    dueStatus = LoanDueStatus.of(it.loan.dueAt, today),
                )
            },
        )
    }
}
