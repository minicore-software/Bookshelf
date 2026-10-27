package com.minicore.bookshelf.features.library.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.designsystem.LibraryTextStyles
import com.minicore.bookshelf.core.designsystem.component.BookCover
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.features.library.domain.LentOutRow
import com.minicore.bookshelf.features.library.domain.LoanDueStatus
import java.time.format.DateTimeFormatter

const val LIBRARY_READING_ROW_TEST_TAG = "library_reading_row"
const val LIBRARY_LENT_ROW_TEST_TAG = "library_lent_row"
const val LIBRARY_SEE_ALL_LOANS_TEST_TAG = "library_see_all_loans"

fun libraryReadingCardTag(bookId: Long) = "library_reading_card_$bookId"
fun libraryLentCardTag(bookId: Long) = "library_lent_card_$bookId"
fun libraryLentCaptionTag(bookId: Long) = "library_lent_caption_$bookId"

private val CardWidth = 132.dp

/** "Currently reading" row: covers and titles only, no progress UI (AC1, plan R10/D11/D16). */
@Composable
fun CurrentlyReadingSection(
    books: List<Book>,
    onBookClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = stringResource(R.string.library_currently_reading_title),
            trailing = pluralStringResource(R.plurals.library_reading_count, books.size, books.size),
            topPadding = 16.dp,
        )
        LazyRow(
            modifier = Modifier.testTag(LIBRARY_READING_ROW_TEST_TAG),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(books, key = { it.id }) { book ->
                ReadingCard(book = book, onClick = { onBookClick(book.id) })
            }
        }
    }
}

/** "Lent out" row: dimmed cover, borrower, colour-coded due/overdue caption (AC2, plan R10). */
@Composable
fun LentOutSection(
    rows: List<LentOutRow>,
    onBookClick: (Long) -> Unit,
    onSeeAllLoansClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = stringResource(R.string.library_lent_out_title),
            trailing = stringResource(R.string.library_lent_out_see_all, rows.size),
            topPadding = 18.dp,
            onTrailingClick = onSeeAllLoansClick,
        )
        LazyRow(
            modifier = Modifier.testTag(LIBRARY_LENT_ROW_TEST_TAG),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(rows, key = { it.book.id }) { row ->
                LentCard(row = row, onClick = { onBookClick(row.book.id) })
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    trailing: String,
    topPadding: Dp,
    onTrailingClick: (() -> Unit)? = null,
) {
    val seeAllClickLabel = stringResource(R.string.library_see_all_click_label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = topPadding, end = 20.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline().semantics { heading() },
        )
        if (onTrailingClick != null) {
            // A Box passes up its single child's FirstBaseline, so alignByBaseline here lines
            // the "See all" text up with the title's baseline (plan R10: align-items: baseline)
            // while still keeping the 48dp touch target around it.
            Box(
                modifier = Modifier
                    .testTag(LIBRARY_SEE_ALL_LOANS_TEST_TAG)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .alignByBaseline()
                    .clickable(
                        onClickLabel = seeAllClickLabel,
                        role = Role.Button,
                        onClick = onTrailingClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Composable
private fun ReadingCard(book: Book, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clickLabel = stringResource(R.string.library_book_click_label)
    Column(
        modifier = modifier
            .testTag(libraryReadingCardTag(book.id))
            .width(CardWidth)
            .semantics(mergeDescendants = true) {}
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BookCover(bookId = book.id, title = book.title, cover = book.cover)
    }
}

@Composable
private fun LentCard(row: LentOutRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clickLabel = stringResource(R.string.library_book_click_label)
    Column(
        modifier = modifier
            .testTag(libraryLentCardTag(row.book.id))
            .width(CardWidth)
            .semantics(mergeDescendants = true) {}
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BookCover(bookId = row.book.id, title = row.book.title, cover = row.book.cover, dimmed = true)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.library_loan_borrower, row.borrowerName),
                style = LibraryTextStyles.borrower,
                color = BookshelfTheme.extendedColors.inkSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val captionStyle = if (row.dueStatus.isOverdue) {
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = BookshelfTheme.extendedColors.overdue,
                )
            } else {
                MaterialTheme.typography.labelSmall.copy(
                    color = BookshelfTheme.extendedColors.dueUpcoming,
                )
            }
            Text(
                text = loanDueCaption(row.dueStatus),
                style = captionStyle,
                modifier = Modifier.testTag(libraryLentCaptionTag(row.book.id)),
            )
        }
    }
}

/** The due/overdue caption text, per the exact rule in plan R4. */
@Composable
fun loanDueCaption(status: LoanDueStatus): String {
    return when (status) {
        is LoanDueStatus.Overdue -> pluralStringResource(
            R.plurals.library_loan_overdue,
            status.days.toInt(),
            status.days.toInt(),
        )
        LoanDueStatus.DueToday -> stringResource(R.string.library_loan_due_today)
        is LoanDueStatus.DueSoon -> pluralStringResource(
            R.plurals.library_loan_due_in,
            status.days.toInt(),
            status.days.toInt(),
        )
        is LoanDueStatus.DueLater -> {
            val locale = LocalLocale.current.platformLocale
            val skeleton = if (status.showYear) "MMMdyyyy" else "MMMd"
            val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
            val formatted = DateTimeFormatter.ofPattern(pattern, locale).format(status.dueAt)
            stringResource(R.string.library_loan_due_on, formatted)
        }
    }
}
