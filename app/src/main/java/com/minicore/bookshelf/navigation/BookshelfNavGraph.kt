package com.minicore.bookshelf.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import com.minicore.bookshelf.features.library.ui.LibraryRoute
import com.minicore.bookshelf.features.library.ui.addbook.AddBookResult
import com.minicore.bookshelf.features.library.ui.addbook.AddBookRoute

/** A12: the result key `AddBookRoute` -> `LibraryRoute` hand off a successful save through, never
 * exposed outside `navigation/` (the Library side only sees a plain `Boolean`, see
 * `LibraryRoute.showBookAddedSnackbar`). */
private const val KEY_BOOK_ADDED = "book_added"

@Composable
fun BookshelfNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = BookshelfDestinations.Home.route
    ) {
        composable(BookshelfDestinations.Home.route) { backStackEntry ->
            // stub until search/Book Detail/My Loans ship (epic #17 D18, D19)
            val bookAdded by backStackEntry.savedStateHandle
                .getStateFlow(KEY_BOOK_ADDED, false)
                .collectAsStateWithLifecycle()

            LaunchedEffect(bookAdded) {
                if (bookAdded) backStackEntry.savedStateHandle[KEY_BOOK_ADDED] = false
            }

            LibraryRoute(
                onSearchClick = {},
                onBookClick = {},
                onSeeAllLoansClick = {},
                onAddBookClick = { navController.navigateToAddBook() },
                showBookAddedSnackbar = bookAdded,
            )
        }
        dialog(
            route = BookshelfDestinations.AddBookManual.route,
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            AddBookRoute(
                onDone = { result ->
                    if (result is AddBookResult.Saved) {
                        navController.previousBackStackEntry?.savedStateHandle?.set(KEY_BOOK_ADDED, true)
                    }
                    navController.popBackStack()
                },
            )
        }
    }
}
