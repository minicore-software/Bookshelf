package com.minicore.bookshelf

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minicore.bookshelf.features.splash.ui.SPLASH_SCREEN_TEST_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchReachesResumedState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    // F8
    @Test
    fun splashCompletes_andHomePlaceholderIsDisplayed() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(SPLASH_SCREEN_TEST_TAG).fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.app_name))
            .assertIsDisplayed()
    }

    // F9: rotation (simulated via recreate) after the splash completes must not replay it.
    @Test
    fun splashDoesNotReplay_afterRecreate() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(SPLASH_SCREEN_TEST_TAG).fetchSemanticsNodes().isEmpty()
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(SPLASH_SCREEN_TEST_TAG).assertCountEquals(0)
    }
}
