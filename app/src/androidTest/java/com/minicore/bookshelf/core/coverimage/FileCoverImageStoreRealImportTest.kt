package com.minicore.bookshelf.core.coverimage

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Review fix-findings (critical #1, BOOKSHELF-0031): [ImageDecoder.decodeBitmap] defaults to a
 * hardware bitmap, which the plain software [android.graphics.Canvas] in
 * `flattenOntoOpaqueBackground` cannot draw onto - that threw `IllegalArgumentException` and
 * crashed the app importing a real photo, because [FileCoverImageStore.importDraft]'s try/catch
 * only caught [CoverImportException]. This exercises the real decode path end to end (not a
 * fake), proving the store now sets `ImageDecoder.ALLOCATOR_SOFTWARE` and that any failure
 * anywhere in the decode/flatten/write chain surfaces as a single [CoverImportException] with no
 * partial file left behind, instead of an uncaught crash.
 */
@RunWith(AndroidJUnit4::class)
class FileCoverImageStoreRealImportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = FileCoverImageStore(context, Dispatchers.IO)

    private fun realJpegUri(width: Int = 1200, height: Int = 900): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val sourceFile = File(context.cacheDir, "real-photo-fixture-${width}x$height.jpg")
        FileOutputStream(sourceFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        bitmap.recycle()
        return Uri.fromFile(sourceFile).toString()
    }

    @Test
    fun importingARealJpegLargerThanTheDownscaleTarget_doesNotCrash() = runBlocking {
        val draftRef = store.importDraft(realJpegUri(width = 1200, height = 900))

        assertTrue(CoverRefs.isValidDraftRef(draftRef))
        assertTrue(store.exists(draftRef))
    }

    @Test
    fun importingARealJpegSmallerThanTheDownscaleTarget_doesNotCrash() = runBlocking {
        val draftRef = store.importDraft(realJpegUri(width = 200, height = 150))

        assertTrue(CoverRefs.isValidDraftRef(draftRef))
        assertTrue(store.exists(draftRef))
    }

    // Round 2: this only proves a MISSING source throws - decode fails before `FileOutputStream`
    // even opens, so of course no partial file exists; it says nothing about the write-failure
    // cleanup path. `assertThrows` now also asserts the exception TYPE, which the previous
    // try/catch-and-ignore version never did.
    @Test
    fun decodeFailure_throwsCoverImportException_andLeavesNoPartialDraftFile() {
        val draftsDirBefore = File(context.cacheDir, CoverRefs.DRAFTS_DIR).apply { mkdirs() }
        val filesBefore = draftsDirBefore.listFiles()?.size ?: 0

        assertThrows(CoverImportException::class.java) {
            runBlocking { store.importDraft("file:///does/not/exist.jpg") }
        }

        val filesAfter = draftsDirBefore.listFiles()?.size ?: 0
        assertTrue(filesAfter == filesBefore)
    }

    // Round 2: actually covers the write-failure cleanup path - decode and flatten succeed (a
    // real, valid source), but `writeJpeg`'s `FileOutputStream` fails to open because the drafts
    // directory itself has been made unwritable, forcing the one failure mode
    // `decodeFailure_throwsCoverImportException_andLeavesNoPartialDraftFile` above cannot reach.
    @Test
    fun writeFailure_throwsCoverImportException_andLeavesNoPartialDraftFile() {
        val draftsDir = File(context.cacheDir, CoverRefs.DRAFTS_DIR).apply { mkdirs() }
        val filesBefore = draftsDir.listFiles()?.size ?: 0

        assertTrue(
            "test setup: could not make the drafts dir read-only",
            draftsDir.setWritable(false, false),
        )
        try {
            assertThrows(CoverImportException::class.java) {
                runBlocking { store.importDraft(realJpegUri()) }
            }
        } finally {
            draftsDir.setWritable(true, false)
        }

        val filesAfter = draftsDir.listFiles()?.size ?: 0
        assertTrue(filesAfter == filesBefore)
    }
}
