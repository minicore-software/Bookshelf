package com.minicore.bookshelf.core.model

import java.time.Instant

/**
 * A book in the user's library (plan section 5). Identity is [id]-based, never by [title]
 * (CLAUDE.md domain model) - titles can change.
 */
data class Book(
    val id: Long,
    val title: String,
    val author: String,
    val isbn: String?,
    val cover: Cover,
    val genre: String?,
    val pageCount: Int?,
    val publishedYear: Int?,
    val status: BookStatus,
    val rating: Int?,
    val synopsis: String?,
    val comment: String? = null,
    val memoryWhy: String? = null,
    val memoryWhere: String? = null,
    val memoryWhat: String? = null,
    val createdAt: Instant,
)
