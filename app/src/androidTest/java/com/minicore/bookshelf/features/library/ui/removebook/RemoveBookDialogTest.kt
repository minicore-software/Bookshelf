package com.minicore.bookshelf.features.library.ui.removebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** F1-F4 (plan #34 section 6): [RemoveBookDialog]'s warning visibility and dismiss routes. */
class RemoveBookDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        bookTitle: String = "Dune",
        borrowerName: String? = null,
        onConfirm: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            BookshelfTheme {
                RemoveBookDialog(
                    bookTitle = bookTitle,
                    borrowerName = borrowerName,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    // F1: no open loan -> no warning, Confirm enabled.
    @Test
    fun noOpenLoan_noWarning_confirmEnabled() {
        setContent(borrowerName = null)

        composeRule.onNodeWithTag(REMOVE_BOOK_OPEN_LOAN_WARNING_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG).assertIsEnabled()
    }

    // F2: an open loan shows the warning with the borrower's name, and Confirm stays enabled -
    // the warning informs, it never blocks.
    @Test
    fun openLoan_showsWarningWithBorrowerName_confirmStillEnabled() {
        setContent(borrowerName = "Alex")

        composeRule.onNodeWithTag(REMOVE_BOOK_OPEN_LOAN_WARNING_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Alex", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG).assertIsEnabled()
    }

    // F3: Cancel calls onDismiss, never onConfirm.
    @Test
    fun cancel_callsOnDismiss_notOnConfirm() {
        var confirmed = false
        var dismissed = false
        setContent(onConfirm = { confirmed = true }, onDismiss = { dismissed = true })

        composeRule.onNodeWithTag(REMOVE_BOOK_CANCEL_BUTTON_TEST_TAG).performClick()

        assertEquals(true, dismissed)
        assertEquals(false, confirmed)
    }

    // F4: system back triggers onDismiss, never onConfirm.
    @Test
    fun systemBack_triggersOnDismiss() {
        var confirmed = false
        var dismissed = false
        setContent(onConfirm = { confirmed = true }, onDismiss = { dismissed = true })

        pressBack()
        composeRule.waitForIdle()

        assertEquals(true, dismissed)
        assertEquals(false, confirmed)
    }

    // Confirm calls onConfirm, never onDismiss.
    @Test
    fun confirm_callsOnConfirm_notOnDismiss() {
        var confirmed = false
        var dismissed = false
        setContent(onConfirm = { confirmed = true }, onDismiss = { dismissed = true })

        composeRule.onNodeWithTag(REMOVE_BOOK_CONFIRM_BUTTON_TEST_TAG).performClick()

        assertEquals(true, confirmed)
        assertEquals(false, dismissed)
    }

    // R10: the title uses the book title passed in.
    @Test
    fun title_showsTheBookTitle() {
        setContent(bookTitle = "The Left Hand of Darkness")

        composeRule.onNodeWithText("The Left Hand of Darkness", substring = true).assertIsDisplayed()
    }
}
