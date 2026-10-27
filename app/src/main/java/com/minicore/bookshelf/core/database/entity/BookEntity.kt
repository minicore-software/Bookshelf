package com.minicore.bookshelf.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.CoverPreset
import java.time.Instant

/**
 * Persisted shape of [com.minicore.bookshelf.core.model.Book] (plan section 3/4). Non-blank
 * `title`/`author` and the `rating` 1..5 range are enforced at the write boundary, not in SQL
 * (Add Book epic). `cover_uri` and `cover_color` are mutually exclusive, enforced by
 * [com.minicore.bookshelf.core.database.BookCoverInvariantCallback]'s triggers, not by this class
 * (ADR-002 S1) - the sealed [com.minicore.bookshelf.core.model.Cover] rules it out for the
 * repository, but a direct DAO write could still violate it, hence the triggers.
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
    val comment: String? = null,
    @ColumnInfo(name = "memory_why")
    val memoryWhy: String? = null,
    @ColumnInfo(name = "memory_where")
    val memoryWhere: String? = null,
    @ColumnInfo(name = "memory_what")
    val memoryWhat: String? = null,
    @ColumnInfo(name = "cover_color")
    val coverColor: CoverPreset? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
)
