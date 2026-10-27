package com.minicore.bookshelf.core.database.converter

import androidx.room.TypeConverter
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.CoverPreset
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

    /**
     * [CoverPreset] persists as the stable `TEXT` literals `preset_1`..`preset_6` (plan S2). Unlike
     * [stringToBookStatus], an unknown stored value returns `null` rather than throwing (plan S3):
     * a cover is cosmetic, so reads fall back to the id-derived preset instead of crashing.
     */
    @TypeConverter
    fun coverPresetToString(preset: CoverPreset?): String? = when (preset) {
        null -> null
        CoverPreset.Preset1 -> "preset_1"
        CoverPreset.Preset2 -> "preset_2"
        CoverPreset.Preset3 -> "preset_3"
        CoverPreset.Preset4 -> "preset_4"
        CoverPreset.Preset5 -> "preset_5"
        CoverPreset.Preset6 -> "preset_6"
    }

    @TypeConverter
    fun stringToCoverPreset(value: String?): CoverPreset? = when (value) {
        "preset_1" -> CoverPreset.Preset1
        "preset_2" -> CoverPreset.Preset2
        "preset_3" -> CoverPreset.Preset3
        "preset_4" -> CoverPreset.Preset4
        "preset_5" -> CoverPreset.Preset5
        "preset_6" -> CoverPreset.Preset6
        else -> null
    }
}
