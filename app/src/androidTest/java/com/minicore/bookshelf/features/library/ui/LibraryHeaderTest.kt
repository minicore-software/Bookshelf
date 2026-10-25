package com.minicore.bookshelf.features.library.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.R
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class LibraryHeaderTest {

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

    // F3: brand-mark is 38x38dp; the title+tagline text block sits 14dp to its right, with the
    // block's vertical center aligned to the mark's vertical center (the PNG lockup: the mark is
    // centered against the whole text block, not just the title), and the title's cap-top close
    // to the mark's top.
    @Test
    fun brandMarkAndTextBlock_areSizedAndAlignedPerSpec() {
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryHeader(onSearchClick = {})
                }
            }
        }

        composeRule.onNodeWithTag(LIBRARY_BRAND_MARK_TEST_TAG)
            .assertWidthIsEqualTo(38.dp)
            .assertHeightIsEqualTo(38.dp)

        val markBounds = composeRule.onNodeWithTag(LIBRARY_BRAND_MARK_TEST_TAG)
            .fetchSemanticsNode().boundsInRoot
        val titleBounds = composeRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        val taglineBounds = composeRule.onNodeWithText(tagline).fetchSemanticsNode().boundsInRoot

        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val gapPx = 14.dp.value * density
        val tolerancePx = 1.dp.value * density
        val verticalTolerancePx = 2.dp.value * density
        val capTopTolerancePx = 10.dp.value * density

        assertTrue(
            "title left edge (${titleBounds.left}) must be mark's right edge " +
                "(${markBounds.right}) + 14dp (expected ${markBounds.right + gapPx}) within $tolerancePx px",
            abs(titleBounds.left - (markBounds.right + gapPx)) <= tolerancePx
        )

        val textBlockTop = minOf(titleBounds.top, taglineBounds.top)
        val textBlockBottom = maxOf(titleBounds.bottom, taglineBounds.bottom)
        val textBlockCenterY = (textBlockTop + textBlockBottom) / 2
        val markCenterY = markBounds.top + markBounds.height / 2

        assertTrue(
            "text block center-y ($textBlockCenterY) and mark center-y ($markCenterY) must be " +
                "within $verticalTolerancePx px",
            abs(textBlockCenterY - markCenterY) <= verticalTolerancePx
        )

        assertTrue(
            "title top (${titleBounds.top}) must be close to the mark's top (${markBounds.top}) " +
                "within $capTopTolerancePx px",
            abs(titleBounds.top - markBounds.top) <= capTopTolerancePx
        )
    }

    // F4/FF1: the title really renders in Fraunces (titleLarge), not a fallback.
    @Test
    fun title_rendersInFraunces_matchingMeasuredWidth() {
        var frauncesResult: TextLayoutResult? = null
        var defaultResult: TextLayoutResult? = null
        var serifResult: TextLayoutResult? = null

        composeRule.setContent {
            BookshelfTheme {
                val textMeasurer = rememberTextMeasurer()
                val titleStyle = MaterialTheme.typography.titleLarge
                frauncesResult = textMeasurer.measure(title, titleStyle)
                defaultResult =
                    textMeasurer.measure(title, titleStyle.copy(fontFamily = FontFamily.Default))
                serifResult =
                    textMeasurer.measure(title, titleStyle.copy(fontFamily = FontFamily.Serif))

                Surface {
                    LibraryHeader(onSearchClick = {})
                }
            }
        }

        val frauncesWidth = frauncesResult!!.size.width
        val defaultWidth = defaultResult!!.size.width
        val serifWidth = serifResult!!.size.width
        val tolerancePx = 1

        assertTrue(
            "titleLarge width ($frauncesWidth) must differ from FontFamily.Default " +
                "($defaultWidth) by more than $tolerancePx px",
            abs(frauncesWidth - defaultWidth) > tolerancePx
        )
        assertTrue(
            "titleLarge width ($frauncesWidth) must differ from FontFamily.Serif " +
                "($serifWidth) by more than $tolerancePx px",
            abs(frauncesWidth - serifWidth) > tolerancePx
        )

        val renderedWidth = composeRule.onNodeWithText(title).fetchSemanticsNode().size.width
        assertTrue(
            "rendered title width ($renderedWidth) must match the titleLarge measurement " +
                "($frauncesWidth) within $tolerancePx px",
            abs(renderedWidth - frauncesWidth) <= tolerancePx
        )
    }

    // F5/FF1: the tagline really renders in Inter (bodySmall), not a fallback.
    @Test
    fun tagline_rendersInInter_matchingMeasuredWidth() {
        var interResult: TextLayoutResult? = null
        var defaultResult: TextLayoutResult? = null

        composeRule.setContent {
            BookshelfTheme {
                val textMeasurer = rememberTextMeasurer()
                val taglineStyle = MaterialTheme.typography.bodySmall
                interResult = textMeasurer.measure(tagline, taglineStyle)
                defaultResult =
                    textMeasurer.measure(tagline, taglineStyle.copy(fontFamily = FontFamily.Default))

                Surface {
                    LibraryHeader(onSearchClick = {})
                }
            }
        }

        val interWidth = interResult!!.size.width
        val defaultWidth = defaultResult!!.size.width
        val tolerancePx = 1

        assertTrue(
            "bodySmall width ($interWidth) must differ from FontFamily.Default ($defaultWidth) " +
                "by more than $tolerancePx px",
            abs(interWidth - defaultWidth) > tolerancePx
        )

        val renderedWidth = composeRule.onNodeWithText(tagline).fetchSemanticsNode().size.width
        assertTrue(
            "rendered tagline width ($renderedWidth) must match the bodySmall measurement " +
                "($interWidth) within $tolerancePx px",
            abs(renderedWidth - interWidth) <= tolerancePx
        )
    }

    // F9: tapping the search bar invokes the callback exactly once, does not focus anything, and
    // does not otherwise change the tree.
    @Test
    fun searchBar_tapIsNoOp_otherThanInvokingCallback() {
        var clicks = 0
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryHeader(onSearchClick = { clicks++ })
                }
            }
        }

        composeRule.onNodeWithTag(LIBRARY_SEARCH_BAR_TEST_TAG).performClick()

        assertEquals(1, clicks)
        composeRule.onNodeWithTag(LIBRARY_SEARCH_BAR_TEST_TAG).assertIsNotFocused()
        composeRule.onNodeWithTag(LIBRARY_HEADER_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(placeholder).assertIsDisplayed()
    }

    // F13: exactly one heading node (the title), no node exposes the bare "B" glyph text, and
    // the search bar is a semantic button with a click action.
    @Test
    fun semantics_oneHeadingNoStrayGlyph_searchBarIsAButton() {
        composeRule.setContent {
            BookshelfTheme {
                Surface {
                    LibraryHeader(onSearchClick = {})
                }
            }
        }

        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertCountEquals(1)
        composeRule.onNodeWithText(title)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

        // Merged tree (the default, and what TalkBack actually traverses): the brand mark's
        // `clearAndSetSemantics {}` must hide the "B" glyph from assistive services, even though
        // the raw text node is still present in the unmerged tree.
        val brandMarkGlyph = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.brand_mark_initial)
        composeRule.onAllNodesWithText(brandMarkGlyph)
            .assertCountEquals(0)

        val searchBarNode = composeRule.onNodeWithTag(LIBRARY_SEARCH_BAR_TEST_TAG)
            .assertHasClickAction()
            .fetchSemanticsNode()
        assertEquals(Role.Button, searchBarNode.config[SemanticsProperties.Role])
    }

    // F14: the brand mark tile stays exactly 38x38dp even at 2x font scale.
    @Test
    fun brandMark_staysFixedSize_atDoubleFontScale() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                BookshelfTheme {
                    Surface {
                        LibraryHeader(onSearchClick = {})
                    }
                }
            }
        }

        composeRule.onNodeWithTag(LIBRARY_BRAND_MARK_TEST_TAG)
            .assertWidthIsEqualTo(38.dp)
            .assertHeightIsEqualTo(38.dp)
    }

    // Critical 2 (review): the glyph itself (not just the tile) stays centered and fully inside
    // the tile at 2x font scale. Before the fix, titleLarge's lineHeight (28sp) still scaled with
    // the system font size even though fontSize was pinned, sinking/shrinking the glyph.
    @Test
    fun brandMarkGlyph_staysCenteredAndInsideTile_atDoubleFontScale() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                BookshelfTheme {
                    Surface {
                        LibraryHeader(onSearchClick = {})
                    }
                }
            }
        }

        val markBounds = composeRule.onNodeWithTag(LIBRARY_BRAND_MARK_TEST_TAG)
            .fetchSemanticsNode().boundsInRoot

        // Unmerged tree: this inspects layout, not accessibility exposure (F13 already proves
        // the glyph text is hidden from the merged/TalkBack tree).
        val brandMarkGlyph = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.brand_mark_initial)
        val glyphBounds = composeRule.onNodeWithText(brandMarkGlyph, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val tolerancePx = 1.dp.value * density

        assertTrue(
            "glyph bounds ($glyphBounds) must be fully inside the tile bounds ($markBounds) " +
                "at 2x font scale",
            glyphBounds.left >= markBounds.left - tolerancePx &&
                glyphBounds.right <= markBounds.right + tolerancePx &&
                glyphBounds.top >= markBounds.top - tolerancePx &&
                glyphBounds.bottom <= markBounds.bottom + tolerancePx
        )

        val glyphCenterX = glyphBounds.left + glyphBounds.width / 2
        val glyphCenterY = glyphBounds.top + glyphBounds.height / 2
        val markCenterX = markBounds.left + markBounds.width / 2
        val markCenterY = markBounds.top + markBounds.height / 2

        assertTrue(
            "glyph center ($glyphCenterX,$glyphCenterY) must be within $tolerancePx px of the " +
                "tile center ($markCenterX,$markCenterY)",
            abs(glyphCenterX - markCenterX) <= tolerancePx &&
                abs(glyphCenterY - markCenterY) <= tolerancePx
        )
    }

    // F15: the search bar is at least 48dp tall, spans the header width minus 2x20dp padding,
    // and its placeholder is visible.
    @Test
    fun searchBar_meetsTouchTargetAndWidthSpec() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 800.dp))) {
                BookshelfTheme {
                    Surface {
                        LibraryHeader(onSearchClick = {})
                    }
                }
            }
        }

        composeRule.onNodeWithTag(LIBRARY_SEARCH_BAR_TEST_TAG).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(placeholder).assertIsDisplayed()

        val searchBarWidth = composeRule.onNodeWithTag(LIBRARY_SEARCH_BAR_TEST_TAG)
            .fetchSemanticsNode().size.width
        val headerWidth = composeRule.onNodeWithTag(LIBRARY_HEADER_TEST_TAG)
            .fetchSemanticsNode().size.width

        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val expectedWidth = headerWidth - (2 * 20.dp.value * density)
        val tolerancePx = 1.dp.value * density

        assertTrue(
            "search bar width ($searchBarWidth) must equal header width minus 2x20dp padding " +
                "($expectedWidth) within $tolerancePx px",
            abs(searchBarWidth - expectedWidth) <= tolerancePx
        )
    }
}
