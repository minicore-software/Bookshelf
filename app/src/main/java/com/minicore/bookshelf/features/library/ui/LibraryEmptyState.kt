package com.minicore.bookshelf.features.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme

const val LIBRARY_EMPTY_STATE_TEST_TAG = "library_empty_state"

private val PanelShape = RoundedCornerShape(22.dp)

/**
 * The first-run "no books" message (plan E3-E5, BOOKSHELF-0022): a full-span panel shown in the
 * grid's body in place of the chip row, title and cards when
 * [LibraryUiState.Content.isLibraryEmpty] is true. Uses only existing palette/type tokens - no
 * new illustration, glyph or CTA (plan E5, the ticket's own note).
 */
@Composable
internal fun LibraryEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(start = 20.dp, top = 8.dp, end = 20.dp)
            .fillMaxWidth()
            .clip(PanelShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PanelShape)
            .padding(horizontal = 20.dp, vertical = 80.dp)
            .testTag(LIBRARY_EMPTY_STATE_TEST_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}

@Preview(name = "LibraryEmptyState")
@Composable
private fun LibraryEmptyStatePreview() {
    BookshelfTheme {
        Surface {
            LibraryEmptyState()
        }
    }
}

@Preview(name = "LibraryEmptyState — 2x font scale, 320dp", fontScale = 2f, widthDp = 320)
@Composable
private fun LibraryEmptyStateDoubleFontScalePreview() {
    BookshelfTheme {
        Surface {
            LibraryEmptyState()
        }
    }
}
