package com.minicore.bookshelf.features.library.ui.bookform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import kotlin.math.cos
import kotlin.math.sin

const val STAR_RATING_INPUT_TEST_TAG = "star_rating_input"
const val STAR_RATING_STAR_TEST_TAG_PREFIX = "star_rating_star_"

private val StarTouchTarget = 32.dp
private val StarGlyphSize = 22.dp
private const val UNSET_ALPHA = 0.25f

/**
 * Optional 1-5 rating (plan #31 decision A6/D8, UX spec section 5). Unset ([rating] `== null`)
 * is a third, visually and semantically distinct state from "1 star" (AC3) - an outline star at
 * 25% `terracotta` alpha versus a fully-opaque filled star, never confusable.
 *
 * Exposed as one composite accessibility node ("Rating", `stateDescription` "No rating" or
 * "N of 5 stars") with custom actions to set/clear each value, per the UX spec's recommendation
 * that TalkBack users not have to precision-tap 5 adjacent 32dp targets.
 *
 * @param rating `null` = unset, else `1..5`.
 * @param onRatingTap tapping the currently-set star clears it (plan onRatingTap rule).
 */
@Composable
fun StarRatingInput(rating: Int?, onRatingTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val stateDescriptionText = if (rating == null) {
        stringResource(R.string.add_book_rating_unset)
    } else {
        pluralStringResource(R.plurals.add_book_rating_set, rating, rating)
    }
    val contentDescriptionText = stringResource(R.string.add_book_rating_label)
    val clearLabel = stringResource(R.string.add_book_rating_clear_action)

    Row(
        modifier = modifier
            .testTag(STAR_RATING_INPUT_TEST_TAG)
            .semantics {
                contentDescription = contentDescriptionText
                stateDescription = stateDescriptionText
                customActions = buildList {
                    for (n in 1..5) {
                        add(CustomAccessibilityAction(label = "Set rating to $n") { onRatingTap(n); true })
                    }
                    if (rating != null) {
                        add(CustomAccessibilityAction(label = clearLabel) { onRatingTap(rating); true })
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (n in 1..5) {
            val filled = rating != null && n <= rating
            val tint = starColor(filled)
            Canvas(
                modifier = Modifier
                    .testTag(STAR_RATING_STAR_TEST_TAG_PREFIX + n)
                    .size(StarTouchTarget)
                    .selectable(selected = filled, onClick = { onRatingTap(n) }),
            ) {
                drawStar(color = tint, glyphSizePx = StarGlyphSize.toPx(), filled = filled)
            }
        }
    }
}

@Composable
private fun starColor(filled: Boolean): Color {
    val terracotta = BookshelfTheme.extendedColors.lentAccent
    return if (filled) terracotta else terracotta.copy(alpha = UNSET_ALPHA)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(color: Color, glyphSizePx: Float, filled: Boolean) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = glyphSizePx / 2f
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
    if (filled) {
        drawPath(path, color = color)
    } else {
        drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
    }
}

@Preview(name = "StarRatingInput — unset")
@Composable
private fun StarRatingInputUnsetPreview() {
    BookshelfTheme { StarRatingInput(rating = null, onRatingTap = {}) }
}

@Preview(name = "StarRatingInput — 3 of 5")
@Composable
private fun StarRatingInputSetPreview() {
    BookshelfTheme { StarRatingInput(rating = 3, onRatingTap = {}) }
}
