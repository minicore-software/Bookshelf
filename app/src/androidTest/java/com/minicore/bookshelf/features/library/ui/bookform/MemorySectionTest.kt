package com.minicore.bookshelf.features.library.ui.bookform

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.minicore.bookshelf.core.designsystem.BookshelfTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** AC2/F5 (plan #33 section 3.3, decision D15): the "Memory" heading plus three independent,
 * labelled, placeholder-carrying multiline fields. */
class MemorySectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersHeadingAndThreeLabelledFields_withPlaceholders() {
        composeRule.setContent {
            BookshelfTheme {
                MemorySection(
                    memoryWhy = "",
                    onMemoryWhyChange = {},
                    memoryWhere = "",
                    onMemoryWhereChange = {},
                    memoryWhat = "",
                    onMemoryWhatChange = {},
                    enabled = true,
                )
            }
        }

        composeRule.onNodeWithText("Memory").assertIsDisplayed()
        composeRule.onNodeWithText("Why did I read this?").assertIsDisplayed()
        composeRule.onNodeWithText("Where was I?").assertIsDisplayed()
        composeRule.onNodeWithText("What was I going through?").assertIsDisplayed()
        composeRule.onNodeWithText("Recommended by…").assertIsDisplayed()
        composeRule.onNodeWithText("On the train to Edinburgh, the rainy week…").assertIsDisplayed()
        composeRule.onNodeWithText("Restless, mostly. Tea kept going cold.").assertIsDisplayed()
    }

    // D15: each of the three fields is edited independently.
    @Test
    fun eachField_isEditedIndependently() {
        var why = ""
        var where = ""
        var what = ""

        composeRule.setContent {
            BookshelfTheme {
                MemorySection(
                    memoryWhy = why,
                    onMemoryWhyChange = { why = it },
                    memoryWhere = where,
                    onMemoryWhereChange = { where = it },
                    memoryWhat = what,
                    onMemoryWhatChange = { what = it },
                    enabled = true,
                )
            }
        }

        composeRule.onNodeWithTag(MEMORY_WHERE_FIELD_TEST_TAG).performTextInput("Edinburgh")

        assertEquals("", why)
        assertEquals("Edinburgh", where)
        assertEquals("", what)
    }
}
