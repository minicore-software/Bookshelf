package com.minicore.bookshelf.core.designsystem.component

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.minicore.bookshelf.core.coverimage.CoverImageLoader
import com.minicore.bookshelf.core.coverimage.LocalCoverImageLoader
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val FAKE_IMAGE_ARGB = -0xff8001 // opaque, distinct from every coverPalette entry

/**
 * Plan #31 decision A11: once an image can actually be set and saved through the real UI,
 * [BookCover] must draw it, not the id-derived placeholder colour it drew before this ticket
 * wired the upload path end to end.
 */
class BookCoverImageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun solidColorBitmap(argb: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(argb)
        return bitmap.asImageBitmap()
    }

    private fun maxChannelDiff(pixel: Int, target: Int): Int {
        val dr = Math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(target))
        val dg = Math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(target))
        val db = Math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(target))
        return maxOf(dr, dg, db)
    }

    @Test
    fun coverImage_withALoadedBitmap_drawsTheRealImage_notThePlaceholder() {
        val loader = object : CoverImageLoader {
            override suspend fun load(ref: String, maxSizePx: Int): ImageBitmap = solidColorBitmap(FAKE_IMAGE_ARGB)
        }

        composeRule.setContent {
            CompositionLocalProvider(LocalCoverImageLoader provides loader) {
                BookshelfTheme {
                    BookCover(
                        bookId = 0,
                        title = "A Book",
                        cover = Cover.Image("covers/11111111-1111-1111-1111-111111111111.jpg"),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag(BOOK_COVER_TEST_TAG_PREFIX + "0").fetchSemanticsNode().boundsInRoot
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val centre = bitmap.getPixel((bounds.left + bounds.width / 2).toInt(), (bounds.top + bounds.height / 2).toInt())

        assertTrue(
            "expected the loaded image's colour, not the id-0 palette placeholder",
            maxChannelDiff(centre, FAKE_IMAGE_ARGB) <= 10,
        )
    }

    // A [Cover.Preset] must never attempt a load, even with a loader wired - the colour
    // placeholder is always correct for it.
    @Test
    fun coverPreset_neverLoadsAnImage() {
        var loadCalled = false
        val loader = object : CoverImageLoader {
            override suspend fun load(ref: String, maxSizePx: Int): ImageBitmap? {
                loadCalled = true
                return null
            }
        }

        composeRule.setContent {
            CompositionLocalProvider(LocalCoverImageLoader provides loader) {
                BookshelfTheme {
                    BookCover(bookId = 0, title = "A Book", cover = Cover.Preset(CoverPreset.Preset1))
                }
            }
        }
        composeRule.waitForIdle()

        assertTrue(!loadCalled)
    }

    // A null load result (loading, failure, or NoOpCoverImageLoader) falls back to the
    // placeholder, never a blank/crashing render.
    @Test
    fun coverImage_withANullLoadResult_fallsBackToPlaceholder_withoutCrashing() {
        val loader = object : CoverImageLoader {
            override suspend fun load(ref: String, maxSizePx: Int): ImageBitmap? = null
        }

        composeRule.setContent {
            CompositionLocalProvider(LocalCoverImageLoader provides loader) {
                BookshelfTheme {
                    BookCover(
                        bookId = 0,
                        title = "A Book",
                        cover = Cover.Image("covers/11111111-1111-1111-1111-111111111111.jpg"),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BOOK_COVER_TEST_TAG_PREFIX + "0").assertExists()
    }
}
