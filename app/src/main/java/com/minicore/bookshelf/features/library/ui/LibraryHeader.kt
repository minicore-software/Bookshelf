package com.minicore.bookshelf.features.library.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme

const val LIBRARY_HEADER_TEST_TAG = "library_header"
const val LIBRARY_BRAND_MARK_TEST_TAG = "library_brand_mark"
const val LIBRARY_SEARCH_BAR_TEST_TAG = "library_search_bar"

private val BrandMarkSize = 38.dp
private val HeaderHorizontalPadding = 20.dp
private val SearchBarShape = RoundedCornerShape(14.dp)

/**
 * The Library screen's header: brand-mark/title/tagline wordmark, plus a stub search bar below
 * it (plan `docs/plans/19-library-header-wordmark.md`, decisions H2-H9). Composition, proportions
 * and copy follow `Landing_Screen_Sample_Title.png`; typeface and colour come from the CLAUDE.md
 * tokens (Fraunces/Inter, paper/ink/forest), not the PNG's own slab/sage/cream (H1).
 *
 * @param onSearchClick invoked when the search bar is tapped. The search bar itself is a
 *   clickable, field-styled [Row] (H6) — not a [androidx.compose.foundation.text.BasicTextField]
 *   or an `M3 SearchBar` — so tapping it never takes focus or opens the IME; it is a stub for a
 *   later search ticket.
 */
@Composable
fun LibraryHeader(onSearchClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .testTag(LIBRARY_HEADER_TEST_TAG)
            .padding(start = HeaderHorizontalPadding, top = 28.dp, end = HeaderHorizontalPadding, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Wordmark()
        LibrarySearchBar(onClick = onSearchClick)
    }
}

@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark()
        Column(
            modifier = Modifier.padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.library_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .testTag(LIBRARY_BRAND_MARK_TEST_TAG)
            .size(BrandMarkSize)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        // A fixed, non-scaling glyph: this is a decorative logotype inside a fixed-size tile,
        // not real text, so it must never overflow/sink in the tile at large font scales (plan
        // H9, risk R3). Pinning fontScale to 1f (not just fontSize) also pins lineHeight, which
        // otherwise still scales with the system font size and pushes the glyph off-center. The
        // surrounding real text (title, tagline, placeholder) scales normally.
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 1f)) {
            Text(
                text = stringResource(R.string.brand_mark_initial),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun LibrarySearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clickLabel = stringResource(R.string.library_search_click_label)
    Row(
        modifier = modifier
            .testTag(LIBRARY_SEARCH_BAR_TEST_TAG)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, SearchBarShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SearchBarShape)
            .clip(SearchBarShape)
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SearchGlyph(tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = stringResource(R.string.library_search_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A hand-drawn magnifier glyph, purely decorative (the search bar [Row] itself carries the
 * accessible label and click action). Drawn with [Canvas] rather than
 * `Icons.Outlined.Search`/`material-icons-core` because that artifact is not a project dependency
 * and this ticket does not add one (plan H6 alternative).
 */
@Composable
private fun SearchGlyph(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier = modifier.size(18.dp)) {
        val strokeWidthPx = 1.8.dp.toPx()
        val lensRadius = size.minDimension * 0.32f
        val lensCenter = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(
            color = tint,
            radius = lensRadius,
            center = lensCenter,
            style = Stroke(width = strokeWidthPx),
        )
        val direction = Offset(1f, 1f)
        val handleStart = lensCenter + direction * (lensRadius * 0.75f)
        val handleEnd = Offset(size.width * 0.92f, size.height * 0.92f)
        drawLine(
            color = tint,
            start = handleStart,
            end = handleEnd,
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
    }
}

@Preview(name = "Library header")
@Composable
private fun LibraryHeaderPreview() {
    BookshelfTheme {
        LibraryHeader(onSearchClick = {})
    }
}

@Preview(name = "Library header — 2x font scale", fontScale = 2f)
@Composable
private fun LibraryHeaderFontScalePreview() {
    BookshelfTheme {
        LibraryHeader(onSearchClick = {})
    }
}

@Preview(name = "Library header — 320dp width", widthDp = 320)
@Composable
private fun LibraryHeaderNarrowPreview() {
    BookshelfTheme {
        LibraryHeader(onSearchClick = {})
    }
}
