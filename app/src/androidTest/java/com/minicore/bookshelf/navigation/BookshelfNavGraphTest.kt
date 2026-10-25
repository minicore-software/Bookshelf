package com.minicore.bookshelf.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
}
