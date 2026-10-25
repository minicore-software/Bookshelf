package com.minicore.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minicore.bookshelf.core.database.entity.BookEntity
import com.minicore.bookshelf.core.model.BookStatus
import kotlinx.coroutines.flow.Flow

/**
 * Never uses [OnConflictStrategy.REPLACE] (ADR-001 S10/R5): a replace on `books` deletes and
 * re-inserts the row, which cascades through `loans.book_id -> books.id ON DELETE CASCADE` and
 * wipes that book's loan history.
 */
@Dao
interface BookDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity): Long

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query(
        """
        SELECT b.*, l.id AS open_loan_id, l.borrower_name AS open_loan_borrower_name,
               l.loaned_at AS open_loan_loaned_at, l.due_at AS open_loan_due_at
        FROM books AS b
        LEFT JOIN loans AS l ON l.book_id = b.id AND l.returned_at IS NULL
        WHERE (:status IS NULL OR b.status = :status)
        ORDER BY b.created_at DESC, b.id DESC
        """
    )
    fun observeLibraryBooks(status: BookStatus?): Flow<List<LibraryBookRow>>

    @Query(
        """
        SELECT b.*, l.id AS open_loan_id, l.borrower_name AS open_loan_borrower_name,
               l.loaned_at AS open_loan_loaned_at, l.due_at AS open_loan_due_at
        FROM books AS b
        INNER JOIN loans AS l ON l.book_id = b.id AND l.returned_at IS NULL
        ORDER BY l.due_at ASC, b.id ASC
        """
    )
    fun observeLentOutBooks(): Flow<List<LibraryBookRow>>

    @Query("SELECT status, COUNT(*) AS count FROM books GROUP BY status")
    fun observeStatusCounts(): Flow<List<StatusCountRow>>
}
