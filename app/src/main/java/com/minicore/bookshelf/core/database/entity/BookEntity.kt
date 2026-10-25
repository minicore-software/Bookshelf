package com.minicore.bookshelf.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.minicore.bookshelf.core.model.BookStatus
import java.time.Instant

/**
 * Persisted shape of [com.minicore.bookshelf.core.model.Book] (plan section 3). Non-blank
 * `title`/`author` and the `rating` 1..5 range are enforced at the write boundary, not in SQL
 * (Add Book epic) - this ticket is read-only.
 */
@Entity(
    tableName = "books",
    indices = [Index(value = ["status"], name = "index_books_status")],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String,
    val isbn: String?,
    @ColumnInfo(name = "cover_uri")
    val coverUri: String?,
    val genre: String?,
    @ColumnInfo(name = "page_count")
    val pageCount: Int?,
    @ColumnInfo(name = "published_year")
    val publishedYear: Int?,
    val status: BookStatus,
    val rating: Int?,
    val synopsis: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
)
