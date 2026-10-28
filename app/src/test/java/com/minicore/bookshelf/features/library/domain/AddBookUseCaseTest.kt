package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.core.coverimage.CoverImageStore
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class AddBookFakeLibraryRepository : LibraryRepository {
    var nextId = 7L
    var failInsert = false
    var lastAdded: Book? = null

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = MutableStateFlow(emptyList())
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = MutableStateFlow(emptyList())
    override fun observeLentOutBooks(): Flow<List<LentBook>> = MutableStateFlow(emptyList())
    override fun observeCounts(): Flow<LibraryCounts> = MutableStateFlow(LibraryCounts(0, 0, 0, 0, 0))
    override fun observeBook(id: Long): Flow<Book?> = MutableStateFlow(null)
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> = MutableStateFlow(null)

    override suspend fun addBook(book: Book): Long {
        if (failInsert) throw IllegalStateException("insert failed")
        lastAdded = book
        return nextId
    }

    override suspend fun updateBook(book: Book): Boolean = throw NotImplementedError()
    override suspend fun removeBook(id: Long): Boolean = throw NotImplementedError()
}

/**
 * Models the same move semantics as [com.minicore.bookshelf.core.coverimage.FileCoverImageStore]
 * (F19): [commit] "moves" a draft to a stored ref, and a draft ref only [exists] again once
 * [uncommit] moves it back - so a test can assert a retry after rollback actually succeeds.
 */
private class AddBookFakeCoverImageStore : CoverImageStore {
    val committed = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    val uncommitted = mutableListOf<Pair<String, String>>()
    private val draftsOnDisk = mutableSetOf<String>()
    private val storedOnDisk = mutableSetOf<String>()
    var nextStoredRef = "covers/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg"

    override suspend fun importDraft(sourceUri: String): String = throw NotImplementedError()

    override suspend fun discardDraft(draftRef: String) {
        draftsOnDisk.remove(draftRef)
    }

    override suspend fun commit(draftRef: String): String {
        committed += draftRef
        draftsOnDisk.remove(draftRef)
        storedOnDisk += nextStoredRef
        return nextStoredRef
    }

    override suspend fun uncommit(storedRef: String, draftRef: String) {
        uncommitted += storedRef to draftRef
        storedOnDisk.remove(storedRef)
        draftsOnDisk += draftRef
    }

    override suspend fun delete(storedRef: String) {
        deleted += storedRef
        storedOnDisk.remove(storedRef)
    }

    override suspend fun exists(ref: String): Boolean = ref in draftsOnDisk || ref in storedOnDisk
}

/** FF-A1 (plan #31 section 6): [AddBookUseCase]'s write-boundary rules (decision A6). */
class AddBookUseCaseTest {

    private val repository = AddBookFakeLibraryRepository()
    private val coverImages = AddBookFakeCoverImageStore()
    private val clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC)
    private val useCase = AddBookUseCase(repository, coverImages, clock)

    // F4, F5: a minimal draft persists with the documented defaults, always want_to_read.
    @Test
    fun minimalDraft_persistsWithDefaults() = runTest {
        val id = useCase(BookDraft(title = "Dune"))

        assertEquals(7L, id)
        val saved = requireNotNull(repository.lastAdded)
        assertEquals("Dune", saved.title)
        assertEquals("", saved.author)
        assertNull(saved.publishedYear)
        assertNull(saved.rating)
        assertNull(saved.comment)
        assertEquals(Cover.Preset(CoverPreset.Preset1), saved.cover)
        assertEquals(BookStatus.WantToRead, saved.status)
        assertEquals(Instant.parse("2026-10-27T12:00:00Z"), saved.createdAt)
        assertNull(saved.isbn)
        assertNull(saved.genre)
        assertNull(saved.pageCount)
        assertNull(saved.synopsis)
    }

    // F29, F30, R10: title and author are trimmed; a blank comment becomes null.
    @Test
    fun titleAndAuthorAreTrimmed_blankCommentBecomesNull() = runTest {
        useCase(BookDraft(title = "  Dune  ", author = "  Frank Herbert  ", comment = "   "))

        val saved = requireNotNull(repository.lastAdded)
        assertEquals("Dune", saved.title)
        assertEquals("Frank Herbert", saved.author)
        assertNull(saved.comment)
    }

    // F1/F2 at the domain level: a blank or whitespace-only title is a programming error.
    @Test
    fun blankTitle_throws() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase(BookDraft(title = "")) }
        }
    }

    @Test
    fun whitespaceOnlyTitle_throws() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase(BookDraft(title = "   ")) }
        }
    }

    @Test
    fun yearOutOfRange_throws() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase(BookDraft(title = "Dune", publishedYear = 0)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase(BookDraft(title = "Dune", publishedYear = 10_000)) }
        }
    }

    @Test
    fun ratingOutOfRange_throws() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase(BookDraft(title = "Dune", rating = 0)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase(BookDraft(title = "Dune", rating = 6)) }
        }
    }

    // AC4, F12: an image draft is committed before the insert, and the stored ref is what's persisted.
    @Test
    fun imageCoverDraft_isCommittedBeforeInsert() = runTest {
        useCase(BookDraft(title = "Dune", cover = CoverDraft.Image("cover-drafts/bbbb.jpg")))

        assertEquals(listOf("cover-drafts/bbbb.jpg"), coverImages.committed)
        val saved = requireNotNull(repository.lastAdded)
        assertEquals(Cover.Image(coverImages.nextStoredRef), saved.cover)
    }

    // F19: an insert failure after a cover commit un-commits the file back to its original draft
    // ref (not delete - a bare delete would strand the committed file and break retry), and
    // rethrows. A retry referencing the same draft ref then succeeds.
    @Test
    fun insertFailureAfterCoverCommit_rollsBackTheCommittedFile_andRetrySucceeds() = runTest {
        repository.failInsert = true

        assertThrows(IllegalStateException::class.java) {
            runBlocking { useCase(BookDraft(title = "Dune", cover = CoverDraft.Image("cover-drafts/bbbb.jpg"))) }
        }

        assertEquals(listOf(coverImages.nextStoredRef to "cover-drafts/bbbb.jpg"), coverImages.uncommitted)
        assertTrue(coverImages.deleted.isEmpty())
        assertTrue(coverImages.exists("cover-drafts/bbbb.jpg"))

        repository.failInsert = false
        val id = useCase(BookDraft(title = "Dune", cover = CoverDraft.Image("cover-drafts/bbbb.jpg")))

        assertEquals(7L, id)
        assertEquals(Cover.Image(coverImages.nextStoredRef), requireNotNull(repository.lastAdded).cover)
    }

    // No rollback should ever happen for a preset cover (nothing was committed).
    @Test
    fun insertFailureWithPresetCover_deletesNothing() = runTest {
        repository.failInsert = true

        assertThrows(IllegalStateException::class.java) {
            runBlocking { useCase(BookDraft(title = "Dune")) }
        }

        assertTrue(coverImages.deleted.isEmpty())
        assertTrue(coverImages.uncommitted.isEmpty())
        assertFalse(coverImages.committed.isNotEmpty())
    }
}
