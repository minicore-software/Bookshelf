package com.minicore.bookshelf.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Persisted shape of a book loan (plan section 3). `returnedAt == null` means the loan is open.
 * At most one open loan per book is enforced by the database triggers created in
 * [com.minicore.bookshelf.core.database.OpenLoanInvariantCallback] (ADR-001 S5), not by this
 * entity.
 *
 * `dueAt` is a [LocalDate] (epoch day), never [Instant] - a calendar day must not shift across a
 * time zone change (ADR-001 S3). Any SQL comparison against `due_at` must bind a [LocalDate].
 */
@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["book_id", "returned_at"], name = "index_loans_book_id_returned_at"),
    ],
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "book_id")
    val bookId: Long,
    @ColumnInfo(name = "borrower_name")
    val borrowerName: String,
    @ColumnInfo(name = "borrower_contact")
    val borrowerContact: String?,
    @ColumnInfo(name = "loaned_at")
    val loanedAt: Instant,
    @ColumnInfo(name = "due_at")
    val dueAt: LocalDate,
    @ColumnInfo(name = "returned_at")
    val returnedAt: Instant?,
    @ColumnInfo(name = "remind_enabled")
    val remindEnabled: Boolean,
    val notes: String?,
)
