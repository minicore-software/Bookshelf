package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.core.coverimage.CoverImageStore
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import java.time.Instant
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

private val BASE_CREATED_AT = Instant.parse("2025-01-01T00:00:00Z")

private fun baseBook(
    id: Long = 42L,
    title: String = "Dune",
    author: String = "Frank Herbert",
    cover: Cover = Cover.Preset(CoverPreset.Preset1),
    status: BookStatus = BookStatus.Finished,
    isbn: String? = "9780441013593",
    pageCount: Int? = 412,
    genre: String? = "Sci-fi",
    synopsis: String? = "A desert planet.",
) = Book(
    id = id,
    title = title,
    author = author,
    isbn = isbn,
    cover = cover,
    genre = genre,
    pageCount = pageCount,
    publishedYear = 1965,
    status = status,
    rating = 5,
    synopsis = synopsis,
    comment = "great",
    memoryWhy = "recommended",
    memoryWhere = "home",
    memoryWhat = "curious",
    createdAt = BASE_CREATED_AT,
)

private class UpdateBookFakeLibraryRepository(initial: Book?) : LibraryRepository {
    var row: Book? = initial
    var failUpdate = false
    var returnFalse = false
    var lastUpdated: Book? = null
    var updateCallCount = 0

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = MutableStateFlow(emptyList())
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = MutableStateFlow(emptyList())
    override fun observeLentOutBooks(): Flow<List<LentBook>> = MutableStateFlow(emptyList())
    override fun observeCounts(): Flow<LibraryCounts> = MutableStateFlow(LibraryCounts(0, 0, 0, 0, 0))
    override fun observeBook(id: Long): Flow<Book?> = MutableStateFlow(row)
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> = MutableStateFlow(null)
    override suspend fun addBook(book: Book): Long = throw NotImplementedError()

    override suspend fun updateBook(book: Book): Boolean {
        updateCallCount++
        if (failUpdate) throw IllegalStateException("update failed")
        lastUpdated = book
        if (returnFalse) return false
        row = book
        return true
    }
}

private class UpdateBookFakeCoverImageStore : CoverImageStore {
    val committed = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    val uncommitted = mutableListOf<Pair<String, String>>()
    var nextStoredRef = "covers/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg"

    override suspend fun importDraft(sourceUri: String): String = throw NotImplementedError()
    override suspend fun discardDraft(draftRef: String) = Unit

    override suspend fun commit(draftRef: String): String {
        committed += draftRef
        return nextStoredRef
    }

    override suspend fun uncommit(storedRef: String, draftRef: String) {
        uncommitted += storedRef to draftRef
    }

    override suspend fun delete(storedRef: String) {
        deleted += storedRef
    }

    override suspend fun exists(ref: String): Boolean = true
}

/** FF-E1 (plan #33 section 5): [UpdateBookUseCase]'s write-boundary and cover lifecycle rules. */
class UpdateBookUseCaseTest {

    private val coverImages = UpdateBookFakeCoverImageStore()

    // R1/F10: fields outside the form (status, isbn, pageCount, genre, synopsis, createdAt)
    // survive an edit of only the form's fields.
    @Test
    fun fieldsOutsideTheForm_surviveAnEdit() = runTest {
        val repository = UpdateBookFakeLibraryRepository(baseBook())
        val useCase = UpdateBookUseCase(repository, coverImages)

        val result = useCase(
            42L,
            BookDraft(title = "Dune Messiah", author = "Frank Herbert", rating = 4, cover = CoverDraft.Preset(CoverPreset.Preset1)),
        )

        assertEquals(UpdateBookResult.Updated, result)
        val saved = requireNotNull(repository.lastUpdated)
        assertEquals("Dune Messiah", saved.title)
        assertEquals(4, saved.rating)
        assertEquals(BookStatus.Finished, saved.status)
        assertEquals("9780441013593", saved.isbn)
        assertEquals(412, saved.pageCount)
        assertEquals("Sci-fi", saved.genre)
        assertEquals("A desert planet.", saved.synopsis)
        assertEquals(BASE_CREATED_AT, saved.createdAt)
        assertEquals(42L, saved.id)
    }

    // AC2/F4: each memory field is saved independently; whitespace-only -> null; others trimmed.
    @Test
    fun memoryFields_savedIndependently_blankBecomesNull_trimmed() = runTest {
        val repository = UpdateBookFakeLibraryRepository(baseBook())
        val useCase = UpdateBookUseCase(repository, coverImages)

        useCase(
            42L,
            BookDraft(
                title = "Dune",
                memoryWhy = "  Recommended by a friend  ",
                memoryWhere = "   ",
                memoryWhat = "",
            ),
        )

        val saved = requireNotNull(repository.lastUpdated)
        assertEquals("Recommended by a friend", saved.memoryWhy)
        assertNull(saved.memoryWhere)
        assertNull(saved.memoryWhat)
    }

    @Test
    fun blankComment_becomesNull_trimmedCommentIsKept() = runTest {
        val repository = UpdateBookFakeLibraryRepository(baseBook())
        val useCase = UpdateBookUseCase(repository, coverImages)

        useCase(42L, BookDraft(title = "Dune", comment = "  nice read  "))
        assertEquals("nice read", requireNotNull(repository.lastUpdated).comment)

        useCase(42L, BookDraft(title = "Dune", comment = "   "))
        assertNull(requireNotNull(repository.lastUpdated).comment)
    }

    @Test
    fun blankTitle_throws() = runTest {
        val repository = UpdateBookFakeLibraryRepository(baseBook())
        val useCase = UpdateBookUseCase(repository, coverImages)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase(42L, BookDraft(title = "   ")) }
        }
    }

    @Test
    fun yearOrRatingOutOfRange_throws() = runTest {
        val repository = UpdateBookFakeLibraryRepository(baseBook())
        val useCase = UpdateBookUseCase(repository, coverImages)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase(42L, BookDraft(title = "Dune", publishedYear = 0)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase(42L, BookDraft(title = "Dune", rating = 6)) }
        }
    }

    // E7 step 2: row vanished before save -> NotFound, no file touched.
    @Test
    fun rowGoneBeforeSave_returnsNotFound_touchesNoFile() = runTest {
        val repository = UpdateBookFakeLibraryRepository(null)
        val useCase = UpdateBookUseCase(repository, coverImages)

        val result = useCase(42L, BookDraft(title = "Dune", cover = CoverDraft.Image("cover-drafts/x.jpg")))

        assertEquals(UpdateBookResult.NotFound, result)
        assertTrue(coverImages.committed.isEmpty())
        assertEquals(0, repository.updateCallCount)
    }

    // E7 step 4: `updateBook` returning false (row vanished mid-save) -> NotFound + uncommit.
    @Test
    fun updateReturnsFalse_returnsNotFound_rollsBackCommittedCover() = runTest {
        val repository = UpdateBookFakeLibraryRepository(baseBook()).apply { returnFalse = true }
        val useCase = UpdateBookUseCase(repository, coverImages)

        val result = useCase(42L, BookDraft(title = "Dune", cover = CoverDraft.Image("cover-drafts/x.jpg")))

        assertEquals(UpdateBookResult.NotFound, result)
        assertEquals(listOf(coverImages.nextStoredRef to "cover-drafts/x.jpg"), coverImages.uncommitted)
        assertTrue(coverImages.deleted.isEmpty())
    }

    // F16: update throws -> uncommit + rethrow; old cover untouched.
    @Test
    fun updateThrows_rollsBackCommittedCover_rethrows_oldCoverUntouched() = runTest {
        val repository = UpdateBookFakeLibraryRepository(baseBook(cover = Cover.Image("covers/old.jpg"))).apply { failUpdate = true }
        val useCase = UpdateBookUseCase(repository, coverImages)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { useCase(42L, BookDraft(title = "Dune", cover = CoverDraft.Image("cover-drafts/x.jpg"))) }
        }

        assertEquals(listOf(coverImages.nextStoredRef to "cover-drafts/x.jpg"), coverImages.uncommitted)
        assertTrue(coverImages.deleted.isEmpty())
    }

    // F15: old stored file deleted only after a successful save when the cover actually changed.
    @Test
    fun successfulSave_deletesOldStoredCover_onlyWhenCoverChanged() = runTest {
        val repository = UpdateBookFakeLibraryRepository(baseBook(cover = Cover.Image("covers/old.jpg")))
        val useCase = UpdateBookUseCase(repository, coverImages)

        val result = useCase(42L, BookDraft(title = "Dune", cover = CoverDraft.Image("cover-drafts/new.jpg")))

        assertEquals(UpdateBookResult.Updated, result)
        assertEquals(listOf("covers/old.jpg"), coverImages.deleted)
        assertEquals(Cover.Image(coverImages.nextStoredRef), requireNotNull(repository.lastUpdated).cover)
    }

    // E5: Stored, unchanged -> no commit/uncommit/delete file op at all.
    @Test
    fun storedCoverUnchanged_noFileOp() = runTest {
        val repository = UpdateBookFakeLibraryRepository(baseBook(cover = Cover.Image("covers/keep.jpg")))
        val useCase = UpdateBookUseCase(repository, coverImages)

        val result = useCase(42L, BookDraft(title = "Dune", cover = CoverDraft.Stored("covers/keep.jpg")))

        assertEquals(UpdateBookResult.Updated, result)
        assertTrue(coverImages.committed.isEmpty())
        assertTrue(coverImages.deleted.isEmpty())
        assertTrue(coverImages.uncommitted.isEmpty())
        assertEquals(Cover.Image("covers/keep.jpg"), requireNotNull(repository.lastUpdated).cover)
    }

    // E5: a Stored ref that disagrees with the current row's cover is a programming error.
    @Test
    fun storedCoverMismatchingCurrentRow_throws() = runTest {
        val repository = UpdateBookFakeLibraryRepository(baseBook(cover = Cover.Image("covers/keep.jpg")))
        val useCase = UpdateBookUseCase(repository, coverImages)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { useCase(42L, BookDraft(title = "Dune", cover = CoverDraft.Stored("covers/other.jpg"))) }
        }
    }

    // Switching from a preset to a preset deletes nothing (nothing was ever an image).
    @Test
    fun presetToPreset_deletesNothing() = runTest {
        val repository = UpdateBookFakeLibraryRepository(baseBook(cover = Cover.Preset(CoverPreset.Preset1)))
        val useCase = UpdateBookUseCase(repository, coverImages)

        useCase(42L, BookDraft(title = "Dune", cover = CoverDraft.Preset(CoverPreset.Preset3)))

        assertFalse(coverImages.deleted.isNotEmpty())
    }
}
