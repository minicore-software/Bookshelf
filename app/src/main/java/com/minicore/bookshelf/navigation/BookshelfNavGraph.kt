package com.minicore.bookshelf.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.minicore.bookshelf.features.library.ui.LibraryRoute

@Composable
fun BookshelfNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = BookshelfDestinations.Home.route
    ) {
        composable(BookshelfDestinations.Home.route) {
            // stub until search/Book Detail/My Loans ship (epic #17 D9, D18, D19)
            LibraryRoute(
                onSearchClick = {},
                onBookClick = {},
                onSeeAllLoansClick = {},
            )
        }
    }
}
