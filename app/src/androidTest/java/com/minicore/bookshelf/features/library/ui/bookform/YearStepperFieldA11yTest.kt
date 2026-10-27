package com.minicore.bookshelf.features.library.ui.bookform

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Review fix-findings (top suggestion #1, BOOKSHELF-0031): the stepper's custom actions must live
 * on the text field itself (a semantics leaf for TalkBack), not on an ancestor Row with two
 * clickable chevron children - and each chevron needs its own contentDescription + Button role.
 */
class YearStepperFieldA11yTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exactlyOneNode_exposesTheIncreaseAndDecreaseCustomActions() {
        composeRule.setContent {
            BookshelfTheme { YearStepperField(yearText = "1965", onYearTextChange = {}, onStep = {}) }
        }

        val matches = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .fetchSemanticsNodes()
        assertEquals(1, matches.size)
        val actions = matches.single().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Increase year", "Decrease year"), actions.map { it.label })
    }

    @Test
    fun chevrons_haveContentDescriptionAndButtonRole() {
        composeRule.setContent {
            BookshelfTheme { YearStepperField(yearText = "1965", onYearTextChange = {}, onStep = {}) }
        }

        val increase = composeRule.onNodeWithTag(YEAR_STEPPER_INCREASE_TEST_TAG).fetchSemanticsNode()
        assertEquals("Increase year", increase.config[SemanticsProperties.ContentDescription].first())
        assertEquals(Role.Button, increase.config[SemanticsProperties.Role])

        val decrease = composeRule.onNodeWithTag(YEAR_STEPPER_DECREASE_TEST_TAG).fetchSemanticsNode()
        assertEquals("Decrease year", decrease.config[SemanticsProperties.ContentDescription].first())
        assertEquals(Role.Button, decrease.config[SemanticsProperties.Role])
    }

    @Test
    fun disabled_chevronsExposeNoClickAction_textFieldExposesNoCustomActions() {
        composeRule.setContent {
            BookshelfTheme { YearStepperField(yearText = "1965", onYearTextChange = {}, onStep = {}, enabled = false) }
        }

        val increase = composeRule.onNodeWithTag(YEAR_STEPPER_INCREASE_TEST_TAG).fetchSemanticsNode()
        assertNull(increase.config.getOrNull(SemanticsActions.OnClick))

        val matches = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .fetchSemanticsNodes()
        assertTrue(matches.isEmpty())
    }
}
