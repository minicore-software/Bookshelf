package com.minicore.bookshelf.features.library.ui.addbook

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.ui.bookform.BookForm
import com.minicore.bookshelf.features.library.ui.bookform.BookFormDiscardDialog
import com.minicore.bookshelf.features.library.ui.bookform.BookFormSheet
import com.minicore.bookshelf.features.library.ui.bookform.BookFormSheetTestTags

const val ADD_BOOK_SHEET_TEST_TAG = "add_book_sheet"
const val ADD_BOOK_SCRIM_TEST_TAG = "add_book_scrim"
const val ADD_BOOK_CLOSE_BUTTON_TEST_TAG = "add_book_close_button"
const val ADD_BOOK_CANCEL_BUTTON_TEST_TAG = "add_book_cancel_button"
const val ADD_BOOK_SAVE_BUTTON_TEST_TAG = "add_book_save_button"
const val ADD_BOOK_DISCARD_DIALOG_TEST_TAG = "add_book_discard_dialog"

private val ADD_BOOK_TEST_TAGS = BookFormSheetTestTags(
    sheet = ADD_BOOK_SHEET_TEST_TAG,
    scrim = ADD_BOOK_SCRIM_TEST_TAG,
    closeButton = ADD_BOOK_CLOSE_BUTTON_TEST_TAG,
)

/**
 * Stateful entry point for the manual "add a book" sheet (plan #31 decisions A1/A9, UX spec
 * section 1-2). Hosted by `navigation/BookshelfNavGraph.kt`'s `dialog(...)` destination. Every
 * exit (Cancel, close, system back, scrim) routes through [AddBookViewModel.onDismissRequest],
 * which decides whether to discard silently or - via this composable's own dirty check - show a
 * confirm dialog first (UX spec section 2).
 *
 * The sheet chrome and the discard dialog are [BookFormSheet]/[BookFormDiscardDialog] (plan #33
 * decision E9) - shared with Edit, behaviour-preserving extraction.
 */
@Composable
fun AddBookRoute(onDone: (AddBookResult) -> Unit, viewModel: AddBookViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.onDone.collect { result -> onDone(result) }
    }

    fun requestDismiss() {
        if (uiState.isSaving) return
        if (uiState.isDirty) showDiscardConfirm = true else viewModel.onDismissRequest()
    }

    BackHandler(enabled = !uiState.isSaving) { requestDismiss() }

    // A5: the system Photo Picker, no storage permission needed; falls back to
    // ACTION_OPEN_DOCUMENT on devices without it (built into the contract itself).
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onImagePicked(uri?.toString())
    }
    fun launchImagePicker() {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    AddBookSheet(
        uiState = uiState,
        onTitleChange = viewModel::onTitleChange,
        onAuthorChange = viewModel::onAuthorChange,
        onYearTextChange = viewModel::onYearTextChange,
        onYearStep = viewModel::onYearStep,
        onRatingTap = viewModel::onRatingTap,
        onCommentChange = viewModel::onCommentChange,
        onPresetSelected = viewModel::onPresetSelected,
        onUploadImageClick = ::launchImagePicker,
        onRemoveImageClick = viewModel::onRemoveImage,
        onSave = viewModel::onSave,
        onCancel = ::requestDismiss,
        onClose = ::requestDismiss,
        onScrimClick = ::requestDismiss,
    )

    if (showDiscardConfirm) {
        BookFormDiscardDialog(
            testTag = ADD_BOOK_DISCARD_DIALOG_TEST_TAG,
            title = stringResource(R.string.add_book_discard_title),
            body = stringResource(R.string.add_book_discard_body),
            keepEditingLabel = stringResource(R.string.add_book_discard_keep_editing),
            discardLabel = stringResource(R.string.add_book_discard_confirm),
            onKeepEditing = { showDiscardConfirm = false },
            onDiscard = {
                showDiscardConfirm = false
                viewModel.onDismissRequest()
            },
        )
    }
}

@Composable
private fun AddBookSheet(
    uiState: AddBookUiState,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onYearTextChange: (String) -> Unit,
    onYearStep: (Int) -> Unit,
    onRatingTap: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onPresetSelected: (CoverPreset) -> Unit,
    onUploadImageClick: () -> Unit,
    onRemoveImageClick: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onScrimClick: () -> Unit,
) {
    val enabled = !uiState.isSaving

    BookFormSheet(
        title = stringResource(R.string.add_book_title),
        closeContentDescription = stringResource(R.string.add_book_close),
        testTags = ADD_BOOK_TEST_TAGS,
        onClose = onClose,
        onScrimClick = onScrimClick,
        errorSlot = if (uiState.error == AddBookError.SaveFailed) {
            {
                Text(
                    text = stringResource(R.string.add_book_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        } else {
            null
        },
        footer = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel, enabled = enabled, modifier = Modifier.testTag(ADD_BOOK_CANCEL_BUTTON_TEST_TAG)) {
                    Text(stringResource(R.string.add_book_cancel))
                }
                Button(
                    onClick = onSave,
                    enabled = uiState.canSave,
                    modifier = Modifier.padding(start = 10.dp).testTag(ADD_BOOK_SAVE_BUTTON_TEST_TAG),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp).heightIn(max = 16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Text(stringResource(R.string.add_book_save))
                }
            }
        },
    ) {
        BookForm(
            title = uiState.title,
            onTitleChange = onTitleChange,
            author = uiState.author,
            onAuthorChange = onAuthorChange,
            yearText = uiState.yearText,
            onYearTextChange = onYearTextChange,
            onYearStep = onYearStep,
            rating = uiState.rating,
            onRatingTap = onRatingTap,
            comment = uiState.comment,
            onCommentChange = onCommentChange,
            cover = uiState.cover,
            isImportingImage = uiState.isImportingImage,
            onPresetSelected = onPresetSelected,
            onUploadImageClick = onUploadImageClick,
            onRemoveImageClick = onRemoveImageClick,
            enabled = enabled,
        )

        if (uiState.error == AddBookError.ImageImportFailed) {
            Text(
                text = stringResource(R.string.add_book_cover_import_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
