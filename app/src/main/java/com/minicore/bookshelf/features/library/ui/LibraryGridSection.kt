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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.designsystem.LibraryTextStyles
import com.minicore.bookshelf.core.designsystem.component.BookCover
import com.minicore.bookshelf.core.designsystem.component.BookCoverVariant
import com.minicore.bookshelf.features.library.domain.LibraryFilter
import com.minicore.bookshelf.features.library.domain.countFor
import com.minicore.bookshelf.features.library.domain.model.LibraryBook
import com.minicore.bookshelf.features.library.domain.model.LibraryCounts

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

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (!selected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                } else {
                    Modifier
                },
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = textStyle, color = textColor, maxLines = 1, softWrap = false)
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

    Column(
        modifier = modifier
            .padding(gridCellPadding(column))
            .clickable(onClickLabel = clickLabel, role = Role.Button) { onBookClick(book.id) }
            .semantics(mergeDescendants = true) {}
            .testTag(libraryGridCardTag(book.id)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            BookCover(
                bookId = book.id,
                title = book.title,
                coverUri = book.coverUri,
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
        val openLoan = libraryBook.openLoan
        if (openLoan != null) {
            Text(
                text = stringResource(R.string.library_grid_lent_to, openLoan.borrowerName),
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
