package com.minicore.bookshelf.core.coverimage

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes a stored cover-image ref ([com.minicore.bookshelf.core.model.Cover.Image.uri]) into a
 * bitmap for [com.minicore.bookshelf.core.designsystem.component.BookCover] to draw (plan #31
 * section 4.4, decision A11). A `null` result - loading, missing file, or an invalid ref - means
 * "draw the id-derived colour placeholder instead"; it is never an error the caller must handle.
 */
interface CoverImageLoader {
    suspend fun load(ref: String, maxSizePx: Int): ImageBitmap?
}

/** Draws nothing, ever: the default so `core/designsystem` needs no `Context` or IO. */
object NoOpCoverImageLoader : CoverImageLoader {
    override suspend fun load(ref: String, maxSizePx: Int): ImageBitmap? = null
}

/** Provided from Hilt in `MainActivity`; defaults to [NoOpCoverImageLoader] everywhere else (previews, existing pixel tests). */
val LocalCoverImageLoader = staticCompositionLocalOf<CoverImageLoader> { NoOpCoverImageLoader }
