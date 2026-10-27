package com.minicore.bookshelf.features.library.data

import com.minicore.bookshelf.core.database.dao.StatusCountRow
import com.minicore.bookshelf.core.database.entity.BookEntity
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A9 (QA): [buildLibraryCounts] assembles [LibraryCounts] from [StatusCountRow]s missing one or
 * more statuses, and `lentOut` comes from its own count independent of the status rows.
 */
class LibraryMappersTest {

    @Test
    fun missingStatuses_mapToZero() {
        val statusCounts = listOf(StatusCountRow(status = BookStatus.Reading, count = 3))

        val counts = buildLibraryCounts(statusCounts, lentOut = 0)

        assertEquals(
            LibraryCounts(all = 3, reading = 3, finished = 0, wantToRead = 0, lentOut = 0),
            counts,
        )
    }

    @Test
    fun allStatusesMissing_mapToAllZero() {
        val counts = buildLibraryCounts(emptyList(), lentOut = 0)

        assertEquals(LibraryCounts(0, 0, 0, 0, 0), counts)
    }

    @Test
    fun allPresent_sumsToAll() {
        val statusCounts = listOf(
            StatusCountRow(status = BookStatus.Reading, count = 2),
            StatusCountRow(status = BookStatus.Finished, count = 5),
            StatusCountRow(status = BookStatus.WantToRead, count = 1),
        )

        val counts = buildLibraryCounts(statusCounts, lentOut = 4)

        assertEquals(
            LibraryCounts(all = 8, reading = 2, finished = 5, wantToRead = 1, lentOut = 4),
            counts,
        )
    }

    @Test
    fun lentOut_isIndependentOfStatusRows() {
        val statusCounts = listOf(StatusCountRow(status = BookStatus.Finished, count = 1))

        val counts = buildLibraryCounts(statusCounts, lentOut = 9)

        assertEquals(9, counts.lentOut)
        assertEquals(1, counts.all)
    }

    private fun entity(
        id: Long = 1,
        coverUri: String? = null,
        coverColor: CoverPreset? = null,
    ) = BookEntity(
        id = id,
        title = "Title",
        author = "Author",
        isbn = null,
        coverUri = coverUri,
        genre = null,
        pageCount = null,
        publishedYear = null,
        status = BookStatus.Reading,
        rating = null,
        synopsis = null,
        coverColor = coverColor,
        createdAt = Instant.EPOCH,
    )

    // F12: toDomain, case 1 - an image wins when both are somehow set.
    @Test
    fun toDomain_imageWinsOverColor_whenBothSomehowSet() {
        val book = entity(coverUri = "content://covers/a.jpg", coverColor = CoverPreset.Preset3).toDomain()

        assertEquals(Cover.Image("content://covers/a.jpg"), book.cover)
    }

    // F12: toDomain, case 2 - image only.
    @Test
    fun toDomain_resolvesImage_whenOnlyUriSet() {
        val book = entity(coverUri = "content://covers/b.jpg").toDomain()

        assertEquals(Cover.Image("content://covers/b.jpg"), book.cover)
    }

    // F12: toDomain, case 3 - stored preset only.
    @Test
    fun toDomain_resolvesStoredPreset_whenOnlyColorSet() {
        val book = entity(coverColor = CoverPreset.Preset5).toDomain()

        assertEquals(Cover.Preset(CoverPreset.Preset5), book.cover)
    }

    // F12: toDomain, case 4 - neither set (including an unknown/corrupt key, mapped upstream by
    // the converter) falls back to the id-derived preset (plan S3/R5).
    @Test
    fun toDomain_fallsBackToIdDerivedPreset_whenNeitherSet() {
        val book = entity(id = 13).toDomain()

        assertEquals(Cover.Preset(CoverPreset.forBookId(13)), book.cover)
    }

    private fun domainBook(
        cover: Cover = Cover.Preset(CoverPreset.Preset1),
        comment: String? = null,
        memoryWhy: String? = null,
        memoryWhere: String? = null,
        memoryWhat: String? = null,
    ) = Book(
        id = 1,
        title = "Title",
        author = "Author",
        isbn = null,
        cover = cover,
        genre = null,
        pageCount = null,
        publishedYear = null,
        status = BookStatus.Reading,
        rating = null,
        synopsis = null,
        comment = comment,
        memoryWhy = memoryWhy,
        memoryWhere = memoryWhere,
        memoryWhat = memoryWhat,
        createdAt = Instant.EPOCH,
    )

    // F13: toEntity, blank -> null for each of the 4 text fields (plan S7).
    @Test
    fun toEntity_blanksBecomeNull_forAllFourTextFields() {
        val entity = domainBook(comment = "  ", memoryWhy = "", memoryWhere = "\t", memoryWhat = " ").toEntity()

        assertEquals(null, entity.comment)
        assertEquals(null, entity.memoryWhy)
        assertEquals(null, entity.memoryWhere)
        assertEquals(null, entity.memoryWhat)
    }

    @Test
    fun toEntity_preservesNonBlankText() {
        val entity = domainBook(comment = "Loved it").toEntity()

        assertEquals("Loved it", entity.comment)
    }

    // F13: toEntity, Image/Preset column split.
    @Test
    fun toEntity_splitsImageIntoUriColumn_colorNull() {
        val entity = domainBook(cover = Cover.Image("content://covers/c.jpg")).toEntity()

        assertEquals("content://covers/c.jpg", entity.coverUri)
        assertEquals(null, entity.coverColor)
    }

    @Test
    fun toEntity_splitsPresetIntoColorColumn_uriNull() {
        val entity = domainBook(cover = Cover.Preset(CoverPreset.Preset4)).toEntity()

        assertEquals(null, entity.coverUri)
        assertEquals(CoverPreset.Preset4, entity.coverColor)
    }
}
