package com.minicore.bookshelf.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookshelfDatabaseTest {

    private lateinit var database: BookshelfDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, BookshelfDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun opensAndClosesWithoutThrowing() {
        val writableDatabase = database.openHelper.writableDatabase
        assert(writableDatabase.isOpen)
    }
}
