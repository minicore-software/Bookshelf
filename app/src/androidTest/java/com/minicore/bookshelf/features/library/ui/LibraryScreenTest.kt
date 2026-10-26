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
import com.minicore.bookshelf.features.library.domain.LibraryRows
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

    private val emptyTitle: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.library_empty_title)

    private val emptyBody: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.library_empty_body)

    // F1/AC1: the avatar and greeting are gone — no node has "MT", "Good evening" or
    // "Marco's Shelf" — and the brand-mark tile is displayed in their place.
    @Test
    fun libraryScreen_hasNoAvatarOrGreeting() {
        composeRule.setContent {
            BookshelfTheme {
                LibraryScreen(uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())))
            }
        }

        composeRule.onNodeWithText("MT").assertDoesNotExist()
        composeRule.onNodeWithText("Good evening").assertDoesNotExist()
        composeRule.onNodeWithText("Marco's Shelf").assertDoesNotExist()
        composeRule.onNodeWithTag(LIBRARY_BRAND_MARK_TEST_TAG).assertIsDisplayed()
    }

    // F2/AC1/AC3, updated for BOOKSHELF-0022 AC2: exactly the five expected strings are present
    // on screen for a 0-book library - the header's three plus the empty-state heading and body -
    // nothing else (no chips, no "Your library", no cards).
    @Test
    fun libraryScreen_showsExactlyTheExpectedText() {
        composeRule.setContent {
            BookshelfTheme {
                LibraryScreen(uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())))
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(tagline).assertIsDisplayed()
        composeRule.onNodeWithText(placeholder).assertIsDisplayed()
        composeRule.onNodeWithText(emptyTitle).assertIsDisplayed()
        composeRule.onNodeWithText(emptyBody).assertIsDisplayed()
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .assertCountEquals(5)
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
                        LibraryScreen(uiState = LibraryUiState.Content(LibraryRows(emptyList(), emptyList())))
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
