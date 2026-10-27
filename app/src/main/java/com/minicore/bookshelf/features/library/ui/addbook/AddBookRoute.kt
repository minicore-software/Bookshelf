package com.minicore.bookshelf.features.library.ui.addbook

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.ui.bookform.BookForm

const val ADD_BOOK_SHEET_TEST_TAG = "add_book_sheet"
const val ADD_BOOK_CLOSE_BUTTON_TEST_TAG = "add_book_close_button"
const val ADD_BOOK_CANCEL_BUTTON_TEST_TAG = "add_book_cancel_button"
const val ADD_BOOK_SAVE_BUTTON_TEST_TAG = "add_book_save_button"
const val ADD_BOOK_DISCARD_DIALOG_TEST_TAG = "add_book_discard_dialog"

/**
 * Stateful entry point for the manual "add a book" sheet (plan #31 decisions A1/A9, UX spec
 * section 1-2). Hosted by `navigation/BookshelfNavGraph.kt`'s `dialog(...)` destination. Every
 * exit (Cancel, close, system back, scrim) routes through [AddBookViewModel.onDismissRequest],
 * which decides whether to discard silently or - via this composable's own dirty check - show a
 * confirm dialog first (UX spec section 2).
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
        DiscardConfirmDialog(
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
    val sheetTitle = stringResource(R.string.add_book_title)
    val closeDescription = stringResource(R.string.add_book_close)
    val enabled = !uiState.isSaving

    // BoxWithConstraints (not LocalConfiguration.screenHeightDp, which is the whole device
    // screen) so the 92% ceiling tracks this composable's own available height - correct in
    // multi-window/freeform, not just full-screen.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // UX spec section 1: the 92% ceiling applies after subtracting the status bar inset, so
        // the sheet never visually touches it (decorFitsSystemWindows = false means nothing does
        // this for us automatically).
        val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val maxHeight = (maxHeight - statusBarInset) * 0.92f

        // The scrim is a sibling *behind* the Surface, not an ancestor - a `clickable` on an
        // ancestor can't be "swallowed" by a disabled clickable on the Surface in Compose
        // foundation, so taps anywhere on the sheet (title, labels, gaps) would otherwise also
        // dismiss it. Hidden from the semantics tree since it carries no information of its own.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onScrimClick)
                .clearAndSetSemantics {},
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .testTag(ADD_BOOK_SHEET_TEST_TAG)
                .semantics { paneTitle = sheetTitle },
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            // UX spec section 1: the IME inset shrinks the body (so the focused field scrolls
            // fully above it) while the footer - the last fixed-height child - lands pinned
            // immediately above the IME, never hidden or pushed off-screen.
            Column(modifier = Modifier.imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = sheetTitle, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .testTag(ADD_BOOK_CLOSE_BUTTON_TEST_TAG)
                            .semantics { contentDescription = closeDescription },
                    ) {
                        Text("✕", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
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

                if (uiState.error == AddBookError.SaveFailed) {
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            }
        }
    }
}

@Composable
private fun DiscardConfirmDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag(ADD_BOOK_DISCARD_DIALOG_TEST_TAG),
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.add_book_discard_title)) },
        text = { Text(stringResource(R.string.add_book_discard_body)) },
        confirmButton = {
            Button(
                onClick = onDiscard,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.add_book_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.add_book_discard_keep_editing)) }
        },
    )
}
