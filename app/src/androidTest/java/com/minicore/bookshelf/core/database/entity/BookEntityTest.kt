package com.minicore.bookshelf.core.database.entity

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minicore.bookshelf.core.database.BookshelfDatabase
import com.minicore.bookshelf.core.database.applyBookshelfConfig
import com.minicore.bookshelf.core.model.BookStatus
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AC1 (QA A1/A2): [BookEntity] round-trips every field, nullable fields included, and `status`
 * is stored as the stable string (not `enum.name`/ordinal - ADR-001 S4).
 */
@RunWith(AndroidJUnit4::class)
class BookEntityTest {

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
    fun bookWithAllNullableFieldsNull_roundTrips() = runTest {
        val book = BookEntity(
            title = "Pale Fire",
            author = "Vladimir Nabokov",
            isbn = null,
            coverUri = null,
            genre = null,
            pageCount = null,
            publishedYear = null,
            status = BookStatus.WantToRead,
            rating = null,
            synopsis = null,
            createdAt = Instant.ofEpochMilli(1_000_000),
        )

        val id = database.bookDao().insert(book)

        database.openHelper.readableDatabase.query("SELECT status FROM books WHERE id = $id").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("want_to_read", cursor.getString(0))
        }

        val restored = database.bookDao().observeLibraryBooks(null).first().single().book
        assertEquals(book.copy(id = id), restored)
    }

    @Test
    fun bookWithAllFieldsSet_roundTrips() = runTest {
        val book = BookEntity(
            title = "It",
            author = "Stephen King",
            isbn = "0X443X", // leading-zero + ISBN-10 check-digit style value, kept as string
            coverUri = "content://covers/it.jpg",
            genre = "Horror",
            pageCount = 1138,
            publishedYear = 1986,
            status = BookStatus.Reading,
            rating = 5,
            synopsis = "A shapeshifting evil terrorizes Derry.",
            createdAt = Instant.ofEpochMilli(2_000_000),
        )

        val id = database.bookDao().insert(book)

        val restored = database.bookDao().observeLibraryBooks(null).first().single().book

        assertEquals(book.copy(id = id), restored)
        assertEquals("0X443X", restored.isbn)
    }
}
