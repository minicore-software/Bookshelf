package com.minicore.bookshelf.features.library.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val title: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.app_name)

    private val tagline: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.library_tagline)

    private val placeholder: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.library_search_placeholder)

    // F1/AC1: the avatar and greeting are gone — no node has "MT", "Good evening" or
    // "Marco's Shelf" — and the only non-text graphic in the lockup is the brand-mark tile.
    @Test
    fun libraryScreen_showsOnlyTheTitle() {
        composeRule.setContent {
            BookshelfTheme {
                LibraryScreen()
            }
        }

        composeRule.onNodeWithText("MT").assertDoesNotExist()
        composeRule.onNodeWithText("Good evening").assertDoesNotExist()
        composeRule.onNodeWithText("Marco's Shelf").assertDoesNotExist()
        composeRule.onNodeWithTag(LIBRARY_BRAND_MARK_TEST_TAG).assertIsDisplayed()
    }

    // F2/AC1/AC3: exactly the three expected strings are present on screen, nothing else.
    @Test
    fun libraryScreen_showsExactlyTheExpectedText() {
        composeRule.setContent {
            BookshelfTheme {
                LibraryScreen()
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(tagline).assertIsDisplayed()
        composeRule.onNodeWithText(placeholder).assertIsDisplayed()
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .assertCountEquals(3)
    }

    // F12/FF5: at 2x font scale on a 320dp-wide viewport, the title is unclipped and on one
    // line, the tagline is fully displayed (wrapping allowed), and the search bar meets the
    // 48dp touch-target minimum.
    @Test
    fun libraryScreen_atDoubleFontScaleAndNarrowWidth_remainsUnclippedAndUsable() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(2f)
                    .then(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 800.dp)))
            ) {
                BookshelfTheme {
                    Surface {
                        LibraryScreen()
                    }
                }
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(tagline).assertIsDisplayed()
        composeRule.onNodeWithTag(LIBRARY_SEARCH_BAR_TEST_TAG).assertHeightIsAtLeast(48.dp)

        val node = composeRule.onNodeWithText(title).fetchSemanticsNode()
        assertFalse("title must not be clipped to zero width at 2x font scale", node.size.width == 0)

        val textLayoutResults = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(textLayoutResults)
        val layoutResult = textLayoutResults.single()

        assertEquals(
            "title must render on a single line at 2x font scale, not wrap",
            1,
            layoutResult.lineCount
        )
        assertTrue(
            "title width (${node.size.width}px) must fit strictly within the available width " +
                "(${layoutResult.layoutInput.constraints.maxWidth}px) at 2x font scale, or it " +
                "was clamped/clipped",
            node.size.width < layoutResult.layoutInput.constraints.maxWidth
        )
    }
}
