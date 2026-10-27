package com.minicore.bookshelf.features.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.designsystem.LibraryTextStyles
import com.minicore.bookshelf.core.designsystem.component.BookCover
import com.minicore.bookshelf.core.designsystem.component.BookCoverVariant
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.LibraryFilter
import com.minicore.bookshelf.features.library.domain.countFor
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts
import com.minicore.bookshelf.features.library.domain.model.OpenLoan
import java.time.Instant
import java.time.LocalDate

const val LIBRARY_FILTER_CHIPS_TEST_TAG = "library_filter_chips"
const val LIBRARY_GRID_TITLE_TEST_TAG = "library_grid_title"

fun libraryFilterChipTag(filter: LibraryFilter) = "library_filter_chip_$filter"
fun libraryGridCardTag(bookId: Long) = "library_grid_card_$bookId"
fun libraryGridLentCaptionTag(bookId: Long) = "library_grid_lent_caption_$bookId"
fun libraryGridStatusDotTag(bookId: Long) = "library_grid_status_dot_$bookId"

@Composable
private fun filterLabel(filter: LibraryFilter, count: Int): String {
    val res = when (filter) {
        LibraryFilter.All -> R.string.library_filter_all
        LibraryFilter.Reading -> R.string.library_filter_reading
        LibraryFilter.Finished -> R.string.library_filter_finished
        LibraryFilter.WantToRead -> R.string.library_filter_want_to_read
        LibraryFilter.LentOut -> R.string.library_filter_lent_out
    }
    return stringResource(res, count)
}

/** The single-choice chip row (plan G10/G11): scrolls with the content, is not sticky. */
@Composable
fun LibraryFilterChips(
    selected: LibraryFilter,
    counts: LibraryCounts,
    onFilterSelected: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .testTag(LIBRARY_FILTER_CHIPS_TEST_TAG)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (filter in LibraryFilter.entries) {
            LibraryFilterChip(
                label = filterLabel(filter, counts.countFor(filter)),
                selected = filter == selected,
                onClick = { onFilterSelected(filter) },
                modifier = Modifier.testTag(libraryFilterChipTag(filter)),
            )
        }
    }
}

@Composable
private fun LibraryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = BookshelfTheme.extendedColors
    val shape = RoundedCornerShape(20.dp)
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else extended.inkSoft
    val textStyle = if (selected) {
        LibraryTextStyles.chip.copy(fontWeight = FontWeight.SemiBold)
    } else {
        LibraryTextStyles.chip
    }

    // A 48dp touch target (plan G11) via an explicit minimum size on the outer, interactive box,
    // not `Modifier.minimumInteractiveComponentSize()`: that modifier only enlarges the layout
    // when it comes before every other size-affecting modifier in the *same* node's chain, and
    // ties the chip's clip/background/border to its enforced (invisible) size, which would
    // inflate the visible pill to 48dp too - `defaultMinSize` on a dedicated outer `Box`
    // (the same pattern as #20's "See all" touch target in LibraryRows.kt) keeps the ~31dp
    // visual pill nested, unaffected, and centred inside the full touch target.
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(backgroundColor)
                .then(
                    if (!selected) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = textStyle, color = textColor, maxLines = 1, softWrap = false)
        }
    }
}

/** The "Your library" section heading (plan G10). */
@Composable
fun LibraryGridTitle(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.library_grid_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .testTag(LIBRARY_GRID_TITLE_TEST_TAG)
            .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/** One grid cell: cover, status/lent dot, title, and a "Lent to X" caption where applicable. */
@Composable
fun LibraryGridCard(
    libraryBook: LibraryBook,
    column: Int,
    onBookClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val book = libraryBook.book
    val clickLabel = stringResource(R.string.library_book_click_label)
    val kind = libraryBook.statusDotKind()
    val openLoan = libraryBook.openLoan
    val statusLabel = book.status.label()
    val lentToText = openLoan?.let { stringResource(R.string.library_grid_lent_to, it.borrowerName) }
    // The merged card description, built in the order TalkBack should read (plan section 8:
    // "Sapiens, Finished, Lent to Priya"). The dot itself sets no contentDescription - see
    // LibraryStatusDot's KDoc - so this is the only place the status/lent text is announced.
    val cardDescription = listOfNotNull(book.title, statusLabel, lentToText).joinToString(", ")

    Column(
        modifier = modifier
            .padding(gridCellPadding(column))
            .clickable(onClickLabel = clickLabel, role = Role.Button) { onBookClick(book.id) }
            .semantics(mergeDescendants = true) { contentDescription = cardDescription }
            .testTag(libraryGridCardTag(book.id)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            BookCover(
                bookId = book.id,
                title = book.title,
                cover = book.cover,
                variant = BookCoverVariant.Grid,
            )
            LibraryStatusDot(
                kind = kind,
                status = book.status,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .testTag(libraryGridStatusDotTag(book.id)),
            )
        }
        Text(
            text = book.title,
            style = LibraryTextStyles.gridTitle,
            color = BookshelfTheme.extendedColors.inkSoft,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (openLoan != null && lentToText != null) {
            Text(
                text = lentToText,
                style = LibraryTextStyles.gridLentCaption,
                color = BookshelfTheme.extendedColors.lentCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(libraryGridLentCaptionTag(book.id)),
            )
        }
    }
}

/**
 * Per-cell padding by column index (plan G10): `end(c) + start(c+1) == 14dp`,
 * `start(0) == end(2) == 20dp`, and `start(c) + end(c)` is the same for every column, so the
 * three columns' content widths are equal. `column` is the item's absolute index modulo 3 -
 * valid because every item before the grid cells is full-span (plan G10).
 */
internal fun gridCellPadding(column: Int): PaddingValues {
    val margin = 20.dp
    val gap = 14.dp
    val middle = (gap + margin) / 3f
    val edge = (gap * 2f - margin) / 3f
    return when (column % 3) {
        0 -> PaddingValues(start = margin, end = edge, bottom = gap)
        1 -> PaddingValues(start = middle, end = middle, bottom = gap)
        else -> PaddingValues(start = edge, end = margin, bottom = gap)
    }
}

private fun previewBook(id: Long, title: String, status: BookStatus) = Book(
    id = id,
    title = title,
    author = "Author",
    isbn = null,
    cover = Cover.Preset(CoverPreset.forBookId(id)),
    genre = null,
    pageCount = null,
    publishedYear = null,
    status = status,
    rating = null,
    synopsis = null,
    comment = null,
    memoryWhy = null,
    memoryWhere = null,
    memoryWhat = null,
    createdAt = Instant.EPOCH,
)

private val previewLoan = OpenLoan(loanId = 1, borrowerName = "Priya", loanedAt = Instant.EPOCH, dueAt = LocalDate.now())

// Plan section 5: all four dot kinds (reading, finished, want-to-read, lent) in one preview.
@Preview(name = "LibraryGridSection — all dot kinds")
@Composable
private fun LibraryGridSectionAllDotKindsPreview() {
    BookshelfTheme {
        Surface {
            Column {
                LibraryFilterChips(
                    selected = LibraryFilter.All,
                    counts = LibraryCounts(all = 4, reading = 1, finished = 2, wantToRead = 1, lentOut = 1),
                    onFilterSelected = {},
                )
                LibraryGridTitle()
                Row {
                    LibraryGridCard(
                        libraryBook = LibraryBook(previewBook(1, "The Hidden Life of Trees", BookStatus.Reading), null),
                        column = 0,
                        onBookClick = {},
                    )
                    LibraryGridCard(
                        libraryBook = LibraryBook(previewBook(2, "Braiding Sweetgrass", BookStatus.Finished), null),
                        column = 1,
                        onBookClick = {},
                    )
                    LibraryGridCard(
                        libraryBook = LibraryBook(previewBook(3, "Pachinko", BookStatus.WantToRead), null),
                        column = 2,
                        onBookClick = {},
                    )
                }
                Row {
                    LibraryGridCard(
                        libraryBook = LibraryBook(previewBook(4, "Sapiens", BookStatus.Finished), previewLoan),
                        column = 0,
                        onBookClick = {},
                    )
                }
            }
        }
    }
}

// Plan section 5: 2x font scale at a 320dp width - chips stay single-line/scrollable, the grid
// title and card title wrap without clipping.
@Preview(name = "LibraryGridSection — 2x font scale, 320dp", fontScale = 2f, widthDp = 320)
@Composable
private fun LibraryGridSectionDoubleFontScalePreview() {
    BookshelfTheme {
        Surface {
            Column {
                LibraryFilterChips(
                    selected = LibraryFilter.Finished,
                    counts = LibraryCounts(all = 27, reading = 2, finished = 19, wantToRead = 6, lentOut = 3),
                    onFilterSelected = {},
                )
                LibraryGridTitle()
                LibraryGridCard(
                    libraryBook = LibraryBook(previewBook(1, "Braiding Sweetgrass", BookStatus.Finished), previewLoan),
                    column = 0,
                    onBookClick = {},
                )
            }
        }
    }
}

// Plan section 5: the "Lent out" filter selected.
@Preview(name = "LibraryGridSection — Lent out selected")
@Composable
private fun LibraryGridSectionLentOutSelectedPreview() {
    BookshelfTheme {
        Surface {
            Column {
                LibraryFilterChips(
                    selected = LibraryFilter.LentOut,
                    counts = LibraryCounts(all = 27, reading = 2, finished = 19, wantToRead = 6, lentOut = 3),
                    onFilterSelected = {},
                )
                LibraryGridTitle()
                LibraryGridCard(
                    libraryBook = LibraryBook(previewBook(1, "Sapiens", BookStatus.Finished), previewLoan),
                    column = 0,
                    onBookClick = {},
                )
                Spacer(modifier = Modifier.height(1.dp))
            }
        }
    }
}
