package com.minicore.bookshelf.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.designsystem.CoverColors

const val BOOK_COVER_TEST_TAG_PREFIX = "book_cover_"

private val CoverShape = RoundedCornerShape(8.dp)
private val CoverWidth = 132.dp
private val CoverHeight = 188.dp

/**
 * The shared cover placeholder used by the Library rows ([com.minicore.bookshelf.features])
 * and, from #21, the library grid (plan R1). Always draws a colour-block placeholder with the
 * title overlaid, bottom-start - [coverUri] is accepted but **ignored**: image loading arrives
 * with the first ticket that writes a non-null `coverUri` (no book-creation UI exists yet, so
 * none can). Imports nothing from `features` (`LayeringTest`).
 *
 * @param bookId used (not [title]) to pick the placeholder colour from a fixed six-colour
 *   palette via `Math.floorMod` (plan R2) - stable across renames, identical wherever the same
 *   book's cover is drawn.
 * @param dimmed `true` for a lent-out book: the whole cover (including the title) is drawn at
 *   55% opacity, exactly as mocked (plan D15). The title's contrast is reduced at that opacity;
 *   accepted, see plan section 12 (Q7).
 */
@Composable
fun BookCover(
    bookId: Long,
    title: String,
    coverUri: String?,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val palette = BookshelfTheme.extendedColors.coverPalette
    val colors = coverColorsFor(bookId, palette)

    Surface(
        modifier = modifier
            .testTag(BOOK_COVER_TEST_TAG_PREFIX + bookId)
            .size(CoverWidth, CoverHeight)
            .alpha(if (dimmed) 0.55f else 1f)
            .shadow(4.dp, CoverShape),
        shape = CoverShape,
        color = colors.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.content,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** `Math.floorMod`, not title hashing: a rename must never change the colour (plan R2/Q4). */
internal fun coverColorsFor(bookId: Long, palette: List<CoverColors>): CoverColors =
    palette[Math.floorMod(bookId, palette.size.toLong()).toInt()]

@Preview(name = "BookCover — palette")
@Composable
private fun BookCoverPalettePreview() {
    BookshelfTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(8.dp),
        ) {
            for (id in 0L until 6L) {
                BookCover(bookId = id, title = "Book $id: A Long Title", coverUri = null)
            }
        }
    }
}

@Preview(name = "BookCover — dimmed (lent)")
@Composable
private fun BookCoverDimmedPreview() {
    BookshelfTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(8.dp)) {
            BookCover(bookId = 1, title = "A Lent Book", coverUri = null, dimmed = true)
        }
    }
}
