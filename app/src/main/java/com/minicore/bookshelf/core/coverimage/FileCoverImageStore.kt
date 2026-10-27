package com.minicore.bookshelf.core.coverimage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val MAX_LONG_EDGE_PX = 600
private const val JPEG_QUALITY = 80

/**
 * [CoverImageStore] backed by app-private files (ADR-003, plan #31 section 4.4). Decodes with
 * [ImageDecoder]'s header callback so the full-resolution bitmap is never allocated (security
 * section 5: a decompression-bomb image cannot OOM this path), downscales to at most
 * [MAX_LONG_EDGE_PX] on the long edge, flattens onto an opaque white background (JPEG has no
 * alpha channel) and re-encodes as JPEG - which also strips EXIF, so no GPS/device metadata
 * survives the import.
 */
class FileCoverImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CoverImageStore {

    override suspend fun importDraft(sourceUri: String): String = withContext(ioDispatcher) {
        val draftsDir = File(context.cacheDir, CoverRefs.DRAFTS_DIR).apply { mkdirs() }
        val fileName = "${UUID.randomUUID()}.jpg"
        val target = File(draftsDir, fileName)
        try {
            importInto(sourceUri, target)
        } catch (e: CoverImportException) {
            target.delete()
            throw e
        } catch (e: Exception) {
            target.delete()
            throw CoverImportException("could not import $sourceUri", e)
        }
        "${CoverRefs.DRAFTS_DIR}/$fileName"
    }

    /**
     * Decode -> flatten -> encode as one unit so any failure anywhere in the chain (including a
     * [Canvas] rejecting a hardware bitmap, or [writeJpeg] failing) leaves no partial [target]
     * file and is reported as a single [CoverImportException] to the caller.
     */
    private fun importInto(sourceUri: String, target: File) {
        val decoded = decodeDownscaled(sourceUri)
        val flattened = try {
            flattenOntoOpaqueBackground(decoded)
        } finally {
            decoded.recycle()
        }
        try {
            writeJpeg(flattened, target)
        } finally {
            flattened.recycle()
        }
    }

    override suspend fun discardDraft(draftRef: String): Unit = withContext(ioDispatcher) {
        resolveDraft(draftRef)?.delete()
        Unit
    }

    override suspend fun commit(draftRef: String): String = withContext(ioDispatcher) {
        val draftFile = resolveDraft(draftRef)
            ?: throw CoverImportException("draft ref did not resolve to a file: $draftRef")
        val coversDir = File(context.filesDir, CoverRefs.COVERS_DIR).apply { mkdirs() }
        val fileName = "${UUID.randomUUID()}.jpg"
        val target = File(coversDir, fileName)
        if (!draftFile.renameTo(target)) {
            draftFile.copyTo(target, overwrite = true)
            draftFile.delete()
        }
        "${CoverRefs.COVERS_DIR}/$fileName"
    }

    override suspend fun uncommit(storedRef: String, draftRef: String): Unit = withContext(ioDispatcher) {
        val storedFile = resolveStored(storedRef) ?: return@withContext
        val draftRefFile = resolveDraft(draftRef) ?: return@withContext
        draftRefFile.parentFile?.mkdirs()
        if (!storedFile.renameTo(draftRefFile)) {
            // `copyTo` can throw `IOException` - swallowed here so a failure to roll back the
            // cover never masks the original insert exception this runs inside the catch of
            // (optional finding, round 2).
            runCatching { storedFile.copyTo(draftRefFile, overwrite = true) }
            storedFile.delete()
        }
    }

    override suspend fun delete(storedRef: String): Unit = withContext(ioDispatcher) {
        resolveStored(storedRef)?.delete()
        Unit
    }

    override suspend fun exists(ref: String): Boolean = withContext(ioDispatcher) {
        (resolveDraft(ref)?.isFile == true) || (resolveStored(ref)?.isFile == true)
    }

    /** `null` for anything that is not a well-formed draft ref (security section 5: no path traversal). */
    private fun resolveDraft(ref: String): File? {
        if (!CoverRefs.isValidDraftRef(ref)) return null
        return File(File(context.cacheDir, CoverRefs.DRAFTS_DIR), CoverRefs.fileNameOf(ref))
    }

    /** `null` for anything that is not a well-formed stored ref (security section 5: no path traversal). */
    private fun resolveStored(ref: String): File? {
        if (!CoverRefs.isValidStoredRef(ref)) return null
        return File(File(context.filesDir, CoverRefs.COVERS_DIR), CoverRefs.fileNameOf(ref))
    }

    private fun decodeDownscaled(sourceUri: String): Bitmap {
        val source = try {
            ImageDecoder.createSource(context.contentResolver, sourceUri.toUri())
        } catch (e: Exception) {
            throw CoverImportException("could not open $sourceUri", e)
        }
        return try {
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Default allocation can return a hardware bitmap, which the plain software
                // Canvas in flattenOntoOpaqueBackground() cannot draw onto (IllegalArgumentException).
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val longEdge = maxOf(width, height)
                if (longEdge > MAX_LONG_EDGE_PX) {
                    val scale = MAX_LONG_EDGE_PX.toDouble() / longEdge
                    decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
                }
            }
        } catch (e: Exception) {
            throw CoverImportException("could not decode $sourceUri", e)
        }
    }

    private fun flattenOntoOpaqueBackground(bitmap: Bitmap): Bitmap {
        val flattened = createBitmap(bitmap.width, bitmap.height)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return flattened
    }

    private fun writeJpeg(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (!ok) throw CoverImportException("JPEG compression failed for ${file.path}")
        }
    }
}
