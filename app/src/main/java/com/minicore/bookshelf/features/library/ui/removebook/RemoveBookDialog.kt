package com.minicore.bookshelf.features.library.ui.removebook

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme

const val REMOVE_BOOK_DIALOG_TEST_TAG = "remove_book_dialog"
const val REMOVE_BOOK_OPEN_LOAN_WARNING_TEST_TAG = "remove_book_open_loan_warning"
const val REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG = "remove_book_confirm_button"
const val REMOVE_BOOK_CANCEL_BUTTON_TEST_TAG = "remove_book_cancel_button"

/**
 * The shared "remove this book?" confirm dialog (plan #34 section 4.3, decision R4), reused
 * unchanged by Edit (#34) and the Library grid (#35). Stateless, no ViewModel. Shows an
 * additional open-loan warning paragraph when [borrowerName] is non-null (decision R10); either
 * way, [onConfirm] proceeds without blocking (AC2 - the warning never disables Confirm).
 */
@Composable
fun RemoveBookDialog(
    bookTitle: String,
    borrowerName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(REMOVE_BOOK_DIALOG_TEST_TAG),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remove_book_title, bookTitle)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.remove_book_body))
                if (borrowerName != null) {
                    Text(
                        text = stringResource(R.string.remove_book_open_loan_warning, borrowerName),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(REMOVE_BOOK_OPEN_LOAN_WARNING_TEST_TAG),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag(REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.remove_book_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(REMOVE_BOOK_CANCEL_BUTTON_TEST_TAG),
            ) {
                Text(stringResource(R.string.remove_book_cancel))
            }
        },
    )
}

@Preview(name = "RemoveBookDialog - no open loan")
@Composable
private fun RemoveBookDialogNoLoanPreview() {
    BookshelfTheme {
        RemoveBookDialog(bookTitle = "Dune", borrowerName = null, onConfirm = {}, onDismiss = {})
    }
}

@Preview(name = "RemoveBookDialog - open loan")
@Composable
private fun RemoveBookDialogOpenLoanPreview() {
    BookshelfTheme {
        RemoveBookDialog(bookTitle = "Dune", borrowerName = "Alex", onConfirm = {}, onDismiss = {})
    }
}
