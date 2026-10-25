package com.minicore.bookshelf

import android.content.res.Configuration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.di.LibraryDataModule
import com.minicore.bookshelf.features.library.domain.LibraryRepository
import com.minicore.bookshelf.features.library.ui.LIBRARY_LENT_ROW_TEST_TAG
import com.minicore.bookshelf.features.library.ui.LIBRARY_READING_ROW_TEST_TAG
import com.minicore.bookshelf.features.library.ui.LIBRARY_SEARCH_BAR_TEST_TAG
import com.minicore.bookshelf.features.splash.ui.SPLASH_SCREEN_TEST_TAG
import com.minicore.bookshelf.navigation.FakeLibraryRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@UninstallModules(LibraryDataModule::class)
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    // Empty by default: a developer's debug data in the real database would make the R11
    // "neither row" check flaky if it depended on the device's own bookshelf.db (plan section 8).
    @BindValue
    @JvmField
    val fakeRepository: LibraryRepository = FakeLibraryRepository()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun launchReachesResumedState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    // F1: the "Bookshelf" title is the start destination, not a pushed screen. The route
    // assertion itself lives in BookshelfNavGraphTest; this only proves MainActivity mounts
    // the graph and the title actually reaches the screen.
    @Test
    fun splashCompletes_andLibraryTitleIsDisplayed() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(SPLASH_SCREEN_TEST_TAG).fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.app_name))
            .assertIsDisplayed()
    }

    // F8: enableEdgeToEdge's SystemBarStyle.light(...) must force dark status/navigation bar
    // icons even with the system in dark mode, since the background is always paper. The
    // system is forced into dark mode (via "cmd uimode night yes", restored after) before
    // launching a fresh Activity instance: in light mode (the default, and what the shared
    // composeRule's own Activity already launched under) even the old SystemBarStyle.auto
    // would also yield light status bars, which would let this test pass without defending
    // the fix.
    @Test
    fun systemBarIconsStayDark_whenSystemIsInDarkMode() {
        setNightMode(force = true)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertTrue(
                        "test setup: system did not actually switch to dark mode",
                        activity.resources.configuration.isNightMode
                    )

                    val window = activity.window
                    val controller = WindowInsetsControllerCompat(window, window.decorView)

                    assertTrue(controller.isAppearanceLightStatusBars)
                    assertTrue(controller.isAppearanceLightNavigationBars)
                }
            }
        } finally {
            setNightMode(force = false)
        }
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

    // F11/AC4(c): tapping the stub search bar at the app level is a no-op — the activity stays
    // RESUMED, the title is still displayed, and nothing crashes.
    @Test
    fun tappingSearchBar_afterSplash_staysResumedAndDisplaysTitle() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(SPLASH_SCREEN_TEST_TAG).fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag(LIBRARY_SEARCH_BAR_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(Lifecycle.State.RESUMED, composeRule.activityRule.scenario.state)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.app_name))
            .assertIsDisplayed()
    }

    // R11/F11/AC4(c): with an empty fake repository, neither row's section title exists -
    // guards against a fresh install (or the device's real, possibly non-empty, database)
    // showing an empty "Currently reading"/"Lent out" header with nothing under it.
    @Test
    fun emptyLibrary_showsNeitherRowSectionTitle() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(SPLASH_SCREEN_TEST_TAG).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.library_currently_reading_title))
            .assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.library_lent_out_title))
            .assertDoesNotExist()
        composeRule.onAllNodesWithTag(LIBRARY_READING_ROW_TEST_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(LIBRARY_LENT_ROW_TEST_TAG).assertCountEquals(0)
    }

    private val Configuration.isNightMode: Boolean
        get() = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun setNightMode(force: Boolean) {
        val mode = if (force) "yes" else "no"
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd uimode night $mode")
            .close()

        val deadline = System.currentTimeMillis() + 5_000
        while (targetContext.resources.configuration.isNightMode != force &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(50)
        }
    }

    @After
    fun restoreLightMode() {
        setNightMode(force = false)
    }
}
