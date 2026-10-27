package com.minicore.bookshelf.features.library.ui.bookform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.CoverDraft

const val BOOK_FORM_TEST_TAG = "book_form"
const val BOOK_FORM_TITLE_FIELD_TEST_TAG = "book_form_title_field"
const val BOOK_FORM_AUTHOR_FIELD_TEST_TAG = "book_form_author_field"
const val BOOK_FORM_COMMENT_FIELD_TEST_TAG = "book_form_comment_field"

/**
 * The stateless "book" + "cover" fields (plan #31 decision A3/A4): seeded and driven entirely by
 * its parameters, no [androidx.lifecycle.ViewModel] of its own, so #33 Edit can reuse it with its
 * own extra sections. Deliberately does not import anything from `ui.addbook` (`FF-A4`).
 *
 * @param cover the draft's current cover choice - a preset or an in-progress image (plan #31
 *   decision A5/4.2).
 * @param isImportingImage `true` while an uploaded image is being decoded/downscaled (plan A5) -
 *   forwarded to [CoverPicker] for its in-progress row.
 */
@Composable
fun BookForm(
    title: String,
    onTitleChange: (String) -> Unit,
    author: String,
    onAuthorChange: (String) -> Unit,
    yearText: String,
    onYearTextChange: (String) -> Unit,
    onYearStep: (Int) -> Unit,
    rating: Int?,
    onRatingTap: (Int) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    cover: CoverDraft,
    isImportingImage: Boolean,
    onPresetSelected: (CoverPreset) -> Unit,
    onUploadImageClick: () -> Unit,
    onRemoveImageClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag(BOOK_FORM_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.add_book_section_book),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth().testTag(BOOK_FORM_TITLE_FIELD_TEST_TAG),
            label = { Text(stringResource(R.string.add_book_field_title)) },
            placeholder = { Text(stringResource(R.string.add_book_field_title_placeholder)) },
            enabled = enabled,
            singleLine = true,
        )
        OutlinedTextField(
            value = author,
            onValueChange = onAuthorChange,
            modifier = Modifier.fillMaxWidth().testTag(BOOK_FORM_AUTHOR_FIELD_TEST_TAG),
            label = { Text(stringResource(R.string.add_book_field_author)) },
            placeholder = { Text(stringResource(R.string.add_book_field_author_placeholder)) },
            enabled = enabled,
            singleLine = true,
        )
        YearStepperField(yearText = yearText, onYearTextChange = onYearTextChange, onStep = onYearStep, enabled = enabled)
        StarRatingInput(rating = rating, onRatingTap = onRatingTap, enabled = enabled)
        OutlinedTextField(
            value = comment,
            onValueChange = onCommentChange,
            modifier = Modifier.fillMaxWidth().testTag(BOOK_FORM_COMMENT_FIELD_TEST_TAG),
            label = { Text(stringResource(R.string.add_book_field_comment)) },
            placeholder = { Text(stringResource(R.string.add_book_field_comment_placeholder)) },
            enabled = enabled,
            minLines = 3,
            maxLines = 8,
        )
        Text(
            text = stringResource(R.string.add_book_section_cover),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        CoverPicker(
            cover = cover,
            isImportingImage = isImportingImage,
            onPresetSelected = onPresetSelected,
            onUploadClick = onUploadImageClick,
            onRemoveClick = onRemoveImageClick,
            enabled = enabled,
        )
    }
}

@Preview(name = "BookForm")
@Composable
private fun BookFormPreview() {
    BookshelfTheme {
        BookForm(
            title = "Dune",
            onTitleChange = {},
            author = "",
            onAuthorChange = {},
            yearText = "",
            onYearTextChange = {},
            onYearStep = {},
            rating = null,
            onRatingTap = {},
            comment = "",
            onCommentChange = {},
            cover = CoverDraft.Preset(CoverPreset.Preset1),
            isImportingImage = false,
            onPresetSelected = {},
            onUploadImageClick = {},
            onRemoveImageClick = {},
            enabled = true,
        )
    }
}
