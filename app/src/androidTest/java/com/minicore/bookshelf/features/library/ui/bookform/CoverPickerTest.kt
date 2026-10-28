package com.minicore.bookshelf.features.library.ui.bookform

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.CoverDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * UX spec #31 section 6: the always-present swatch row, the "Upload an image..." entry point, the
 * Replace/Remove row once an image is set, and the in-progress state. No Hilt needed -
 * [CoverPicker] is stateless.
 */
class CoverPickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun presetState_showsUploadButton_noImageRow() {
        composeRule.setContent {
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

        composeRule.onNodeWithTag(COVER_UPLOAD_BUTTON_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(COVER_IMAGE_ROW_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun tappingUpload_invokesCallback() {
        var uploadClicked = false
        composeRule.setContent {
            BookshelfTheme {
                CoverPicker(
                    cover = CoverDraft.Preset(CoverPreset.Preset1),
                    isImportingImage = false,
                    onPresetSelected = {},
                    onUploadClick = { uploadClicked = true },
                    onRemoveClick = {},
                    enabled = true,
                )
            }
        }

        composeRule.onNodeWithTag(COVER_UPLOAD_BUTTON_TEST_TAG).performClick()
        assertTrue(uploadClicked)
    }

    // AC4: once an image is set, the upload button is replaced by the thumbnail + Replace/Remove
    // row, and no swatch shows as selected.
    @Test
    fun imageSetState_showsReplaceAndRemove_noSwatchSelected() {
        composeRule.setContent {
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

        composeRule.onNodeWithTag(COVER_UPLOAD_BUTTON_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(COVER_IMAGE_ROW_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(COVER_REPLACE_BUTTON_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(COVER_REMOVE_BUTTON_TEST_TAG).assertIsDisplayed()

        for (index in 1..CoverPreset.entries.size) {
            val selected = composeRule.onNodeWithTag(COVER_SWATCH_TEST_TAG_PREFIX + index)
                .fetchSemanticsNode()
                .config
                .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Selected) { false }
            assertFalse("swatch $index must not be selected while an image is set", selected)
        }
    }

    @Test
    fun tappingReplace_relaunchesUpload() {
        var uploadClicked = false
        composeRule.setContent {
            BookshelfTheme {
                CoverPicker(
                    cover = CoverDraft.Image("cover-drafts/11111111-1111-1111-1111-111111111111.jpg"),
                    isImportingImage = false,
                    onPresetSelected = {},
                    onUploadClick = { uploadClicked = true },
                    onRemoveClick = {},
                    enabled = true,
                )
            }
        }

        composeRule.onNodeWithTag(COVER_REPLACE_BUTTON_TEST_TAG).performClick()
        assertTrue(uploadClicked)
    }

    @Test
    fun tappingRemove_invokesCallback() {
        var removeClicked = false
        composeRule.setContent {
            BookshelfTheme {
                CoverPicker(
                    cover = CoverDraft.Image("cover-drafts/11111111-1111-1111-1111-111111111111.jpg"),
                    isImportingImage = false,
                    onPresetSelected = {},
                    onUploadClick = {},
                    onRemoveClick = { removeClicked = true },
                    enabled = true,
                )
            }
        }

        composeRule.onNodeWithTag(COVER_REMOVE_BUTTON_TEST_TAG).performClick()
        assertTrue(removeClicked)
    }

    // UX spec section 6: swatches remain tappable even while an image is set - tapping one
    // replaces the image directly, no confirm dialog.
    @Test
    fun swatchStaysTappable_whileImageIsSet() {
        var selectedPreset: CoverPreset? = null
        composeRule.setContent {
            BookshelfTheme {
                CoverPicker(
                    cover = CoverDraft.Image("cover-drafts/11111111-1111-1111-1111-111111111111.jpg"),
                    isImportingImage = false,
                    onPresetSelected = { selectedPreset = it },
                    onUploadClick = {},
                    onRemoveClick = {},
                    enabled = true,
                )
            }
        }

        composeRule.onNodeWithTag(COVER_SWATCH_TEST_TAG_PREFIX + "3").performClick()
        assertEquals(CoverPreset.Preset3, selectedPreset)
    }

    // plan #33 E5/F1: a Stored cover (the book's existing image) renders exactly like Image.
    @Test
    fun storedCoverState_showsReplaceAndRemove_likeImage() {
        composeRule.setContent {
            BookshelfTheme {
                CoverPicker(
                    cover = CoverDraft.Stored("covers/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg"),
                    isImportingImage = false,
                    onPresetSelected = {},
                    onUploadClick = {},
                    onRemoveClick = {},
                    enabled = true,
                )
            }
        }

        composeRule.onNodeWithTag(COVER_UPLOAD_BUTTON_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(COVER_IMAGE_ROW_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(COVER_REPLACE_BUTTON_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(COVER_REMOVE_BUTTON_TEST_TAG).assertIsDisplayed()
    }

    // UX spec section 6 "Import-in-progress state": spinner row shown, Replace/Remove hidden.
    @Test
    fun importingState_showsSpinnerRow_hidesReplaceAndRemove() {
        composeRule.setContent {
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

        composeRule.onNodeWithTag(COVER_UPLOAD_BUTTON_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(COVER_IMAGE_ROW_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(COVER_REPLACE_BUTTON_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(COVER_REMOVE_BUTTON_TEST_TAG).assertDoesNotExist()
    }

    // Default: Preset 1 carries the selection ring (stateDescription "Selected").
    @Test
    fun defaultPreset1_isMarkedSelected() {
        composeRule.setContent {
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

        val selected = composeRule.onNodeWithTag(COVER_SWATCH_TEST_TAG_PREFIX + "1")
            .fetchSemanticsNode()
            .config
            .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Selected) { false }
        assertTrue(selected)
    }
}
