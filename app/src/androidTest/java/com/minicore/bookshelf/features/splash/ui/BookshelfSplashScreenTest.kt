package com.minicore.bookshelf.features.splash.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Timing is asserted against [androidx.compose.ui.test.MainTestClock] with
 * `autoAdvance = false`, driving the `delay()`-based `LaunchedEffect` explicitly via
 * `advanceTimeBy(millis, ignoreFrameDuration = true)`. `ignoreFrameDuration = true` is
 * required: the default (`false`) rounds the requested advance *up* to a whole number
 * of 16ms frames (`ceil(millis / 16) * 16`), so e.g. `advanceTimeBy(599)` actually
 * advances 608ms — past the 600ms duration — making a sub-duration assertion
 * impossible to express that way. With `ignoreFrameDuration = true` the clock advances
 * by exactly the requested virtual milliseconds, so `advanceTimeBy(SPLASH_DURATION_MS
 * - 1)` is guaranteed to land strictly before the `delay(600)` resolves and the final
 * `advanceTimeBy(1)` is guaranteed to land exactly on it — deterministically, with no
 * dependency on real wall-clock scheduling or test-environment jitter.
 */
class BookshelfSplashScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // F1 + F2: onComplete is not called before the duration elapses, and is
    // called exactly once once it does.
    @Test
    fun onCompleteFiresExactlyOnce_notBeforeTheDurationElapses() {
        var completedCount = 0
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            BookshelfSplashScreen(onComplete = { completedCount++ })
        }

        composeRule.onNodeWithTag(SPLASH_SCREEN_TEST_TAG).assertExists()

        composeRule.mainClock.advanceTimeBy(SPLASH_DURATION_MS - 1, ignoreFrameDuration = true)
        assertEquals(0, completedCount)

        composeRule.mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
        composeRule.waitForIdle()
        assertEquals(1, completedCount)
    }

    // F3: rememberUpdatedState correctness — recomposing with a new lambda before
    // the duration elapses must fire only the newest lambda, exactly once, never
    // the stale one.
    @Test
    fun recomposingWithNewLambdaBeforeDurationElapses_firesOnlyNewestLambdaOnce() {
        var staleCompletedCount = 0
        var freshCompletedCount = 0

        composeRule.setContent {
            var useFreshLambda by remember { mutableStateOf(false) }
            val onComplete: () -> Unit =
                if (useFreshLambda) {
                    { freshCompletedCount++ }
                } else {
                    { staleCompletedCount++ }
                }

            BookshelfSplashScreen(onComplete = onComplete)

            // Swap the lambda well before SPLASH_DURATION_MS elapses.
            LaunchedEffect(Unit) {
                delay(SPLASH_DURATION_MS / 3)
                useFreshLambda = true
            }
        }

        composeRule.waitUntil(timeoutMillis = SPLASH_DURATION_MS + 5_000) {
            freshCompletedCount > 0
        }
        composeRule.waitForIdle()

        assertEquals(0, staleCompletedCount)
        assertEquals(1, freshCompletedCount)
    }

    @Test
    fun splashNodeFillsTheRootBounds() {
        composeRule.setContent {
            BookshelfSplashScreen(onComplete = {})
        }

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val splashBounds =
            composeRule.onNodeWithTag(SPLASH_SCREEN_TEST_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue(splashBounds.width >= rootBounds.width - 1f)
        assertTrue(splashBounds.height >= rootBounds.height - 1f)
    }
}
