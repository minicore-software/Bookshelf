package com.minicore.bookshelf.features.library.data

import com.minicore.bookshelf.core.database.dao.BookDao
import com.minicore.bookshelf.core.database.dao.LoanDao
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomLibraryRepository @Inject constructor(
    private val bookDao: BookDao,
    private val loanDao: LoanDao,
) : LibraryRepository {

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> =
        bookDao.observeLibraryBooks(status).map { rows -> rows.map { it.toLibraryBook() } }

    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> =
        observeBooks(BookStatus.Reading)

    override fun observeLentOutBooks(): Flow<List<LentBook>> =
        bookDao.observeLentOutBooks().map { rows -> rows.map { it.toLentBook() } }

    override fun observeCounts(): Flow<LibraryCounts> =
        combine(bookDao.observeStatusCounts(), loanDao.observeOpenLoanCount()) { statusCounts, lentOut ->
            buildLibraryCounts(statusCounts, lentOut)
        }.distinctUntilChanged()
}
