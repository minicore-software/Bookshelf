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
 * Timing is asserted against the real/wall clock rather than
 * [androidx.compose.ui.test.MainTestClock]: attempts to drive this with the virtual
 * clock (`mainClock.advanceTimeBy`, with and without `autoAdvance = false`) did not
 * reliably control the `delay()`-based `LaunchedEffect` on this project's Compose
 * BOM — see docs/plans/9-splash-screen.md's "Known Deviations" section for exactly
 * what was observed. Falling back to the real clock, the lower bound is exact: a
 * real `delay(600)` cannot resolve less than 600ms after `startNanos`, which is
 * captured during composition, before the effect even starts. The upper bound
 * stays generous to absorb test-environment scheduling jitter without masking a
 * stuck/looping effect.
 */
private const val UPPER_BOUND_SLACK_MS = 1_000L

class BookshelfSplashScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // F1 + F2: onComplete is not called before the duration elapses, and is
    // called exactly once once it does.
    @Test
    fun onCompleteFiresExactlyOnce_notBeforeTheDurationElapses() {
        var completedCount = 0
        var completedAtNanos = 0L
        var startNanos = 0L

        composeRule.setContent {
            // Captured on first composition rather than before setContent(), so the
            // measurement excludes Compose test environment startup overhead and only
            // covers the composable's own delay.
            startNanos = remember { System.nanoTime() }
            BookshelfSplashScreen(onComplete = {
                completedCount++
                completedAtNanos = System.nanoTime()
            })
        }

        composeRule.onNodeWithTag(SPLASH_SCREEN_TEST_TAG).assertExists()

        composeRule.waitUntil(timeoutMillis = SPLASH_DURATION_MS + 5_000) { completedCount > 0 }

        val elapsedMs = (completedAtNanos - startNanos) / 1_000_000
        assertTrue(
            "expected onComplete at least ${SPLASH_DURATION_MS}ms after start, " +
                "was ${elapsedMs}ms",
            elapsedMs >= SPLASH_DURATION_MS
        )
        assertTrue(
            "expected onComplete less than ${SPLASH_DURATION_MS + UPPER_BOUND_SLACK_MS}ms " +
                "after start, was ${elapsedMs}ms",
            elapsedMs < SPLASH_DURATION_MS + UPPER_BOUND_SLACK_MS
        )

        // Give any spurious extra firing a chance to show up before asserting the count.
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
