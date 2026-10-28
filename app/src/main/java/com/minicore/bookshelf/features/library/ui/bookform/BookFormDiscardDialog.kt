package com.minicore.bookshelf.features.library.ui.bookform

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The "discard your changes?" confirm dialog shared by Add and Edit (plan #33 decision E9).
 * Copy is passed in - Add keeps its own strings, Edit uses its own (plan section 3.8) - so this
 * stays a pure chrome extraction with no new copy decisions.
 */
@Composable
fun BookFormDiscardDialog(
    testTag: String,
    title: String,
    body: String,
    keepEditingLabel: String,
    discardLabel: String,
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(testTag),
        onDismissRequest = onKeepEditing,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onClick = onDiscard,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(discardLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) { Text(keepEditingLabel) }
        },
    )
}
