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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import org.junit.Assert.assertEquals
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
        composeRule.onRoot(useUnmergedTree = true).onChildren().assertCountEquals(1)
    }

    // F3/FF1: the title really renders in Fraunces, not a fallback, and the rendered node's
    // measured width matches a Fraunces-only measurement within 1px. The fallback widths must
    // each differ from the Fraunces width by more than that same tolerance, so a fallback that
    // happens to land within the tolerance can't slip past the "differs from fallback" checks.
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
        val tolerancePx = 1

        assertTrue(
            "Fraunces width ($frauncesWidth) must differ from FontFamily.Default ($defaultWidth) " +
                "by more than $tolerancePx px, a near-miss fallback must not slip through",
            abs(frauncesWidth - defaultWidth) > tolerancePx
        )
        assertTrue(
            "Fraunces width ($frauncesWidth) must differ from FontFamily.Serif ($serifWidth) " +
                "by more than $tolerancePx px, a near-miss fallback must not slip through",
            abs(frauncesWidth - serifWidth) > tolerancePx
        )

        val renderedWidth = composeRule.onNodeWithText(title).fetchSemanticsNode().size.width
        assertTrue(
            "rendered width ($renderedWidth) must match the Fraunces measurement " +
                "($frauncesWidth) within $tolerancePx px",
            abs(renderedWidth - frauncesWidth) <= tolerancePx
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

        assertTrue(
            "title node should carry heading semantics",
            node.config.contains(SemanticsProperties.Heading)
        )
        assertFalse(
            "title must not be clipped to zero width at 2x font scale",
            node.size.width == 0
        )

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

        // Not `layoutResult.hasVisualOverflow`/`didOverflowWidth`: per the real implementation
        // (`didOverflowWidth = size.width < multiParagraph.width`), that flag compares the
        // Text's own shrink-wrapped box against the *maximum* width budget it was offered, not
        // against its unconstrained natural width. For a Text with no `fillMaxWidth()` (like
        // this title), `size.width` is virtually always smaller than the offered budget, so the
        // flag is true essentially unconditionally here and does not indicate real clipping.
        // Clipping would only actually occur if the natural, unbreakable word needed more width
        // than the Box could offer, in which case Compose clamps `node.size.width` to exactly
        // that offered budget (`layoutInput.constraints.maxWidth`). A *strict* less-than check
        // against that budget is therefore the real "did not need to clip" signal.
        assertTrue(
            "title width (${node.size.width}px) must fit strictly within the available width " +
                "(${layoutResult.layoutInput.constraints.maxWidth}px) at 2x font scale, or it " +
                "was clamped/clipped",
            node.size.width < layoutResult.layoutInput.constraints.maxWidth
        )
    }
}
