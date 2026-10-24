package com.minicore.bookshelf.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.minicore.bookshelf.core.database.entity.PlaceholderEntity

@Database(
    entities = [PlaceholderEntity::class],
    version = 1,
    exportSchema = true
)
abstract class BookshelfDatabase : RoomDatabase()
