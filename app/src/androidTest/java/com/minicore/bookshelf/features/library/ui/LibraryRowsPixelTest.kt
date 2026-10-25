package com.minicore.bookshelf.features.library.ui

import android.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasTestTag
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.features.library.domain.LentOutRow
import com.minicore.bookshelf.features.library.domain.LibraryRows
import com.minicore.bookshelf.features.library.domain.LoanDueStatus
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// FF5 (plan section 6): proves the *mapping* from state to pixel, not just the wording - an
// inverted `if (row.dueStatus.isOverdue)` or a `dimmed` flag moved to the wrong card would still
// pass every text-based test but must fail here. Mirrors LibraryScreenPixelTest's
// darkest-pixel-in-bounds approach. No Hilt needed: LibraryScreen is stateless.
private const val OVERDUE_ARGB = 0xFF9B2C2C.toInt()
private const val DUE_UPCOMING_ARGB = 0xFFA4532C.toInt()
private const val COVER_1_BG_ARGB = 0xFF3F6D63.toInt()
private const val PAPER_ARGB = 0xFFF3EEE3.toInt()
private const val CHANNEL_TOLERANCE = 10
private const val DIM_ALPHA = 0.55

class LibraryRowsPixelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun book(id: Long, title: String = "Book $id") = Book(
        id = id,
        title = title,
        author = "Author",
        isbn = null,
        coverUri = null,
        genre = null,
        pageCount = null,
        publishedYear = null,
        status = BookStatus.Reading,
        rating = null,
        synopsis = null,
        createdAt = Instant.EPOCH,
    )

    // Both cards use book id 0 (palette entry cover_1_bg, #3F6D63) so the reading card's
    // undimmed cover and the lent card's dimmed cover can be compared against the *same* base
    // colour.
    private fun setContent() {
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(
                        uiState = LibraryUiState.Content(
                            LibraryRows(
                                currentlyReading = listOf(book(0, "Reading Book")),
                                lentOut = listOf(
                                    LentOutRow(book(0, "Overdue Book"), "Priya", LoanDueStatus.Overdue(2)),
                                    LentOutRow(book(1, "Upcoming Book"), "Sam", LoanDueStatus.DueSoon(3)),
                                ),
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun darkestPixelInBounds(bitmapGetPixel: (Int, Int) -> Int, left: Int, top: Int, right: Int, bottom: Int): Int {
        var darkestLuminance = Int.MAX_VALUE
        var darkestPixel = PAPER_ARGB
        for (y in top..bottom) {
            for (x in left..right) {
                val pixel = bitmapGetPixel(x, y)
                val luminance = Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)
                if (luminance < darkestLuminance) {
                    darkestLuminance = luminance
                    darkestPixel = pixel
                }
            }
        }
        return darkestPixel
    }

    private fun maxChannelDiff(pixel: Int, target: Int): Int {
        val dr = Math.abs(Color.red(pixel) - Color.red(target))
        val dg = Math.abs(Color.green(pixel) - Color.green(target))
        val db = Math.abs(Color.blue(pixel) - Color.blue(target))
        return maxOf(dr, dg, db)
    }

    private fun blend(foreground: Int, background: Int, alpha: Double): Int {
        val r = (Color.red(foreground) * alpha + Color.red(background) * (1 - alpha)).toInt()
        val g = (Color.green(foreground) * alpha + Color.green(background) * (1 - alpha)).toInt()
        val b = (Color.blue(foreground) * alpha + Color.blue(background) * (1 - alpha)).toInt()
        return Color.rgb(r, g, b)
    }

    private fun assertWithinTolerance(label: String, pixel: Int, target: Int) {
        assertTrue(
            "$label: pixel (${Color.red(pixel)},${Color.green(pixel)},${Color.blue(pixel)}) must be " +
                "within ±$CHANNEL_TOLERANCE per channel of target " +
                "(${Color.red(target)},${Color.green(target)},${Color.blue(target)})",
            maxChannelDiff(pixel, target) <= CHANNEL_TOLERANCE,
        )
    }

    @Test
    fun overdueCaption_isDrawnInTheOverdueColor() {
        setContent()
        composeRule.waitForIdle()

        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val bounds = composeRule.onNodeWithTag(libraryLentCaptionTag(0), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        val darkest = darkestPixelInBounds(
            bitmap::getPixel,
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.right.toInt() - 1,
            bounds.bottom.toInt() - 1,
        )
        assertWithinTolerance("overdue caption", darkest, OVERDUE_ARGB)
    }

    @Test
    fun dueSoonCaption_isDrawnInTheDueUpcomingColor_notOverdue() {
        setContent()
        composeRule.waitForIdle()

        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val bounds = composeRule.onNodeWithTag(libraryLentCaptionTag(1), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        val darkest = darkestPixelInBounds(
            bitmap::getPixel,
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.right.toInt() - 1,
            bounds.bottom.toInt() - 1,
        )
        assertWithinTolerance("due-soon caption", darkest, DUE_UPCOMING_ARGB)
        assertTrue(
            "a due-soon caption must not be the overdue colour",
            maxChannelDiff(darkest, OVERDUE_ARGB) > CHANNEL_TOLERANCE,
        )
    }

    @Test
    fun lentCover_isDimmed_readingCoverOfTheSameBook_isNot() {
        setContent()
        composeRule.waitForIdle()

        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

        // Sample near each card's top-left inner corner: inside the cover's 8dp rounded-corner
        // clip, well above the bottom-start title text. Both the reading and lent cards hold a
        // cover for book id 0 (same palette colour), so scope the sample by the ancestor card
        // tag, not by the (ambiguous) cover tag alone.
        val readingCoverBounds = composeRule.onNode(hasTestTag(libraryReadingCardTag(0)))
            .fetchSemanticsNode()
        val lentCoverBounds = composeRule.onNode(hasTestTag(libraryLentCardTag(0)))
            .fetchSemanticsNode()

        val readingSample = bitmap.getPixel(
            (readingCoverBounds.boundsInRoot.left + 30).toInt(),
            (readingCoverBounds.boundsInRoot.top + 30).toInt(),
        )
        val lentSample = bitmap.getPixel(
            (lentCoverBounds.boundsInRoot.left + 30).toInt(),
            (lentCoverBounds.boundsInRoot.top + 30).toInt(),
        )

        assertWithinTolerance("undimmed reading cover", readingSample, COVER_1_BG_ARGB)
        assertWithinTolerance(
            "dimmed lent cover",
            lentSample,
            blend(COVER_1_BG_ARGB, PAPER_ARGB, DIM_ALPHA),
        )
    }
}
