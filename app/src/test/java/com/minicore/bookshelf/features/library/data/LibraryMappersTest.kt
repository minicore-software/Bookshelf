package com.minicore.bookshelf.features.library.data

import com.minicore.bookshelf.core.database.dao.StatusCountRow
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
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
}
