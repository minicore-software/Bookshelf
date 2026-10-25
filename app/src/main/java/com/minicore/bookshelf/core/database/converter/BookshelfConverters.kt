package com.minicore.bookshelf.core.database.converter

import androidx.room.TypeConverter
import com.minicore.bookshelf.core.model.BookStatus
import java.time.Instant
import java.time.LocalDate

/**
 * Room type converters for [BookshelfDatabase] (plan section 3, ADR-001 S3/S4).
 *
 * - [Instant] persists as epoch milliseconds.
 * - [LocalDate] persists as epoch day (**not** millis) so calendar-day fields such as
 *   `loans.due_at` never shift across a time zone change.
 * - [BookStatus] persists as the explicit stable `TEXT` literals below, never `enum.name` or
 *   ordinal, so renaming or reordering the Kotlin enum cannot corrupt stored rows. An unknown
 *   stored value throws rather than silently mapping to a default.
 */
class BookshelfConverters {

    @TypeConverter
    fun instantToEpochMillis(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(epochMillis: Long?): Instant? = epochMillis?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun bookStatusToString(status: BookStatus): String = when (status) {
        BookStatus.WantToRead -> "want_to_read"
        BookStatus.Reading -> "reading"
        BookStatus.Finished -> "finished"
    }

    @TypeConverter
    fun stringToBookStatus(value: String): BookStatus = when (value) {
        "want_to_read" -> BookStatus.WantToRead
        "reading" -> BookStatus.Reading
        "finished" -> BookStatus.Finished
        else -> throw IllegalArgumentException("Unknown BookStatus value: $value")
    }
}
