package com.minicore.bookshelf.features.library.ui.bookform

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.CoverDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Review fix-findings (top suggestion #4, BOOKSHELF-0031): while saving, the year stepper and
 * star rating must stop reacting to taps too, not just the text fields - [BookForm] was passing
 * `enabled` only to the [androidx.compose.material3.OutlinedTextField]s.
 */
class BookFormEnabledTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disabled_yearStepperAndRating_ignoreTaps() {
        var stepped: Int? = null
        var tappedStar: Int? = null

        composeRule.setContent {
            BookshelfTheme {
                BookForm(
                    title = "Dune",
                    onTitleChange = {},
                    author = "",
                    onAuthorChange = {},
                    yearText = "1965",
                    onYearTextChange = {},
                    onYearStep = { stepped = it },
                    rating = null,
                    onRatingTap = { tappedStar = it },
                    comment = "",
                    onCommentChange = {},
                    cover = CoverDraft.Preset(CoverPreset.Preset1),
                    isImportingImage = false,
                    onPresetSelected = {},
                    onUploadImageClick = {},
                    onRemoveImageClick = {},
                    enabled = false,
                )
            }
        }

        composeRule.onNodeWithTag(YEAR_STEPPER_INCREASE_TEST_TAG).performClick()
        // The star Canvases sit behind StarRatingInput's clearAndSetSemantics boundary (critical
        // #4), so the default merged-tree query can't see them - this is the real click path, not
        // a workaround.
        composeRule.onNodeWithTag(STAR_RATING_STAR_TEST_TAG_PREFIX + 3, useUnmergedTree = true).performClick()

        assertNull(stepped)
        assertNull(tappedStar)
    }

    @Test
    fun enabled_yearStepperAndRating_reactToTaps() {
        var stepped: Int? = null
        var tappedStar: Int? = null

        composeRule.setContent {
            BookshelfTheme {
                BookForm(
                    title = "Dune",
                    onTitleChange = {},
                    author = "",
                    onAuthorChange = {},
                    yearText = "1965",
                    onYearTextChange = {},
                    onYearStep = { stepped = it },
                    rating = null,
                    onRatingTap = { tappedStar = it },
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

        composeRule.onNodeWithTag(YEAR_STEPPER_INCREASE_TEST_TAG).performClick()
        composeRule.onNodeWithTag(STAR_RATING_STAR_TEST_TAG_PREFIX + 3, useUnmergedTree = true).performClick()

        assertEquals(1, stepped)
        assertEquals(3, tappedStar)
    }
}
