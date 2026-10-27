package com.minicore.bookshelf.features.library.data

import com.minicore.bookshelf.core.database.dao.LibraryBookRow
import com.minicore.bookshelf.core.database.dao.StatusCountRow
import com.minicore.bookshelf.core.database.entity.BookEntity
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan

/**
 * Entity -> domain model mapping. Entities never leave `data` (plan section 5). [Cover]
 * resolution (plan section 4): an image wins if both are somehow set (the triggers prevent that
 * state in practice), a stored preset is used when there is no image, and neither set (including
 * an unknown/corrupt stored key, plan S3/R5) falls back to the id-derived preset.
 */
internal fun BookEntity.toDomain(): Book = Book(
    id = id,
    title = title,
    author = author,
    isbn = isbn,
    cover = toCover(),
    genre = genre,
    pageCount = pageCount,
    publishedYear = publishedYear,
    status = status,
    rating = rating,
    synopsis = synopsis,
    comment = comment,
    memoryWhy = memoryWhy,
    memoryWhere = memoryWhere,
    memoryWhat = memoryWhat,
    createdAt = createdAt,
)

private fun BookEntity.toCover(): Cover = when {
    coverUri != null -> Cover.Image(coverUri)
    coverColor != null -> Cover.Preset(coverColor)
    else -> Cover.Preset(CoverPreset.forBookId(id))
}

/** Domain -> entity mapping (plan section 4 S7): blank optional text becomes `null`. */
internal fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    title = title,
    author = author,
    isbn = isbn,
    coverUri = (cover as? Cover.Image)?.uri,
    coverColor = (cover as? Cover.Preset)?.preset,
    genre = genre,
    pageCount = pageCount,
    publishedYear = publishedYear,
    status = status,
    rating = rating,
    synopsis = synopsis,
    comment = comment.blankToNull(),
    memoryWhy = memoryWhy.blankToNull(),
    memoryWhere = memoryWhere.blankToNull(),
    memoryWhat = memoryWhat.blankToNull(),
    createdAt = createdAt,
)

private fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }

internal fun LibraryBookRow.toOpenLoan(): OpenLoan? {
    val loanId = openLoanId ?: return null
    val borrowerName = openLoanBorrowerName ?: return null
    val loanedAt = openLoanLoanedAt ?: return null
    val dueAt = openLoanDueAt ?: return null
    return OpenLoan(loanId = loanId, borrowerName = borrowerName, loanedAt = loanedAt, dueAt = dueAt)
}

internal fun LibraryBookRow.toLibraryBook(): LibraryBook = LibraryBook(
    book = book.toDomain(),
    openLoan = toOpenLoan(),
)

internal fun LibraryBookRow.toLentBook(): LentBook {
    val loan = requireNotNull(toOpenLoan()) {
        "LentBook requires an open loan; row for book ${book.id} has none"
    }
    return LentBook(book = book.toDomain(), loan = loan)
}

/**
 * Assembles [LibraryCounts] from the per-status rows and the independent open-loan count
 * (ADR-001 S8). A status missing from [statusCounts] contributes 0, not a missing/null value
 * (QA A9).
 */
internal fun buildLibraryCounts(statusCounts: List<StatusCountRow>, lentOut: Int): LibraryCounts {
    val countsByStatus = statusCounts.associate { it.status to it.count }
    val reading = countsByStatus[BookStatus.Reading] ?: 0
    val finished = countsByStatus[BookStatus.Finished] ?: 0
    val wantToRead = countsByStatus[BookStatus.WantToRead] ?: 0
    return LibraryCounts(
        all = reading + finished + wantToRead,
        reading = reading,
        finished = finished,
        wantToRead = wantToRead,
        lentOut = lentOut,
    )
}
