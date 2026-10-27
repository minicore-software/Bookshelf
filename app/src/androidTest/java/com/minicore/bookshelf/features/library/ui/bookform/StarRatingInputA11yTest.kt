package com.minicore.bookshelf.features.library.ui.bookform

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Review fix-findings (critical #4, BOOKSHELF-0031): [StarRatingInput] must be one composite
 * accessibility node, not a Row with 5 selectable semantic children - otherwise TalkBack stops on
 * each star individually instead of the control as a whole.
 */
class StarRatingInputA11yTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val hasStateDescription = SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)

    @Test
    fun unsetRating_isOneCompositeNode_withFiveSetActions_noClearAction() {
        composeRule.setContent {
            BookshelfTheme { StarRatingInput(rating = null, onRatingTap = {}) }
        }

        // Exactly one node in the whole tree carries a stateDescription - the Row itself, not any
        // of its 5 stars (which would make this a 6-stop control for TalkBack).
        composeRule.onAllNodes(hasStateDescription).assertCountEquals(1)

        // Round 2: the previous assertions above would also pass on the pre-fix code, since a
        // plain `selectable` never set `stateDescription` in the first place and the custom
        // actions were already attached to the Row before `clearAndSetSemantics` existed. What
        // actually proves `clearAndSetSemantics` removed the 5 star children from the
        // accessibility tree is that none of them - nor any `selectable` node - are reachable in
        // the default MERGED semantics tree (no `useUnmergedTree`).
        composeRule.onNodeWithTag(STAR_RATING_STAR_TEST_TAG_PREFIX + 1).assertDoesNotExist()
        composeRule.onAllNodes(isSelectable()).assertCountEquals(0)

        val node = composeRule.onNodeWithTag(STAR_RATING_INPUT_TEST_TAG).fetchSemanticsNode()
        assertEquals("No rating", node.config[SemanticsProperties.StateDescription])
        assertEquals("Rating", node.config[SemanticsProperties.ContentDescription].first())

        val actions = node.config[SemanticsActions.CustomActions]
        assertEquals(5, actions.size)
        assertEquals("Set rating to 1", actions[0].label)
        assertEquals("Set rating to 5", actions[4].label)
    }

    @Test
    fun setRating_exposesStateDescriptionAndClearAction() {
        composeRule.setContent {
            BookshelfTheme { StarRatingInput(rating = 3, onRatingTap = {}) }
        }

        composeRule.onAllNodes(hasStateDescription).assertCountEquals(1)

        // Round 2: same gap as the unset-rating test above - prove the children are actually gone
        // from the merged tree, not just that the Row carries a stateDescription.
        composeRule.onNodeWithTag(STAR_RATING_STAR_TEST_TAG_PREFIX + 1).assertDoesNotExist()
        composeRule.onAllNodes(isSelectable()).assertCountEquals(0)

        val node = composeRule.onNodeWithTag(STAR_RATING_INPUT_TEST_TAG).fetchSemanticsNode()
        assertEquals("3 of 5 stars", node.config[SemanticsProperties.StateDescription])

        val actions = node.config[SemanticsActions.CustomActions]
        assertEquals(6, actions.size)
        assertTrue(actions.any { it.label == "Clear rating" })
    }

    @Test
    fun disabled_exposesNoCustomActions() {
        composeRule.setContent {
            BookshelfTheme { StarRatingInput(rating = 2, onRatingTap = {}, enabled = false) }
        }

        val node = composeRule.onNodeWithTag(STAR_RATING_INPUT_TEST_TAG).fetchSemanticsNode()
        val actions = node.config.getOrElse(SemanticsActions.CustomActions) { emptyList() }
        assertTrue(actions.isEmpty())
    }
}
