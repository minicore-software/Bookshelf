package com.minicore.bookshelf.navigation

import androidx.compose.runtime.Composable
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
 * exposed outside `navigation/` (the Library side only sees a plain id, see
 * `LibraryRoute.bookAddedEventId`). Carries the new book's id - a fresh value every save - rather
 * than a `Boolean`, so `LibraryRoute`'s `LaunchedEffect` keyed on it is a one-shot per save: there
 * is no need to (and this must not) write a reset value back afterwards, since doing so
 * synchronously here previously changed the key under `LibraryRoute`'s `LaunchedEffect`, which
 * cancelled its in-flight `showSnackbar` call about a frame after it started (critical finding
 * #5). */
private const val KEY_BOOK_ADDED = "book_added"

@Composable
fun BookshelfNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = BookshelfDestinations.Home.route
    ) {
        composable(BookshelfDestinations.Home.route) { backStackEntry ->
            // stub until search/Book Detail/My Loans ship (epic #17 D18, D19)
            val bookAddedId by backStackEntry.savedStateHandle
                .getStateFlow<Long?>(KEY_BOOK_ADDED, null)
                .collectAsStateWithLifecycle()

            LibraryRoute(
                onSearchClick = {},
                onBookClick = {},
                onSeeAllLoansClick = {},
                onAddBookClick = { navController.navigateToAddBook() },
                bookAddedEventId = bookAddedId,
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
                        navController.previousBackStackEntry?.savedStateHandle?.set(KEY_BOOK_ADDED, result.bookId)
                    }
                    navController.popBackStack()
                },
            )
        }
    }
}
