package com.minicore.bookshelf.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.minicore.bookshelf.core.database.converter.BookshelfConverters
import com.minicore.bookshelf.core.database.dao.BookDao
import com.minicore.bookshelf.core.database.dao.LoanDao
import com.minicore.bookshelf.core.database.entity.BookEntity
import com.minicore.bookshelf.core.database.entity.LoanEntity

@Database(
    entities = [BookEntity::class, LoanEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(BookshelfConverters::class)
abstract class BookshelfDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    abstract fun loanDao(): LoanDao
}
