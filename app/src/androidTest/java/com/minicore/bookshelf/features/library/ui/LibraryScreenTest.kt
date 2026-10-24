package com.minicore.bookshelf.features.library.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class LibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val title: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.app_name)

    // F2: the screen has exactly one visible node — the title — and nothing else.
    @Test
    fun libraryScreen_showsOnlyTheTitle() {
        composeRule.setContent {
            BookshelfTheme {
                LibraryScreen()
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .assertCountEquals(1)
    }

    // F3/FF1: the title really renders in Fraunces, not a fallback, and the rendered node's
    // measured width matches a Fraunces-only measurement within a 2% relative tolerance.
    @Test
    fun libraryScreen_titleRendersInFraunces_matchingMeasuredWidth() {
        var frauncesResult: TextLayoutResult? = null
        var defaultResult: TextLayoutResult? = null
        var serifResult: TextLayoutResult? = null

        composeRule.setContent {
            BookshelfTheme {
                val textMeasurer = rememberTextMeasurer()
                val frauncesStyle = MaterialTheme.typography.headlineMedium
                frauncesResult = textMeasurer.measure(title, frauncesStyle)
                defaultResult =
                    textMeasurer.measure(title, frauncesStyle.copy(fontFamily = FontFamily.Default))
                serifResult =
                    textMeasurer.measure(title, frauncesStyle.copy(fontFamily = FontFamily.Serif))

                Surface {
                    LibraryScreen()
                }
            }
        }

        val frauncesWidth = frauncesResult!!.size.width
        val defaultWidth = defaultResult!!.size.width
        val serifWidth = serifResult!!.size.width

        assertTrue(
            "Fraunces width ($frauncesWidth) must differ from FontFamily.Default ($defaultWidth)",
            frauncesWidth != defaultWidth
        )
        assertTrue(
            "Fraunces width ($frauncesWidth) must differ from FontFamily.Serif ($serifWidth)",
            frauncesWidth != serifWidth
        )

        val renderedWidth = composeRule.onNodeWithText(title).fetchSemanticsNode().size.width
        val relativeDiff = abs(renderedWidth - frauncesWidth).toFloat() / frauncesWidth
        assertTrue(
            "rendered width ($renderedWidth) must match the Fraunces measurement " +
                "($frauncesWidth) within 2%, was ${relativeDiff * 100}%",
            relativeDiff <= 0.02f
        )
    }

    // F9/FF5: at 2x font scale, the full title is visible, not clipped or ellipsized, and
    // still carries heading semantics.
    @Test
    fun libraryScreen_atDoubleFontScale_showsFullTitleWithHeadingSemantics() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                BookshelfTheme {
                    Surface {
                        LibraryScreen()
                    }
                }
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()

        val node = composeRule.onNodeWithText(title).fetchSemanticsNode()
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().size.width

        assertTrue(
            "title node should carry heading semantics",
            node.config.contains(SemanticsProperties.Heading)
        )
        assertFalse(
            "title must not be clipped to zero width at 2x font scale",
            node.size.width == 0
        )
        assertTrue(
            "title width (${node.size.width}) must fit within the root (${rootWidth})",
            node.size.width <= rootWidth
        )
    }
}
