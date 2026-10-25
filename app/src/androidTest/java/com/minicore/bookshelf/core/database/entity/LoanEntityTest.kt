package com.minicore.bookshelf.core.database.entity

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minicore.bookshelf.core.database.BookshelfDatabase
import com.minicore.bookshelf.core.database.applyBookshelfConfig
import com.minicore.bookshelf.core.model.BookStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AC1 (QA A3): [LoanEntity] round-trips every field with `dueAt` on a DST-transition date and
 * `returnedAt` null, and the restored [LocalDate] compares equal (no day shift - ADR-001 S3).
 */
@RunWith(AndroidJUnit4::class)
class LoanEntityTest {

    private lateinit var database: BookshelfDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java)
            .applyBookshelfConfig()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun loanWithFullFields_dueAtOnDstTransitionDate_roundTrips() = runTest {
        val bookId = database.bookDao().insert(
            BookEntity(
                title = "Dracula",
                author = "Bram Stoker",
                isbn = null,
                coverUri = null,
                genre = null,
                pageCount = null,
                publishedYear = null,
                status = BookStatus.Reading,
                rating = null,
                synopsis = null,
                createdAt = Instant.ofEpochMilli(500_000),
            )
        )

        // 2026-03-08: US DST start (spring forward) - a day a local-midnight Instant would shift.
        val dstTransitionDate = LocalDate.of(2026, 3, 8)
        val loan = LoanEntity(
            bookId = bookId,
            borrowerName = "Alex Rivera",
            borrowerContact = "alex@example.com",
            loanedAt = Instant.ofEpochMilli(600_000),
            dueAt = dstTransitionDate,
            returnedAt = null,
            remindEnabled = true,
            notes = "Hardback, handle with care",
        )

        val loanId = database.loanDao().insert(loan)

        val restored = database.loanDao().findOpenLoan(bookId)
        assertNotNull(restored)
        assertEquals(loan.copy(id = loanId), restored)
        assertEquals(dstTransitionDate, restored!!.dueAt)
        assertEquals(dstTransitionDate.toEpochDay(), restored.dueAt.toEpochDay())
    }
}
