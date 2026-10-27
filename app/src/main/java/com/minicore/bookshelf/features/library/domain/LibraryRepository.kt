package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import kotlinx.coroutines.flow.Flow

/**
 * Read-only contract for #20 and #21 (plan section 5, epic #17 decision D9). All flows are cold,
 * backed by Room, re-emit on table changes and never emit `null`: an empty library gives
 * `emptyList()` and `LibraryCounts(0, 0, 0, 0, 0)` (#22's empty state relies on this). Overdue is
 * not computed here - it is a presentation concern derived from [com.minicore.bookshelf.features.library.domain.model.OpenLoan.dueAt]
 * against the current date, because a repository-computed flag would go stale at midnight.
 */
interface LibraryRepository {

    /** `null` = the "All" chip. */
    fun observeBooks(status: BookStatus? = null): Flow<List<LibraryBook>>

    fun observeCurrentlyReading(): Flow<List<LibraryBook>>

    /** "Lent out" row and chip. */
    fun observeLentOutBooks(): Flow<List<LentBook>>

    /** Chip badges. */
    fun observeCounts(): Flow<LibraryCounts>

    /** Cold, re-emits on `books` changes for this [id]. Emits `null` when the id does not exist. */
    fun observeBook(id: Long): Flow<Book?>

    /** Requires `book.id == 0L`. Inserts with `ABORT` and returns the new id (plan section 4 S8). */
    suspend fun addBook(book: Book): Long

    /** `@Update` by id. `false` when the id does not exist. Never touches `loans` (plan S8). */
    suspend fun updateBook(book: Book): Boolean
}
