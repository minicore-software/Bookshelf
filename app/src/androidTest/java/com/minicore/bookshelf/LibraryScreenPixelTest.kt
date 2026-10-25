package com.minicore.bookshelf

import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minicore.bookshelf.features.library.ui.LIBRARY_BRAND_MARK_TEST_TAG
import com.minicore.bookshelf.features.splash.ui.SPLASH_SCREEN_TEST_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min

private const val PAPER_ARGB = 0xFFF3EEE3.toInt()
private const val FOREST_ARGB = 0xFF2F4538.toInt()
private const val CHANNEL_TOLERANCE = 8

// F6/FF3: no near-white (or any off-palette) flash at the splash-to-landing handover, and the
// title is really drawn in ink on paper.
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LibraryScreenPixelTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun afterSplash_backgroundIsPaper_andTitleIsInk() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(SPLASH_SCREEN_TEST_TAG).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()

        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

        val margin = 4
        val corners = listOf(
            margin to margin,
            (bitmap.width - 1 - margin) to margin,
            margin to (bitmap.height - 1 - margin),
            (bitmap.width - 1 - margin) to (bitmap.height - 1 - margin),
            (bitmap.width / 2) to margin, // centre-top
        )
        for ((x, y) in corners) {
            assertEquals("pixel at ($x,$y) must be paper", PAPER_ARGB, bitmap.getPixel(x, y))
        }

        val titleBounds = composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.app_name)
        ).fetchSemanticsNode().boundsInRoot

        var darkestLuminance = Int.MAX_VALUE
        var darkestPixel = PAPER_ARGB
        val left = max(0, titleBounds.left.toInt())
        val top = max(0, titleBounds.top.toInt())
        val right = min(bitmap.width - 1, titleBounds.right.toInt())
        val bottom = min(bitmap.height - 1, titleBounds.bottom.toInt())

        for (y in top..bottom) {
            for (x in left..right) {
                val pixel = bitmap.getPixel(x, y)
                val luminance = Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)
                if (luminance < darkestLuminance) {
                    darkestLuminance = luminance
                    darkestPixel = pixel
                }
            }
        }

        assertTrue(
            "darkest pixel in the title bounds (${Color.red(darkestPixel)}," +
                "${Color.green(darkestPixel)},${Color.blue(darkestPixel)}) must be within " +
                "±$CHANNEL_TOLERANCE per channel of forest",
            maxChannelDiff(darkestPixel, FOREST_ARGB) <= CHANNEL_TOLERANCE
        )

        // F8: a sample away from the "B" glyph, inside the brand-mark tile, must be forest — the
        // tile's own fill, not a fallback/unstyled background.
        val markBounds = composeRule.onNodeWithTag(LIBRARY_BRAND_MARK_TEST_TAG)
            .fetchSemanticsNode().boundsInRoot
        val markSampleX = min(bitmap.width - 1, (markBounds.left + 2).toInt())
        val markSampleY = min(bitmap.height - 1, (markBounds.top + markBounds.height / 2).toInt())
        val markPixel = bitmap.getPixel(markSampleX, markSampleY)
        assertTrue(
            "brand-mark sample pixel (${Color.red(markPixel)},${Color.green(markPixel)}," +
                "${Color.blue(markPixel)}) must be within ±$CHANNEL_TOLERANCE per channel of forest",
            maxChannelDiff(markPixel, FOREST_ARGB) <= CHANNEL_TOLERANCE
        )
    }

    private fun maxChannelDiff(pixel: Int, target: Int): Int {
        val dr = Math.abs(Color.red(pixel) - Color.red(target))
        val dg = Math.abs(Color.green(pixel) - Color.green(target))
        val db = Math.abs(Color.blue(pixel) - Color.blue(target))
        return maxOf(dr, dg, db)
    }
}
