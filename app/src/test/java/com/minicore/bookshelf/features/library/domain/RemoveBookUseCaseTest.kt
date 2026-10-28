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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private fun baseBook(id: Long = 42L, cover: Cover = Cover.Preset(CoverPreset.Preset1)) = Book(
    id = id,
    title = "Dune",
    author = "Frank Herbert",
    isbn = "9780441013593",
    cover = cover,
    genre = "Sci-fi",
    pageCount = 412,
    publishedYear = 1965,
    status = BookStatus.Finished,
    rating = 5,
    synopsis = "A desert planet.",
    createdAt = Instant.parse("2025-01-01T00:00:00Z"),
)

private class RemoveBookFakeLibraryRepository(initial: Book?) : LibraryRepository {
    var row: Book? = initial
    var removeCallCount = 0
    var removeReturnsFalse = false
    var removeThrows = false

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeLentOutBooks(): Flow<List<LentBook>> = throw NotImplementedError()
    override fun observeCounts(): Flow<LibraryCounts> = throw NotImplementedError()
    override fun observeBook(id: Long): Flow<Book?> = MutableStateFlow(row)
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> = throw NotImplementedError()
    override suspend fun addBook(book: Book): Long = throw NotImplementedError()
    override suspend fun updateBook(book: Book): Boolean = throw NotImplementedError()

    override suspend fun removeBook(id: Long): Boolean {
        removeCallCount++
        if (removeThrows) throw IllegalStateException("delete failed")
        if (removeReturnsFalse) return false
        row = null
        return true
    }
}

private class RemoveBookFakeCoverImageStore : CoverImageStore {
    val deleted = mutableListOf<String>()

    override suspend fun importDraft(sourceUri: String): String = throw NotImplementedError()
    override suspend fun discardDraft(draftRef: String) = Unit
    override suspend fun commit(draftRef: String): String = throw NotImplementedError()
    override suspend fun uncommit(storedRef: String, draftRef: String) = Unit
    override suspend fun delete(storedRef: String) {
        deleted += storedRef
    }
    override suspend fun exists(ref: String): Boolean = true
}

/** FF-R2 (plan #34 section 5): [RemoveBookUseCase]'s read-delete-cleanup ordering (D6, D7, D8,
 * D9, D10). */
class RemoveBookUseCaseTest {

    private val coverImages = RemoveBookFakeCoverImageStore()

    // D6/D9: a successful removal of a book with an image cover deletes the stored file after
    // the row delete.
    @Test
    fun removingABookWithAnImageCover_deletesTheStoredFile_afterTheRowDelete() = runTest {
        val repository = RemoveBookFakeLibraryRepository(baseBook(cover = Cover.Image("covers/old.jpg")))
        val useCase = RemoveBookUseCase(repository, coverImages)

        val result = useCase(42L)

        assertEquals(RemoveBookResult.Removed, result)
        assertEquals(listOf("covers/old.jpg"), coverImages.deleted)
        assertEquals(1, repository.removeCallCount)
    }

    // D10: a Cover.Preset is never passed to delete.
    @Test
    fun removingABookWithAPresetCover_neverCallsDelete() = runTest {
        val repository = RemoveBookFakeLibraryRepository(baseBook(cover = Cover.Preset(CoverPreset.Preset3)))
        val useCase = RemoveBookUseCase(repository, coverImages)

        val result = useCase(42L)

        assertEquals(RemoveBookResult.Removed, result)
        assertTrue(coverImages.deleted.isEmpty())
    }

    // D7: the row does not exist before the delete -> NotFound, no delete call, no file touched.
    @Test
    fun rowMissingBeforeDelete_returnsNotFound_touchesNoFile() = runTest {
        val repository = RemoveBookFakeLibraryRepository(null)
        val useCase = RemoveBookUseCase(repository, coverImages)

        val result = useCase(42L)

        assertEquals(RemoveBookResult.NotFound, result)
        assertEquals(0, repository.removeCallCount)
        assertTrue(coverImages.deleted.isEmpty())
    }

    // D8: repository.removeBook returns false (row vanished between the read and the delete) ->
    // NotFound, no file touched.
    @Test
    fun removeBookReturnsFalse_returnsNotFound_touchesNoFile() = runTest {
        val repository = RemoveBookFakeLibraryRepository(baseBook(cover = Cover.Image("covers/old.jpg"))).apply {
            removeReturnsFalse = true
        }
        val useCase = RemoveBookUseCase(repository, coverImages)

        val result = useCase(42L)

        assertEquals(RemoveBookResult.NotFound, result)
        assertTrue(coverImages.deleted.isEmpty())
    }

    // D9: removeBook throws -> rethrows, no file touched.
    @Test
    fun removeBookThrows_rethrows_touchesNoFile() = runTest {
        val repository = RemoveBookFakeLibraryRepository(baseBook(cover = Cover.Image("covers/old.jpg"))).apply {
            removeThrows = true
        }
        val useCase = RemoveBookUseCase(repository, coverImages)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { useCase(42L) }
        }
        assertTrue(coverImages.deleted.isEmpty())
    }
}
