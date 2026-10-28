package com.minicore.bookshelf.features.library.ui.bookform

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.coverimage.LocalCoverImageLoader
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.CoverDraft

const val COVER_PICKER_TEST_TAG = "cover_picker"
const val COVER_SWATCH_TEST_TAG_PREFIX = "cover_swatch_"
const val COVER_UPLOAD_BUTTON_TEST_TAG = "cover_upload_button"
const val COVER_REPLACE_BUTTON_TEST_TAG = "cover_replace_button"
const val COVER_REMOVE_BUTTON_TEST_TAG = "cover_remove_button"
const val COVER_IMAGE_ROW_TEST_TAG = "cover_image_row"

private val SwatchSize = androidx.compose.ui.unit.DpSize(32.dp, 44.dp)
private val SwatchShape = RoundedCornerShape(4.dp)
private val ThumbnailSize = androidx.compose.ui.unit.DpSize(56.dp, 80.dp)
private val ThumbnailShape = RoundedCornerShape(6.dp)

/**
 * The cover field (plan #31 section 4.4/task 8, UX spec section 6, decision A10): an
 * "Upload an image..." entry point into the system Photo Picker when no image is set, or a
 * Replace/Remove row with a live thumbnail once one is, plus the always-tappable preset swatch
 * row (UX spec section 6: "Swatches remain tappable even while an image is set").
 *
 * @param cover the draft's current choice; drives which of the upload button / image row shows
 *   and which swatch (if any) carries the selection ring.
 * @param isImportingImage `true` while [com.minicore.bookshelf.core.coverimage.CoverImageStore.importDraft]
 *   is in flight (UX spec section 6): shows a spinner in place of the thumbnail and hides
 *   Replace/Remove; the swatch row stays tappable (UX spec's accepted implementation deviation).
 * @param onUploadClick launches the system Photo Picker (wired by the caller via
 *   `rememberLauncherForActivityResult`, plan A5) - used for both the initial "Upload an image..."
 *   button and "Replace".
 */
@Composable
fun CoverPicker(
    cover: CoverDraft,
    isImportingImage: Boolean,
    onPresetSelected: (CoverPreset) -> Unit,
    onUploadClick: () -> Unit,
    onRemoveClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val selectedPreset = (cover as? CoverDraft.Preset)?.preset
    val palette = BookshelfTheme.extendedColors.coverPalette

    Column(modifier = modifier.testTag(COVER_PICKER_TEST_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            isImportingImage -> ImportingImageRow()
            cover is CoverDraft.Image -> ImageSetRow(
                ref = cover.draftRef,
                enabled = enabled,
                onReplaceClick = onUploadClick,
                onRemoveClick = onRemoveClick,
            )
            cover is CoverDraft.Stored -> ImageSetRow(
                ref = cover.storedRef,
                enabled = enabled,
                onReplaceClick = onUploadClick,
                onRemoveClick = onRemoveClick,
            )
            else -> OutlinedButton(
                onClick = onUploadClick,
                enabled = enabled,
                modifier = Modifier.testTag(COVER_UPLOAD_BUTTON_TEST_TAG),
            ) {
                Text(stringResource(R.string.add_book_cover_upload))
            }
        }

        Text(
            text = stringResource(R.string.add_book_cover_or_pick_color),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverPreset.entries.forEachIndexed { index, preset ->
                val isSelected = preset == selectedPreset
                val descriptionText = stringResource(R.string.add_book_cover_swatch_description, index + 1, CoverPreset.entries.size)
                val selectedText = stringResource(R.string.add_book_cover_swatch_selected)
                Box(
                    modifier = Modifier
                        .testTag(COVER_SWATCH_TEST_TAG_PREFIX + (index + 1))
                        .padding(2.dp)
                        .let {
                            if (isSelected) it.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)).padding(2.dp) else it
                        }
                        .size(SwatchSize.width, SwatchSize.height)
                        .clip(SwatchShape)
                        .background(palette[index].background)
                        .clickable(enabled = enabled, onClickLabel = descriptionText, role = Role.Button) { onPresetSelected(preset) }
                        .semantics {
                            contentDescription = descriptionText
                            selected = isSelected
                            if (isSelected) stateDescription = selectedText
                        },
                ) {}
            }
        }
    }
}

@Composable
private fun ImportingImageRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ThumbnailShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(ThumbnailSize.width, ThumbnailSize.height).clip(ThumbnailShape),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        Text(text = stringResource(R.string.add_book_cover_importing), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImageSetRow(ref: String, enabled: Boolean, onReplaceClick: () -> Unit, onRemoveClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(COVER_IMAGE_ROW_TEST_TAG)
            .clip(ThumbnailShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumbnail(ref = ref)
        Text(
            text = stringResource(R.string.add_book_cover_custom_image),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onReplaceClick, enabled = enabled, modifier = Modifier.testTag(COVER_REPLACE_BUTTON_TEST_TAG)) {
            Text(stringResource(R.string.add_book_cover_replace))
        }
        TextButton(onClick = onRemoveClick, enabled = enabled, modifier = Modifier.testTag(COVER_REMOVE_BUTTON_TEST_TAG)) {
            Text(stringResource(R.string.add_book_cover_remove))
        }
    }
}

/** Previews the real draft image via [LocalCoverImageLoader] (plan A11), not a placeholder. */
@Composable
private fun CoverThumbnail(ref: String) {
    val loader = LocalCoverImageLoader.current
    val maxSizePx = with(LocalDensity.current) { ThumbnailSize.width.roundToPx() }
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, ref, loader) {
        value = loader.load(ref, maxSizePx)
    }
    Box(
        modifier = Modifier
            .size(ThumbnailSize.width, ThumbnailSize.height)
            .clip(ThumbnailShape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        bitmap?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Preview(name = "CoverPicker — preset selected")
@Composable
private fun CoverPickerPreview() {
    BookshelfTheme {
        CoverPicker(
            cover = CoverDraft.Preset(CoverPreset.Preset1),
            isImportingImage = false,
            onPresetSelected = {},
            onUploadClick = {},
            onRemoveClick = {},
            enabled = true,
        )
    }
}

@Preview(name = "CoverPicker — image set")
@Composable
private fun CoverPickerImageSetPreview() {
    BookshelfTheme {
        CoverPicker(
            cover = CoverDraft.Image("cover-drafts/11111111-1111-1111-1111-111111111111.jpg"),
            isImportingImage = false,
            onPresetSelected = {},
            onUploadClick = {},
            onRemoveClick = {},
            enabled = true,
        )
    }
}

@Preview(name = "CoverPicker — importing")
@Composable
private fun CoverPickerImportingPreview() {
    BookshelfTheme {
        CoverPicker(
            cover = CoverDraft.Preset(CoverPreset.Preset1),
            isImportingImage = true,
            onPresetSelected = {},
            onUploadClick = {},
            onRemoveClick = {},
            enabled = true,
        )
    }
}
