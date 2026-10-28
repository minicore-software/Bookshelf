package com.minicore.bookshelf.features.library.ui.editbook

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.core.coverimage.CoverRefs
import com.minicore.bookshelf.core.coverimage.FileCoverImageStore
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.CoverDraft
import com.minicore.bookshelf.features.library.domain.CurrentDateProvider
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import com.minicore.bookshelf.features.library.domain.ObserveBookDetailUseCase
import com.minicore.bookshelf.features.library.domain.UpdateBookUseCase
import com.minicore.bookshelf.features.library.domain.model.LentBook
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import java.io.File
import java.io.FileOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private class CoverLifecycleFakeLibraryRepository(initial: LibraryBook?) : LibraryRepository {
    val byId = MutableStateFlow(initial)

    override fun observeBooks(status: BookStatus?): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeCurrentlyReading(): Flow<List<LibraryBook>> = throw NotImplementedError()
    override fun observeLentOutBooks(): Flow<List<LentBook>> = throw NotImplementedError()
    override fun observeCounts(): Flow<LibraryCounts> = throw NotImplementedError()
    override fun observeBook(id: Long): Flow<Book?> = MutableStateFlow(byId.value?.book)
    override fun observeLibraryBook(id: Long): Flow<LibraryBook?> = byId
    override suspend fun addBook(book: Book): Long = throw NotImplementedError()

    override suspend fun updateBook(book: Book): Boolean {
        byId.value = byId.value?.copy(book = book)
        return true
    }
}

/**
 * Review finding #1 (risks R3/R4): the cover file's real on-disk lifecycle through
 * [EditBookViewModel] + [UpdateBookUseCase], backed by the real [FileCoverImageStore] (not a
 * fake), so a leaked or wrongly-deleted file would actually show up under `files/covers/` or
 * `cache/cover-drafts/`.
 */
@RunWith(AndroidJUnit4::class)
class EditBookCoverLifecycleTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val coverImages = FileCoverImageStore(context, Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC)

    private val coversDir = File(context.filesDir, CoverRefs.COVERS_DIR)
    private val draftsDir = File(context.cacheDir, CoverRefs.DRAFTS_DIR)

    private fun book(id: Long, cover: Cover) = Book(
        id = id,
        title = "Dune",
        author = "Frank Herbert",
        isbn = null,
        cover = cover,
        genre = null,
        pageCount = null,
        publishedYear = 1965,
        status = BookStatus.Finished,
        rating = 4,
        synopsis = null,
        createdAt = Instant.EPOCH,
    )

    private fun realJpegUri(): String {
        val bitmap = Bitmap.createBitmap(200, 150, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val sourceFile = File(context.cacheDir, "cover-lifecycle-fixture-${System.nanoTime()}.jpg")
        FileOutputStream(sourceFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        bitmap.recycle()
        return Uri.fromFile(sourceFile).toString()
    }

    private fun viewModel(repository: CoverLifecycleFakeLibraryRepository, bookId: Long): EditBookViewModel = EditBookViewModel(
        ObserveBookDetailUseCase(repository, CurrentDateProvider(Provider { clock })),
        UpdateBookUseCase(repository, coverImages),
        coverImages,
        clock,
        SavedStateHandle(mapOf("bookId" to bookId)),
    )

    private fun coversDirFiles(): List<File> = coversDir.listFiles()?.toList() ?: emptyList()
    private fun draftsDirFiles(): List<File> = draftsDir.listFiles()?.toList() ?: emptyList()

    private suspend fun pollUntil(timeoutMillis: Long = 5_000, description: String, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) error("timed out waiting for: $description")
            delay(10)
        }
    }

    // A preset cover, changed to a picked image and saved, commits a real file under
    // `files/covers/`.
    @Test
    fun presetToImage_onSave_createsAFileUnderFilesCovers() = runBlocking {
        val repository = CoverLifecycleFakeLibraryRepository(LibraryBook(book(1L, Cover.Preset(CoverPreset.Preset1)), null))
        val vm = viewModel(repository, 1L)
        pollUntil(description = "VM loaded") { vm.uiState.value.isLoaded }
        val coversBefore = coversDirFiles().size

        vm.onImagePicked(realJpegUri())
        pollUntil(description = "cover becomes an Image draft") { vm.uiState.value.cover is CoverDraft.Image }
        vm.onSave()
        vm.onDone.first()

        assertEquals(coversBefore + 1, coversDirFiles().size)
        val stored = (repository.byId.value?.book?.cover as Cover.Image).uri
        assertTrue(CoverRefs.isValidStoredRef(stored))
        assertTrue(coverImages.exists(stored))
    }

    // An image cover, changed back to a preset and saved, deletes the old stored file - the real
    // one on disk, not just the ref in the row.
    @Test
    fun imageToPreset_onSave_deletesTheOldStoredFile() = runBlocking {
        val originalRef = coverImages.commit(coverImages.importDraft(realJpegUri()))
        val repository = CoverLifecycleFakeLibraryRepository(LibraryBook(book(2L, Cover.Image(originalRef)), null))
        val vm = viewModel(repository, 2L)
        pollUntil(description = "VM loaded") { vm.uiState.value.isLoaded }
        assertTrue(File(context.filesDir, originalRef).exists())

        vm.onRemoveImage()
        pollUntil(description = "cover becomes a Preset") { vm.uiState.value.cover is CoverDraft.Preset }
        vm.onSave()
        vm.onDone.first()

        assertFalse(File(context.filesDir, originalRef).exists())
        assertFalse(coverImages.exists(originalRef))
    }

    // Replacing an image then Cancelling (not Saving) must leave the original stored file alone
    // and must not leave the freshly-imported draft behind in `cache/cover-drafts/`.
    @Test
    fun imageReplaceThenCancel_keepsTheOldFile_andLeavesNoDraftBehind() = runBlocking {
        val originalRef = coverImages.commit(coverImages.importDraft(realJpegUri()))
        val repository = CoverLifecycleFakeLibraryRepository(LibraryBook(book(3L, Cover.Image(originalRef)), null))
        val vm = viewModel(repository, 3L)
        pollUntil(description = "VM loaded") { vm.uiState.value.isLoaded }
        val draftsBefore = draftsDirFiles().size

        vm.onImagePicked(realJpegUri())
        pollUntil(description = "cover becomes an Image draft") { vm.uiState.value.cover is CoverDraft.Image }
        val newDraftRef = (vm.uiState.value.cover as CoverDraft.Image).draftRef
        vm.onDismissRequest()
        vm.onDone.first()

        assertTrue("the original stored file must survive a cancel", File(context.filesDir, originalRef).exists())
        assertTrue(coverImages.exists(originalRef))
        pollUntil(description = "the replaced draft to be discarded") { !coverImages.exists(newDraftRef) }
        assertEquals("no leftover draft file after cancel", draftsBefore, draftsDirFiles().size)
    }
}
