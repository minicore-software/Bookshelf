package com.minicore.bookshelf.features.library.ui.bookdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.designsystem.component.BookCover
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.features.library.domain.model.BookDetail
import com.minicore.bookshelf.features.library.domain.model.BookDetailLoan
import com.minicore.bookshelf.features.library.ui.loanDueCaption
import kotlin.math.cos
import kotlin.math.sin

const val BOOK_DETAIL_SHEET_TEST_TAG = "book_detail_sheet"
const val BOOK_DETAIL_SCRIM_TEST_TAG = "book_detail_scrim"
const val BOOK_DETAIL_CLOSE_ICON_TEST_TAG = "book_detail_close_icon"
const val BOOK_DETAIL_CLOSE_BUTTON_TEST_TAG = "book_detail_close_button"
const val BOOK_DETAIL_EDIT_BUTTON_TEST_TAG = "book_detail_edit_button"
const val BOOK_DETAIL_LEND_BUTTON_TEST_TAG = "book_detail_lend_button"
const val BOOK_DETAIL_STATUS_PILL_TEST_TAG = "book_detail_status_pill"
const val BOOK_DETAIL_RATING_TEST_TAG = "book_detail_rating"
const val BOOK_DETAIL_COMMENT_TEST_TAG = "book_detail_comment"
const val BOOK_DETAIL_MEMORY_WHY_TEST_TAG = "book_detail_memory_why"
const val BOOK_DETAIL_MEMORY_WHERE_TEST_TAG = "book_detail_memory_where"
const val BOOK_DETAIL_MEMORY_FEELING_TEST_TAG = "book_detail_memory_feeling"
const val BOOK_DETAIL_LENT_CAPTION_TEST_TAG = "book_detail_lent_caption"

/**
 * Stateful entry point for the read-only Book Detail modal (plan #32). Hosted by
 * `navigation/BookshelfNavGraph.kt`'s `dialog(...)` destination. [BookDetailUiState.NotFound]
 * (a stale back-stack id, or a book deleted while this modal is open - QA F17) closes the modal
 * itself exactly once, through [onClose].
 */
@Composable
fun BookDetailRoute(
    onClose: () -> Unit,
    onEditClick: (bookId: Long) -> Unit,
    onLendClick: (bookId: Long) -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onClose)

    when (val state = uiState) {
        BookDetailUiState.Loading -> Unit
        BookDetailUiState.NotFound -> LaunchedEffect(Unit) { onClose() }
        is BookDetailUiState.Loaded -> {
            BookDetailSheet(
                detail = state.detail,
                onClose = onClose,
                onEditClick = { onEditClick(state.detail.book.id) },
                onLendClick = { onLendClick(state.detail.book.id) },
            )
        }
    }
}

@Composable
private fun BookDetailSheet(
    detail: BookDetail,
    onClose: () -> Unit,
    onEditClick: () -> Unit,
    onLendClick: () -> Unit,
) {
    val sheetTitle = stringResource(R.string.book_detail_title)
    val closeIconDescription = stringResource(R.string.book_detail_close_icon)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val maxHeight = (maxHeight - statusBarInset) * 0.92f

        Box(
            modifier = Modifier
                .testTag(BOOK_DETAIL_SCRIM_TEST_TAG)
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clearAndSetSemantics {}
                .clickable(onClick = onClose),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .testTag(BOOK_DETAIL_SHEET_TEST_TAG)
                .semantics { paneTitle = sheetTitle },
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sheetTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .testTag(BOOK_DETAIL_CLOSE_ICON_TEST_TAG)
                            .semantics { contentDescription = closeIconDescription },
                    ) {
                        Text("✕", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    BookHeader(detail.book, detail.loan != null)
                    detail.book.comment?.let { CommentBlock(it) }
                    AvailabilityBlock(loan = detail.loan, onLendClick = onLendClick)
                    MemorySection(detail.book)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onClose, modifier = Modifier.testTag(BOOK_DETAIL_CLOSE_BUTTON_TEST_TAG)) {
                        Text(stringResource(R.string.book_detail_close))
                    }
                    Button(
                        onClick = onEditClick,
                        modifier = Modifier.padding(start = 10.dp).testTag(BOOK_DETAIL_EDIT_BUTTON_TEST_TAG),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // The glyph is decorative - clearAndSetSemantics {} keeps it out of
                            // the Button's merged accessible label, so TalkBack announces just
                            // "Edit", not "pencil Edit" (top suggestion, review round 1).
                            Text(
                                text = stringResource(R.string.book_detail_edit_glyph),
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                            Text(stringResource(R.string.book_detail_edit))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookHeader(book: Book, hasOpenLoan: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Decorative here (BookshelfTheme.extendedColors titleLarge Text right next to it already
        // carries the title to TalkBack) - without this, BookCover's own Row-variant title overlay
        // (drawn whenever there is no decoded bitmap, i.e. always for a Cover.Preset) announces
        // the same title a second time.
        BookCover(
            bookId = book.id,
            title = book.title,
            cover = book.cover,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = book.title, style = MaterialTheme.typography.titleLarge)
            val authorYearFormat = stringResource(R.string.book_detail_author_year)
            authorYearLine(book.author, book.publishedYear, authorYearFormat)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = BookshelfTheme.extendedColors.inkSoft)
            }
            ReadOnlyStarRating(rating = book.rating)
            StatusPill(status = bookDetailStatus(hasOpenLoan))
        }
    }
}

@Composable
private fun ReadOnlyStarRating(rating: Int?, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.book_detail_rating_label)
    val notRatedText = stringResource(R.string.book_detail_rating_not_rated)
    val ratedFormat = stringResource(R.string.book_detail_rating_rated)
    val description = ratingAccessibilityLabel(rating, notRatedText, ratedFormat)

    Row(
        modifier = modifier
            .testTag(BOOK_DETAIL_RATING_TEST_TAG)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = description
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val terracotta = BookshelfTheme.extendedColors.lentAccent
        for (n in 1..5) {
            val filled = rating != null && n <= rating
            val tint = if (filled) terracotta else terracotta.copy(alpha = 0.25f)
            Canvas(modifier = Modifier.size(18.dp)) {
                drawDetailStar(color = tint, filled = filled)
            }
        }
    }
}

private fun DrawScope.drawDetailStar(color: Color, filled: Boolean) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = size.minDimension / 2f
    val innerRadius = outerRadius * 0.4f
    val path = Path().apply {
        fillType = PathFillType.NonZero
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val angle = (-90.0 + i * 36.0) * (Math.PI / 180.0)
            val point = Offset(center.x + (radius * cos(angle)).toFloat(), center.y + (radius * sin(angle)).toFloat())
            if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }
    if (filled) drawPath(path, color = color) else drawPath(path, color = color, style = Stroke(width = 1.2.dp.toPx()))
}

@Composable
private fun StatusPill(status: BookDetailStatus, modifier: Modifier = Modifier) {
    val label = when (status) {
        BookDetailStatus.OnTheShelf -> stringResource(R.string.book_detail_status_on_shelf)
        BookDetailStatus.LentOut -> stringResource(R.string.book_detail_status_lent_out)
    }
    Row(
        modifier = modifier
            .testTag(BOOK_DETAIL_STATUS_PILL_TEST_TAG)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(status)
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatusDot(status: BookDetailStatus, modifier: Modifier = Modifier) {
    val extended = BookshelfTheme.extendedColors
    val paper = MaterialTheme.colorScheme.surface
    val (fill, ring) = when (status) {
        BookDetailStatus.OnTheShelf -> MaterialTheme.colorScheme.primary to paper
        BookDetailStatus.LentOut -> paper to extended.lentAccent
    }
    Box(
        modifier = modifier
            .size(13.dp)
            .clip(CircleShape)
            .background(ring)
            .padding(2.dp)
            .clip(CircleShape)
            .background(fill),
    )
}

@Composable
private fun CommentBlock(comment: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.book_detail_comment_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = BookshelfTheme.extendedColors.inkSoft,
            )
            Text(
                text = stringResource(R.string.book_detail_comment_quoted, comment),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                modifier = Modifier.testTag(BOOK_DETAIL_COMMENT_TEST_TAG),
            )
        }
    }
}

@Composable
private fun AvailabilityBlock(loan: BookDetailLoan?, onLendClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(12.dp)) {
        if (loan == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.book_detail_status_on_shelf).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = BookshelfTheme.extendedColors.inkSoft,
                    )
                    Text(text = stringResource(R.string.book_detail_availability_prompt))
                }
                Button(onClick = onLendClick, modifier = Modifier.testTag(BOOK_DETAIL_LEND_BUTTON_TEST_TAG)) {
                    Text(stringResource(R.string.book_detail_lend_out))
                }
            }
        } else {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.book_detail_lent_to_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = BookshelfTheme.extendedColors.inkSoft,
                )
                Text(text = loan.borrowerName, style = MaterialTheme.typography.bodyLarge)
                val captionStyle = if (loan.dueStatus.isOverdue) {
                    MaterialTheme.typography.labelSmall.copy(color = BookshelfTheme.extendedColors.overdue)
                } else {
                    MaterialTheme.typography.labelSmall.copy(color = BookshelfTheme.extendedColors.dueUpcoming)
                }
                Text(
                    text = loanDueCaption(loan.dueStatus),
                    style = captionStyle,
                    modifier = Modifier.testTag(BOOK_DETAIL_LENT_CAPTION_TEST_TAG),
                )
            }
        }
    }
}

@Composable
private fun MemorySection(book: Book) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.book_detail_memory_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        MemoryCell(
            label = stringResource(R.string.book_detail_memory_why),
            value = book.memoryWhy,
            testTag = BOOK_DETAIL_MEMORY_WHY_TEST_TAG,
        )
        MemoryCell(
            label = stringResource(R.string.book_detail_memory_where),
            value = book.memoryWhere,
            testTag = BOOK_DETAIL_MEMORY_WHERE_TEST_TAG,
        )
        MemoryCell(
            label = stringResource(R.string.book_detail_memory_feeling),
            value = book.memoryWhat,
            testTag = BOOK_DETAIL_MEMORY_FEELING_TEST_TAG,
        )
    }
}

@Composable
private fun MemoryCell(label: String, value: String?, testTag: String) {
    val emptyDisplay = stringResource(R.string.book_detail_memory_empty)
    val emptyA11y = stringResource(R.string.book_detail_memory_empty_a11y)
    val description = "$label, ${value ?: emptyA11y}"

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(12.dp)) {
        Column(
            modifier = Modifier
                .testTag(testTag)
                .fillMaxWidth()
                .padding(12.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = BookshelfTheme.extendedColors.inkSoft,
            )
            Text(
                text = value ?: emptyDisplay,
                style = if (value == null) {
                    MaterialTheme.typography.bodyMedium.copy(color = BookshelfTheme.extendedColors.inkSoft)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            )
        }
    }
}
