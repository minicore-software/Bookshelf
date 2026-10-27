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
 * than a `Boolean`, so `LibraryRoute`'s `LaunchedEffect` keyed on it is a one-shot per save: this
 * must not be reset synchronously on write, since doing so previously changed the key under
 * `LibraryRoute`'s `LaunchedEffect` mid-flight and cancelled its in-flight `showSnackbar` call
 * about a frame after it started (critical finding #5). It must, however, be removed from the
 * `SavedStateHandle` once `LibraryRoute` reports (via `onBookAddedSnackbarShown`) that the
 * Snackbar has actually been shown - otherwise `getStateFlow` replays this same non-null value
 * on every Activity recreation (rotation/config-change/process-death restore), reshowing the
 * Snackbar indefinitely (critical finding round 2). */
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
                onBookAddedSnackbarShown = {
                    backStackEntry.savedStateHandle.remove<Long>(KEY_BOOK_ADDED)
                },
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
