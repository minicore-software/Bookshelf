package com.minicore.bookshelf.features.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.features.library.domain.model.LibraryBook

/** The grid card's dot kind (plan G6). `Lent` is a visual overlay, not a fourth [BookStatus]. */
enum class StatusDotKind { Reading, Finished, WantToRead, Lent }

/**
 * The dot/caption rule (plan G6): a book with an open loan always gets the [StatusDotKind.Lent]
 * dot, under every chip - not only "Lent out". `when` is exhaustive over [BookStatus] so a new
 * status is a compile error.
 */
internal fun LibraryBook.statusDotKind(): StatusDotKind = if (openLoan != null) {
    StatusDotKind.Lent
} else {
    book.status.toStatusDotKind()
}

private fun BookStatus.toStatusDotKind(): StatusDotKind = when (this) {
    BookStatus.Reading -> StatusDotKind.Reading
    BookStatus.Finished -> StatusDotKind.Finished
    BookStatus.WantToRead -> StatusDotKind.WantToRead
}

/** Test-only hook so component tests can assert the rendered dot kind without reading pixels. */
val LibraryStatusDotKindKey = SemanticsPropertyKey<StatusDotKind>("LibraryStatusDotKind")
var SemanticsPropertyReceiver.libraryStatusDotKind by LibraryStatusDotKindKey

private val DotOuterSize = 13.dp
private val DotRingThickness = 2.dp

@Composable
private fun BookStatus.label(): String = when (this) {
    BookStatus.Reading -> stringResource(R.string.library_status_reading)
    BookStatus.Finished -> stringResource(R.string.library_status_finished)
    BookStatus.WantToRead -> stringResource(R.string.library_status_want_to_read)
}

/**
 * The 13dp status/lent dot (plan G6/G12): a status dot is a coloured fill inside a paper ring;
 * the lent overlay inverts that - a paper (hollow) fill inside a terracotta ring. The
 * `contentDescription` is always [status]'s label, even when [kind] is [StatusDotKind.Lent]
 * (plan G6) - lent-ness is carried by the "Lent to X" caption text, never by colour alone.
 */
@Composable
fun LibraryStatusDot(kind: StatusDotKind, status: BookStatus, modifier: Modifier = Modifier) {
    val extended = BookshelfTheme.extendedColors
    val paper = MaterialTheme.colorScheme.surface
    val (fill, ring) = when (kind) {
        StatusDotKind.Reading -> extended.statusReading to paper
        StatusDotKind.Finished -> extended.statusFinished to paper
        StatusDotKind.WantToRead -> extended.statusWantToRead to paper
        StatusDotKind.Lent -> paper to extended.lentAccent
    }
    val statusLabel = status.label()

    Box(
        modifier = modifier
            .size(DotOuterSize)
            .clip(CircleShape)
            .background(ring)
            .semantics {
                contentDescription = statusLabel
                libraryStatusDotKind = kind
            }
            .padding(DotRingThickness)
            .clip(CircleShape)
            .background(fill),
    )
}
