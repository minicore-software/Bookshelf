package com.minicore.bookshelf.features.library.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minicore.bookshelf.core.database.BookshelfDatabase
import com.minicore.bookshelf.core.database.applyBookshelfConfig
import com.minicore.bookshelf.core.database.entity.BookEntity
import com.minicore.bookshelf.core.database.entity.LoanEntity
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AC2/AC3 (plan section 5/8): [RoomLibraryRepository] against real DAOs/Room - the contract #20
 * and #21 build on.
 */
@RunWith(AndroidJUnit4::class)
class RoomLibraryRepositoryTest {

    private lateinit var database: BookshelfDatabase
    private lateinit var repository: RoomLibraryRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .applyBookshelfConfig()
            .build()
        repository = RoomLibraryRepository(database.bookDao(), database.loanDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun insertBook(title: String, status: BookStatus, createdAt: Long): Long =
        database.bookDao().insert(
            BookEntity(
                title = title,
                author = "Author",
                isbn = null,
                coverUri = null,
                genre = null,
                pageCount = null,
                publishedYear = null,
                status = status,
                rating = null,
                synopsis = null,
                createdAt = Instant.ofEpochMilli(createdAt),
            )
        )

    private suspend fun insertOpenLoan(bookId: Long, borrowerName: String = "Borrower"): Long =
        database.loanDao().insert(
            LoanEntity(
                bookId = bookId,
                borrowerName = borrowerName,
                borrowerContact = null,
                loanedAt = Instant.ofEpochMilli(1_000),
                dueAt = LocalDate.of(2026, 11, 1),
                returnedAt = null,
                remindEnabled = false,
                notes = null,
            )
        )

    @Test
    fun observeBooks_withNullStatus_returnsAllBooksWithOverlay() = runTest {
        val wantToRead = insertBook("Want", BookStatus.WantToRead, 1_000)
        val lent = insertBook("Lent", BookStatus.Reading, 2_000)
        val loanId = insertOpenLoan(lent, borrowerName = "Jamie")

        val books = repository.observeBooks().first()

        assertEquals(listOf(lent, wantToRead), books.map { it.book.id })
        assertNull(books.single { it.book.id == wantToRead }.openLoan)
        val expectedOpenLoan = OpenLoan(
            loanId = loanId,
            borrowerName = "Jamie",
            loanedAt = Instant.ofEpochMilli(1_000),
            dueAt = LocalDate.of(2026, 11, 1),
        )
        assertEquals(expectedOpenLoan, books.single { it.book.id == lent }.openLoan)
    }

    @Test
    fun observeCurrentlyReading_returnsOnlyReadingBooks() = runTest {
        insertBook("Want", BookStatus.WantToRead, 1_000)
        val reading = insertBook("Reading", BookStatus.Reading, 2_000)

        val books = repository.observeCurrentlyReading().first()

        assertEquals(listOf(reading), books.map { it.book.id })
    }

    @Test
    fun observeLentOutBooks_returnsOnlyBooksWithOpenLoan() = runTest {
        val lent = insertBook("Lent", BookStatus.Reading, 1_000)
        insertBook("Not Lent", BookStatus.Reading, 2_000)
        insertOpenLoan(lent)

        val lentBooks = repository.observeLentOutBooks().first()

        assertEquals(listOf(lent), lentBooks.map { it.book.id })
    }

    @Test
    fun observeCounts_mixedLibrary_matchesStatusesAndLentOut() = runTest {
        insertBook("Reading 1", BookStatus.Reading, 1_000)
        val reading2 = insertBook("Reading 2", BookStatus.Reading, 2_000)
        insertBook("Finished", BookStatus.Finished, 3_000)
        insertOpenLoan(reading2)

        val counts = repository.observeCounts().first()

        assertEquals(LibraryCounts(all = 3, reading = 2, finished = 1, wantToRead = 0, lentOut = 1), counts)
    }

    @Test
    fun emptyDatabase_producesEmptyListsAndAllZeroCounts() = runTest {
        assertTrue(repository.observeBooks().first().isEmpty())
        assertTrue(repository.observeLentOutBooks().first().isEmpty())
        assertEquals(LibraryCounts(0, 0, 0, 0, 0), repository.observeCounts().first())
    }
}
