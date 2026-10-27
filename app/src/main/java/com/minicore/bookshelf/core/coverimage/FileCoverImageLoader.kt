package com.minicore.bookshelf.core.coverimage

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [CoverImageLoader] backed by the same app-private files [FileCoverImageStore] writes (plan #31
 * section 4.4, decision A11). Accepts both a committed `covers/` ref and an in-progress
 * `cover-drafts/` ref (the latter so [com.minicore.bookshelf.features.library.ui.bookform.CoverPicker]
 * can preview a picked-but-not-yet-saved image) - anything else resolves to `null` (same
 * no-path-traversal guarantee as [FileCoverImageStore], via [CoverRefs]).
 *
 * Decoded with [BitmapFactory.Options.inSampleSize] downsampling toward [CoverImageLoader.load]'s
 * `maxSizePx`: the source file is already downscaled to at most 600px on import
 * ([FileCoverImageStore]), so this is a cheap safety margin, not the primary resize step.
 */
class FileCoverImageLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CoverImageLoader {

    override suspend fun load(ref: String, maxSizePx: Int): ImageBitmap? = withContext(ioDispatcher) {
        val file = resolveFile(ref) ?: return@withContext null
        if (!file.isFile) return@withContext null
        runCatching { decodeSampled(file, maxSizePx)?.asImageBitmap() }.getOrNull()
    }

    private fun resolveFile(ref: String): File? = when {
        CoverRefs.isValidStoredRef(ref) -> File(File(context.filesDir, CoverRefs.COVERS_DIR), CoverRefs.fileNameOf(ref))
        CoverRefs.isValidDraftRef(ref) -> File(File(context.cacheDir, CoverRefs.DRAFTS_DIR), CoverRefs.fileNameOf(ref))
        else -> null
    }

    private fun decodeSampled(file: File, maxSizePx: Int) = BitmapFactory.Options().run {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.path, this)
        val longEdge = maxOf(outWidth, outHeight)
        inSampleSize = if (longEdge > maxSizePx && maxSizePx > 0) {
            var sample = 1
            while (longEdge / (sample * 2) >= maxSizePx) sample *= 2
            sample
        } else {
            1
        }
        inJustDecodeBounds = false
        BitmapFactory.decodeFile(file.path, this)
    }
}
