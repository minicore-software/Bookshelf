package com.minicore.bookshelf.features.library.ui.bookform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme

const val YEAR_STEPPER_FIELD_TEST_TAG = "year_stepper_field"
const val YEAR_STEPPER_INCREASE_TEST_TAG = "year_stepper_increase"
const val YEAR_STEPPER_DECREASE_TEST_TAG = "year_stepper_decrease"

private val ChevronTouchTarget = 44.dp

/**
 * Optional publication year (plan #31 section 4.2, UX spec section 4): digits-only, max 4
 * characters, with stepper chevrons. The first tap on an empty field (or a field showing "0")
 * seeds the current year rather than stepping; later taps increment/decrement, clamped to
 * `1..9999`. TalkBack gets two custom actions in addition to direct text entry.
 */
@Composable
fun YearStepperField(
    yearText: String,
    onYearTextChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val increaseLabel = stringResource(R.string.add_book_year_increase)
    val decreaseLabel = stringResource(R.string.add_book_year_decrease)

    Row(
        modifier = modifier.testTag(YEAR_STEPPER_FIELD_TEST_TAG),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = yearText,
            onValueChange = onYearTextChange,
            // The custom actions live on the text field's own semantics, not an ancestor Row -
            // a Row with two clickable chevron children is not a semantics leaf, so TalkBack
            // would never actually stop on a Row-level action (same bug class as StarRatingInput).
            modifier = Modifier.weight(1f).semantics {
                if (enabled) {
                    customActions = listOf(
                        CustomAccessibilityAction(increaseLabel) { onStep(1); true },
                        CustomAccessibilityAction(decreaseLabel) { onStep(-1); true },
                    )
                }
            },
            label = { Text(stringResource(R.string.add_book_field_year)) },
            placeholder = { Text(stringResource(R.string.add_book_field_year_placeholder)) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = enabled,
        )
        Column {
            StepperChevron(
                testTag = YEAR_STEPPER_INCREASE_TEST_TAG,
                contentDescription = increaseLabel,
                pointingUp = true,
                enabled = enabled,
                onClick = { onStep(1) },
            )
            StepperChevron(
                testTag = YEAR_STEPPER_DECREASE_TEST_TAG,
                contentDescription = decreaseLabel,
                pointingUp = false,
                enabled = enabled,
                onClick = { onStep(-1) },
            )
        }
    }
}

@Composable
private fun StepperChevron(
    testTag: String,
    contentDescription: String,
    pointingUp: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = Modifier
            .testTag(testTag)
            .size(ChevronTouchTarget)
            .clickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
    ) {
        val strokeWidthPx = 1.6.dp.toPx()
        val halfWidth = size.minDimension * 0.18f
        val yOffset = size.minDimension * 0.12f
        val cy = size.height / 2f
        val cx = size.width / 2f
        val (leftY, rightY) = if (pointingUp) cy + yOffset to cy + yOffset else cy - yOffset to cy - yOffset
        val tipY = if (pointingUp) cy - yOffset else cy + yOffset
        drawLine(tint, Offset(cx - halfWidth, leftY), Offset(cx, tipY), strokeWidthPx, StrokeCap.Round)
        drawLine(tint, Offset(cx + halfWidth, rightY), Offset(cx, tipY), strokeWidthPx, StrokeCap.Round)
    }
}

@Preview(name = "YearStepperField — empty")
@Composable
private fun YearStepperFieldPreview() {
    BookshelfTheme { YearStepperField(yearText = "", onYearTextChange = {}, onStep = {}) }
}

@Preview(name = "YearStepperField — filled")
@Composable
private fun YearStepperFieldFilledPreview() {
    BookshelfTheme { YearStepperField(yearText = "1965", onYearTextChange = {}, onStep = {}) }
}
