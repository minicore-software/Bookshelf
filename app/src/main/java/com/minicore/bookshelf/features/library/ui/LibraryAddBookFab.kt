package com.minicore.bookshelf.features.library.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme

const val LIBRARY_ADD_BOOK_FAB_TEST_TAG = "library_add_book_fab"

private val FabSize = 52.dp
private val GlyphSize = 22.dp

/**
 * The "Add a book" FAB (plan E6, BOOKSHELF-0022). Built in this ticket - it did not exist after
 * #20/#21 and no other ticket in epic #17 owns it (plan section 1, 12). [onClick] is a no-op
 * until the Add Book screen ships (epic #17 D9, D18); the stub is wired by
 * [com.minicore.bookshelf.navigation.BookshelfNavGraph].
 */
@Composable
internal fun LibraryAddBookFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.library_add_book)
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(FabSize)
            .testTag(LIBRARY_ADD_BOOK_FAB_TEST_TAG)
            .semantics { contentDescription = label },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
    ) {
        PlusGlyph(tint = LocalContentColor.current)
    }
}

/**
 * A hand-drawn plus glyph, purely decorative (the FAB itself carries the accessible label and
 * click action). Drawn with [Canvas], the same pattern as #19 H6's search glyph, because
 * `material-icons-core` is not a project dependency and this ticket does not add one (plan E6).
 */
@Composable
private fun PlusGlyph(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(GlyphSize)) {
        val strokeWidthPx = 2.dp.toPx()
        val start = size.minDimension * (5f / 24f)
        val end = size.minDimension * (19f / 24f)
        val mid = size.minDimension / 2f
        drawLine(
            color = tint,
            start = Offset(mid, start),
            end = Offset(mid, end),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(start, mid),
            end = Offset(end, mid),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
    }
}

@Preview(name = "LibraryAddBookFab")
@Composable
private fun LibraryAddBookFabPreview() {
    BookshelfTheme {
        Surface {
            LibraryAddBookFab(onClick = {})
        }
    }
}
