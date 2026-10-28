package com.minicore.bookshelf.features.library.ui.editbook

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** AC3/F6/F7 (plan #33 section 3.5, decision D13): the lent indicator is a display, never a
 * control - checked state reflects the loan, disabled, no click action, and tapping it does
 * nothing. */
class BorrowingSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notLentOut_showsUncheckedAndDisabled() {
        composeRule.setContent {
            BookshelfTheme { BorrowingSection(isLentOut = false) }
        }

        val row = composeRule.onNodeWithTag(BORROWING_CHECKBOX_ROW_TEST_TAG)
        row.assertIsNotEnabled()
        assertTrue(row.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.Off)
    }

    @Test
    fun lentOut_showsCheckedAndDisabled() {
        composeRule.setContent {
            BookshelfTheme { BorrowingSection(isLentOut = true) }
        }

        val row = composeRule.onNodeWithTag(BORROWING_CHECKBOX_ROW_TEST_TAG)
        row.assertIsNotEnabled()
        assertTrue(row.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On)
    }

    // F7: no OnClick semantics action is defined on the row.
    @Test
    fun row_hasNoOnClickAction() {
        composeRule.setContent {
            BookshelfTheme { BorrowingSection(isLentOut = false) }
        }

        composeRule.onNodeWithTag(BORROWING_CHECKBOX_ROW_TEST_TAG)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    // F7: performClick() on the row while lent or not lent leaves the state unchanged (there is
    // nothing listening - this just proves the row does not crash or toggle under a raw gesture).
    @Test
    fun performClick_onTheRow_doesNotChangeState() {
        composeRule.setContent {
            BookshelfTheme { BorrowingSection(isLentOut = true) }
        }

        val row = composeRule.onNodeWithTag(BORROWING_CHECKBOX_ROW_TEST_TAG)
        row.performClick()

        assertTrue(row.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On)
    }
}
