package com.minicore.bookshelf.features.library.ui

import android.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.designsystem.component.BOOK_COVER_TEST_TAG_PREFIX
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.features.library.domain.LibraryFilter
import com.minicore.bookshelf.features.library.domain.LibraryGrid
import com.minicore.bookshelf.features.library.domain.LibraryRows
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// FF5 (plan section 6): proves the *mapping* from state to pixel for the grid's dot colours, the
// lent ring, the selected chip fill and the (never-dimmed) grid cover - mirrors
// LibraryRowsPixelTest's darkest-pixel/centre-pixel approach. No Hilt needed: LibraryScreen is
// stateless.
private const val STATUS_READING_ARGB = 0xFF3F7A5B.toInt()
private const val GOLD_ARGB = 0xFFC9A227.toInt()
private const val STATUS_WANT_TO_READ_ARGB = 0xFF9B8F7A.toInt()
private const val TERRACOTTA_ARGB = 0xFFC1693C.toInt()
private const val PAPER_ARGB = 0xFFF3EEE3.toInt()
private const val FOREST_ARGB = 0xFF2F4538.toInt()
private const val COVER_1_BG_ARGB = 0xFF3F6D63.toInt()
private const val CHANNEL_TOLERANCE = 10

class LibraryGridPixelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun book(id: Long, title: String, status: BookStatus) = Book(
        id = id,
        title = title,
        author = "Author",
        isbn = null,
        coverUri = null,
        genre = null,
        pageCount = null,
        publishedYear = null,
        status = status,
        rating = null,
        synopsis = null,
        createdAt = Instant.EPOCH,
    )

    private fun maxChannelDiff(pixel: Int, target: Int): Int {
        val dr = Math.abs(Color.red(pixel) - Color.red(target))
        val dg = Math.abs(Color.green(pixel) - Color.green(target))
        val db = Math.abs(Color.blue(pixel) - Color.blue(target))
        return maxOf(dr, dg, db)
    }

    private fun assertWithinTolerance(label: String, pixel: Int, target: Int) {
        assertTrue(
            "$label: pixel (${Color.red(pixel)},${Color.green(pixel)},${Color.blue(pixel)}) must be " +
                "within ±$CHANNEL_TOLERANCE per channel of target " +
                "(${Color.red(target)},${Color.green(target)},${Color.blue(target)})",
            maxChannelDiff(pixel, target) <= CHANNEL_TOLERANCE,
        )
    }

    // Reading (id 0), Finished (id 1), WantToRead (id 2), Finished+lent (id 3). Book ids double
    // as the cover-palette index (plan R2): id 0 -> cover_1_bg.
    private fun setContent() {
        val loan = OpenLoan(loanId = 1, borrowerName = "Priya", loanedAt = Instant.EPOCH, dueAt = LocalDate.now())
        val books = listOf(
            LibraryBook(book(0, "Reading Book", BookStatus.Reading), null),
            LibraryBook(book(1, "Finished Book", BookStatus.Finished), null),
            LibraryBook(book(2, "Want Book", BookStatus.WantToRead), null),
            LibraryBook(book(3, "Lent Book", BookStatus.Finished), loan),
        )
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryScreen(
                        uiState = LibraryUiState.Content(
                            rows = LibraryRows(emptyList(), emptyList()),
                            grid = LibraryGrid(
                                filter = LibraryFilter.All,
                                counts = LibraryCounts(all = 4, reading = 1, finished = 2, wantToRead = 1, lentOut = 1),
                                books = books,
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun dotBounds(bookId: Long) = composeRule
        .onNodeWithTag(libraryGridStatusDotTag(bookId), useUnmergedTree = true)
        .fetchSemanticsNode()
        .boundsInRoot

    @Test
    fun readingDot_fillIsStatusReadingGreen() {
        setContent()
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

        val bounds = dotBounds(0)
        val centre = bitmap.getPixel(
            (bounds.left + bounds.width / 2).toInt(),
            (bounds.top + bounds.height / 2).toInt(),
        )
        assertWithinTolerance("reading dot fill", centre, STATUS_READING_ARGB)
    }

    @Test
    fun finishedDot_fillIsGold() {
        setContent()
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

        val bounds = dotBounds(1)
        val centre = bitmap.getPixel(
            (bounds.left + bounds.width / 2).toInt(),
            (bounds.top + bounds.height / 2).toInt(),
        )
        assertWithinTolerance("finished dot fill", centre, GOLD_ARGB)
    }

    @Test
    fun wantToReadDot_fillIsStatusWantToReadTaupe() {
        setContent()
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

        val bounds = dotBounds(2)
        val centre = bitmap.getPixel(
            (bounds.left + bounds.width / 2).toInt(),
            (bounds.top + bounds.height / 2).toInt(),
        )
        assertWithinTolerance("want-to-read dot fill", centre, STATUS_WANT_TO_READ_ARGB)
    }

    @Test
    fun lentDot_fillIsPaper_ringIsTerracotta() {
        setContent()
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

        val bounds = dotBounds(3)
        val centre = bitmap.getPixel(
            (bounds.left + bounds.width / 2).toInt(),
            (bounds.top + bounds.height / 2).toInt(),
        )
        assertWithinTolerance("lent dot fill", centre, PAPER_ARGB)

        // 2px inside the top edge: inside the 2dp ring, outside the inset inner fill circle.
        val ringSample = bitmap.getPixel(
            (bounds.left + bounds.width / 2).toInt(),
            (bounds.top + 2).toInt(),
        )
        assertWithinTolerance("lent dot ring", ringSample, TERRACOTTA_ARGB)
    }

    @Test
    fun selectedChip_fillIsForest() {
        setContent()
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

        val bounds = composeRule.onNodeWithTag(libraryFilterChipTag(LibraryFilter.All))
            .fetchSemanticsNode().boundsInRoot
        // Sample the chip's left edge, away from any glyph, to read the fill rather than text.
        val sample = bitmap.getPixel((bounds.left + 4).toInt(), (bounds.top + bounds.height / 2).toInt())
        assertWithinTolerance("selected chip fill", sample, FOREST_ARGB)
    }

    @Test
    fun gridCover_isNeverDimmed_equalsItsPaletteBackground() {
        setContent()
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

        val bounds = composeRule.onNode(
            hasTestTag(BOOK_COVER_TEST_TAG_PREFIX + "0") and hasAnyAncestor(hasTestTag(libraryGridCardTag(0))),
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot

        val sample = bitmap.getPixel(
            (bounds.left + bounds.width / 2).toInt(),
            (bounds.top + bounds.height / 2).toInt(),
        )
        assertWithinTolerance("grid cover (undimmed)", sample, COVER_1_BG_ARGB)
    }
}
