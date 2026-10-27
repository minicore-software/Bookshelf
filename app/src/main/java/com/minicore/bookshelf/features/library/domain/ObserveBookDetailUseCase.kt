package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.features.library.domain.model.BookDetail
import com.minicore.bookshelf.features.library.domain.model.BookDetailLoan
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines the repository's single-book overlay with the current render date to build
 * [BookDetail] (plan #32 decision V4). `null` passes straight through - the book does not exist
 * (or was deleted while the modal was open, QA F17) - so the ViewModel can show `NotFound`.
 * [BookDetailLoan.dueStatus] is recomputed per [CurrentDateProvider] emission, never stored, same
 * rule as [ObserveLibraryRowsUseCase].
 */
class ObserveBookDetailUseCase @Inject constructor(
    private val repository: LibraryRepository,
    private val currentDate: CurrentDateProvider,
) {

    operator fun invoke(bookId: Long): Flow<BookDetail?> = combine(
        repository.observeLibraryBook(bookId),
        currentDate.observe(),
    ) { libraryBook, today ->
        libraryBook?.let {
            BookDetail(
                book = it.book,
                loan = it.openLoan?.let { loan ->
                    BookDetailLoan(
                        borrowerName = loan.borrowerName,
                        dueStatus = LoanDueStatus.of(loan.dueAt, today),
                    )
                },
            )
        }
    }
}
