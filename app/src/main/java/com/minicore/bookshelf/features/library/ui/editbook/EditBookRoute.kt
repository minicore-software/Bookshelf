package com.minicore.bookshelf.features.library.ui.editbook

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
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
import com.minicore.bookshelf.features.library.ui.bookform.MemorySection
import com.minicore.bookshelf.features.library.ui.removebook.RemoveBookDialog

const val EDIT_BOOK_SHEET_TEST_TAG = "edit_book_sheet"
const val EDIT_BOOK_SCRIM_TEST_TAG = "edit_book_scrim"
const val EDIT_BOOK_CLOSE_BUTTON_TEST_TAG = "edit_book_close_button"
const val EDIT_BOOK_REMOVE_BUTTON_TEST_TAG = "edit_book_remove_button"
const val EDIT_BOOK_CANCEL_BUTTON_TEST_TAG = "edit_book_cancel_button"
const val EDIT_BOOK_SAVE_BUTTON_TEST_TAG = "edit_book_save_button"
const val EDIT_BOOK_DISCARD_DIALOG_TEST_TAG = "edit_book_discard_dialog"
const val EDIT_BOOK_LOADING_TEST_TAG = "edit_book_loading"
const val EDIT_BOOK_REMOVE_FAILED_TEST_TAG = "edit_book_remove_failed"

private val EDIT_BOOK_TEST_TAGS = BookFormSheetTestTags(
    sheet = EDIT_BOOK_SHEET_TEST_TAG,
    scrim = EDIT_BOOK_SCRIM_TEST_TAG,
    closeButton = EDIT_BOOK_CLOSE_BUTTON_TEST_TAG,
)

/**
 * Stateful entry point for the "edit a book" sheet (plan #33 section 4.1, decision E2). Hosted
 * by `navigation/BookshelfNavGraph.kt`'s `dialog(...)` destination, stacked on top of Book
 * Detail. Owns the "Remove book" confirm flow in-sheet (plan #34 decision R5): tapping "Remove
 * book" shows [RemoveBookDialog]; confirming runs [EditBookViewModel.onRemoveConfirmed] and the
 * nav graph pops both Edit and Detail on [EditBookResult.Removed].
 */
@Composable
fun EditBookRoute(
    onDone: (EditBookResult) -> Unit,
    viewModel: EditBookViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.onDone.collect { result -> onDone(result) }
    }

    fun requestDismiss() {
        if (uiState.isSaving || uiState.isRemoving || uiState.isImportingImage) return
        if (uiState.isDirty) showDiscardConfirm = true else viewModel.onDismissRequest()
    }

    BackHandler(enabled = !uiState.isSaving && !uiState.isRemoving && !uiState.isImportingImage) { requestDismiss() }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onImagePicked(uri?.toString())
    }
    fun launchImagePicker() {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    EditBookSheet(
        uiState = uiState,
        onTitleChange = viewModel::onTitleChange,
        onAuthorChange = viewModel::onAuthorChange,
        onYearTextChange = viewModel::onYearTextChange,
        onYearStep = viewModel::onYearStep,
        onRatingTap = viewModel::onRatingTap,
        onCommentChange = viewModel::onCommentChange,
        onMemoryWhyChange = viewModel::onMemoryWhyChange,
        onMemoryWhereChange = viewModel::onMemoryWhereChange,
        onMemoryWhatChange = viewModel::onMemoryWhatChange,
        onPresetSelected = viewModel::onPresetSelected,
        onUploadImageClick = ::launchImagePicker,
        onRemoveImageClick = viewModel::onRemoveImage,
        onSave = viewModel::onSave,
        onCancel = ::requestDismiss,
        onClose = ::requestDismiss,
        onScrimClick = ::requestDismiss,
        onRemoveBookClick = { showRemoveConfirm = true },
    )

    if (showDiscardConfirm) {
        BookFormDiscardDialog(
            testTag = EDIT_BOOK_DISCARD_DIALOG_TEST_TAG,
            title = stringResource(R.string.edit_book_discard_title),
            body = stringResource(R.string.edit_book_discard_body),
            keepEditingLabel = stringResource(R.string.edit_book_discard_keep_editing),
            discardLabel = stringResource(R.string.edit_book_discard_confirm),
            onKeepEditing = { showDiscardConfirm = false },
            onDiscard = {
                showDiscardConfirm = false
                viewModel.onDismissRequest()
            },
        )
    }

    if (showRemoveConfirm) {
        // R10: the persisted title, not whatever the user may currently be typing.
        RemoveBookDialog(
            bookTitle = uiState.initialDraft?.title ?: uiState.title,
            borrowerName = uiState.borrowerName,
            onConfirm = {
                showRemoveConfirm = false
                viewModel.onRemoveConfirmed()
            },
            onDismiss = { showRemoveConfirm = false },
        )
    }
}

@Composable
private fun EditBookSheet(
    uiState: EditBookUiState,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onYearTextChange: (String) -> Unit,
    onYearStep: (Int) -> Unit,
    onRatingTap: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onMemoryWhyChange: (String) -> Unit,
    onMemoryWhereChange: (String) -> Unit,
    onMemoryWhatChange: (String) -> Unit,
    onPresetSelected: (CoverPreset) -> Unit,
    onUploadImageClick: () -> Unit,
    onRemoveImageClick: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onScrimClick: () -> Unit,
    onRemoveBookClick: () -> Unit,
) {
    val enabled = uiState.isLoaded && !uiState.isSaving && !uiState.isRemoving && !uiState.isImportingImage

    BookFormSheet(
        title = stringResource(R.string.edit_book_title),
        closeContentDescription = stringResource(R.string.edit_book_close),
        testTags = EDIT_BOOK_TEST_TAGS,
        onClose = onClose,
        onScrimClick = onScrimClick,
        errorSlot = when (uiState.error) {
            EditBookError.SaveFailed -> {
                {
                    Text(
                        text = stringResource(R.string.edit_book_save_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            EditBookError.RemoveFailed -> {
                {
                    Text(
                        text = stringResource(R.string.edit_book_remove_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .testTag(EDIT_BOOK_REMOVE_FAILED_TEST_TAG)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            else -> null
        },
        footer = {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onRemoveBookClick,
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(EDIT_BOOK_REMOVE_BUTTON_TEST_TAG),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.edit_book_remove))
                }
                FlowRow(horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onCancel,
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp).testTag(EDIT_BOOK_CANCEL_BUTTON_TEST_TAG),
                    ) {
                        Text(stringResource(R.string.edit_book_cancel))
                    }
                    Button(
                        onClick = onSave,
                        enabled = uiState.canSave,
                        modifier = Modifier.padding(start = 10.dp).heightIn(min = 48.dp).testTag(EDIT_BOOK_SAVE_BUTTON_TEST_TAG),
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
                        Text(stringResource(R.string.edit_book_save))
                    }
                }
            }
        },
    ) {
        if (!uiState.isLoaded) {
            Box(modifier = Modifier.fillMaxWidth().testTag(EDIT_BOOK_LOADING_TEST_TAG), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@BookFormSheet
        }

        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
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

            if (uiState.error == EditBookError.ImageImportFailed) {
                Text(
                    text = stringResource(R.string.edit_book_cover_import_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            MemorySection(
                memoryWhy = uiState.memoryWhy,
                onMemoryWhyChange = onMemoryWhyChange,
                memoryWhere = uiState.memoryWhere,
                onMemoryWhereChange = onMemoryWhereChange,
                memoryWhat = uiState.memoryWhat,
                onMemoryWhatChange = onMemoryWhatChange,
                enabled = enabled,
            )

            BorrowingSection(isLentOut = uiState.isLentOut)
        }
    }
}
