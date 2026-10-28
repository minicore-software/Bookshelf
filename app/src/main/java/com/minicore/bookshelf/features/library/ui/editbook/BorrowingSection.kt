package com.minicore.bookshelf.features.library.ui.editbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme

const val BORROWING_SECTION_TEST_TAG = "edit_book_borrowing_section"
const val BORROWING_CHECKBOX_ROW_TEST_TAG = "edit_book_borrowing_checkbox_row"

/**
 * The read-only "currently lent out" indicator (plan #33 section 3.5, decision E8/D13). Live:
 * [isLentOut] comes straight from `EditBookViewModel`'s `ObserveBookDetailUseCase` collection, so
 * a loan opening or closing while the sheet is open flips it. There is no `onCheckedChange` and
 * no `toggleable`/`clickable` modifier anywhere in this row - it is a display, never a control
 * (FF-E3, AC3).
 */
@Composable
fun BorrowingSection(isLentOut: Boolean, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.edit_book_borrowing_label)
    val helper = stringResource(R.string.edit_book_borrowing_helper)

    Column(modifier = modifier.testTag(BORROWING_SECTION_TEST_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.edit_book_borrowing_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            modifier = Modifier
                .testTag(BORROWING_CHECKBOX_ROW_TEST_TAG)
                .semantics(mergeDescendants = true) {
                    role = Role.Checkbox
                    toggleableState = if (isLentOut) ToggleableState.On else ToggleableState.Off
                    disabled()
                    contentDescription = "$label. $helper"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = isLentOut, onCheckedChange = null, enabled = false)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Text(text = helper, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "BorrowingSection — not lent")
@Composable
private fun BorrowingSectionPreview() {
    BookshelfTheme { BorrowingSection(isLentOut = false) }
}

@androidx.compose.ui.tooling.preview.Preview(name = "BorrowingSection — lent")
@Composable
private fun BorrowingSectionLentPreview() {
    BookshelfTheme { BorrowingSection(isLentOut = true) }
}
