package com.minicore.bookshelf.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minicore.bookshelf.core.database.entity.LoanEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * [insert] relies on the database triggers (`OpenLoanInvariantCallback`) to abort a second open
 * loan for the same book (ADR-001 S5) - it never uses [OnConflictStrategy.REPLACE] (S10/R5).
 */
@Dao
interface LoanDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(loan: LoanEntity): Long

    @Query("UPDATE loans SET returned_at = :returnedAt WHERE id = :loanId AND returned_at IS NULL")
    suspend fun markReturned(loanId: Long, returnedAt: Instant): Int

    @Query("SELECT * FROM loans WHERE book_id = :bookId AND returned_at IS NULL")
    suspend fun findOpenLoan(bookId: Long): LoanEntity?

    @Query("SELECT COUNT(DISTINCT book_id) FROM loans WHERE returned_at IS NULL")
    fun observeOpenLoanCount(): Flow<Int>
}
