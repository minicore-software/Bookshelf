package com.minicore.bookshelf.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.features.library.ui.LIBRARY_SEARCH_BAR_TEST_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookshelfNavGraphTest {

    @get:Rule
    val composeRule = createComposeRule()

    // F1: the Home route is the graph's start destination, not a pushed screen.
    @Test
    fun startDestination_isHomeRoute() {
        lateinit var navController: TestNavHostController

        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfNavGraph(navController)
        }

        composeRule.waitForIdle()

        assertEquals(
            BookshelfDestinations.Home.route,
            navController.currentDestination?.route
        )
    }

    // F10/AC4(b): tapping the stub search bar is a no-op at the navigation level — the route and
    // back-stack size are unchanged.
    @Test
    fun tappingSearchBar_doesNotChangeTheBackStack() {
        lateinit var navController: TestNavHostController

        composeRule.setContent {
            navController = TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            BookshelfNavGraph(navController)
        }

        composeRule.waitForIdle()
        val backStackSizeBefore = navController.currentBackStack.value.size

        composeRule.onNodeWithTag(LIBRARY_SEARCH_BAR_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(
            BookshelfDestinations.Home.route,
            navController.currentDestination?.route
        )
        assertEquals(backStackSizeBefore, navController.currentBackStack.value.size)
    }
}
