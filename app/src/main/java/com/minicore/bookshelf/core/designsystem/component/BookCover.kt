package com.minicore.bookshelf.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset

const val BOOK_COVER_TEST_TAG_PREFIX = "book_cover_"

private val RowCoverShape = RoundedCornerShape(8.dp)
private val RowCoverWidth = 132.dp
private val RowCoverHeight = 188.dp
private val GridCoverShape = RoundedCornerShape(7.dp)

/** The two places [BookCover] is drawn (plan G9): a fixed-size row card, or a grid cell. */
enum class BookCoverVariant { Row, Grid }

/**
 * The shared cover placeholder used by the Library rows ([com.minicore.bookshelf.features])
 * and, from #21, the library grid (plan R1/G9). Always draws a colour-block placeholder -
 * [Cover.Image] is accepted but its `uri` is **ignored**: image loading is #31's decision
 * (BOOKSHELF-0030 plan S6), so both an image and a preset currently draw a colour block. Imports
 * nothing from `features` (`LayeringTest`).
 *
 * @param bookId used (not [title]) to pick the placeholder colour for a [Cover.Image] from a
 *   fixed six-colour palette via `Math.floorMod` (plan R2) - stable across renames, identical
 *   wherever the same book's cover is drawn. A [Cover.Preset] resolves to its own colour instead
 *   (BOOKSHELF-0030 S6), through an exhaustive `when` on [CoverPreset], never ordinal/list
 *   position.
 * @param cover the book's [Cover.Image] or [Cover.Preset] (BOOKSHELF-0030).
 * @param dimmed `true` for a lent-out book: the whole cover (including the title) is drawn at
 *   55% opacity, exactly as mocked (plan D15). The title's contrast is reduced at that opacity;
 *   accepted, see plan section 12 (Q7). Only meaningful for [BookCoverVariant.Row] - the grid
 *   variant is never dimmed (plan section 1: the grid's one lent book is drawn at full opacity).
 * @param variant [BookCoverVariant.Row] is the #20 row card: a fixed 132x188dp size, 8dp radius,
 *   4dp shadow and the title overlaid bottom-start. [BookCoverVariant.Grid] fills the available
 *   width at a 3:4.3 aspect ratio, 7dp radius, 2dp shadow and **no title overlay** - the grid
 *   draws the title below the cover itself (plan G9/G12).
 */
@Composable
fun BookCover(
    bookId: Long,
    title: String,
    cover: Cover,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    variant: BookCoverVariant = BookCoverVariant.Row,
) {
    val palette = BookshelfTheme.extendedColors.coverPalette
    val colors = coverColorsFor(bookId, cover, palette)

    when (variant) {
        BookCoverVariant.Row -> Surface(
            modifier = modifier
                .testTag(BOOK_COVER_TEST_TAG_PREFIX + bookId)
                .size(RowCoverWidth, RowCoverHeight)
                .alpha(if (dimmed) 0.55f else 1f)
                .shadow(4.dp, RowCoverShape),
            shape = RowCoverShape,
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

        BookCoverVariant.Grid -> Surface(
            modifier = modifier
                .testTag(BOOK_COVER_TEST_TAG_PREFIX + bookId)
                .fillMaxWidth()
                .aspectRatio(3f / 4.3f)
                .shadow(2.dp, GridCoverShape),
            shape = GridCoverShape,
            color = colors.background,
        ) {}
    }
}

/**
 * [Cover.Image] uses `Math.floorMod`, not title hashing: a rename must never change the colour
 * (plan R2/Q4). [Cover.Preset] resolves through an exhaustive `when`, never ordinal/list position
 * (BOOKSHELF-0030 S6) - the no-visible-change guarantee (plan S4) depends on this matching the
 * same index the pre-#30 `floorMod` formula drew for every existing book.
 */
internal fun coverColorsFor(bookId: Long, cover: Cover, palette: List<CoverColors>): CoverColors =
    when (cover) {
        is Cover.Image -> palette[Math.floorMod(bookId, palette.size.toLong()).toInt()]
        is Cover.Preset -> when (cover.preset) {
            CoverPreset.Preset1 -> palette[0]
            CoverPreset.Preset2 -> palette[1]
            CoverPreset.Preset3 -> palette[2]
            CoverPreset.Preset4 -> palette[3]
            CoverPreset.Preset5 -> palette[4]
            CoverPreset.Preset6 -> palette[5]
        }
    }

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
                BookCover(bookId = id, title = "Book $id: A Long Title", cover = Cover.Preset(CoverPreset.forBookId(id)))
            }
        }
    }
}

@Preview(name = "BookCover — dimmed (lent)")
@Composable
private fun BookCoverDimmedPreview() {
    BookshelfTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(8.dp)) {
            BookCover(bookId = 1, title = "A Lent Book", cover = Cover.Preset(CoverPreset.forBookId(1)), dimmed = true)
        }
    }
}

@Preview(name = "BookCover — grid variant")
@Composable
private fun BookCoverGridPreview() {
    BookshelfTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(8.dp),
        ) {
            for (id in 0L until 3L) {
                Box(modifier = Modifier.size(100.dp)) {
                    BookCover(
                        bookId = id,
                        title = "Book $id",
                        cover = Cover.Preset(CoverPreset.forBookId(id)),
                        variant = BookCoverVariant.Grid,
                    )
                }
            }
        }
    }
}
