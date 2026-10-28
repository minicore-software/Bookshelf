package com.minicore.bookshelf.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.minicore.bookshelf.features.library.ui.LibraryRoute
import com.minicore.bookshelf.features.library.ui.addbook.AddBookResult
import com.minicore.bookshelf.features.library.ui.addbook.AddBookRoute
import com.minicore.bookshelf.features.library.ui.bookdetail.BookDetailRoute
import com.minicore.bookshelf.features.library.ui.editbook.EditBookRoute

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
                onBookClick = { navController.navigateToBookDetail(it) },
                onSeeAllLoansClick = {},
                onAddBookClick = { navController.navigateToAddBook() },
                bookAddedEventId = bookAddedId,
                onBookAddedSnackbarShown = {
                    // Must be `set(KEY_BOOK_ADDED, null)`, not `remove()`: `remove()` only clears
                    // the SavedStateHandle's backing map entry, it never pushes a value through
                    // the live StateFlow returned by `getStateFlow` (see
                    // androidx.lifecycle.SavedStateHandle - `remove` vs `set`/`flows[key]?.value =
                    // value`). With `remove()`, after the FIRST save's Snackbar shows, the flow is
                    // stuck on that first non-null id forever, so no subsequent save in the same
                    // session re-triggers `LibraryRoute`'s `LaunchedEffect` (round 3 critical
                    // finding). `set(..., null)` both pushes `null` through the flow (ending this
                    // one-shot cleanly, since this callback runs after `showSnackbar` completes)
                    // and still stores `null` for restore.
                    backStackEntry.savedStateHandle[KEY_BOOK_ADDED] = null
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
        dialog(
            route = BookshelfDestinations.BookDetail.route,
            arguments = listOf(navArgument(BookshelfDestinations.BookDetail.ARG_BOOK_ID) { type = NavType.LongType }),
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            BookDetailRoute(
                // Pop this destination specifically, not just "whatever's on top": a
                // NotFound-triggered auto-close (BookDetailRoute.kt) can race a user tap, and once
                // #33 adds Edit above Detail a plain popBackStack() would pop the wrong entry
                // (top suggestion, review round 1).
                onClose = { navController.popBackStack(BookshelfDestinations.BookDetail.route, inclusive = true) },
                onEditClick = { bookId -> navController.navigateToEditBook(bookId) },
                onLendClick = {},
            )
        }
        dialog(
            route = BookshelfDestinations.EditBook.route,
            arguments = listOf(navArgument(BookshelfDestinations.EditBook.ARG_BOOK_ID) { type = NavType.LongType }),
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            EditBookRoute(
                // Saved/Cancelled/NotFound all pop this destination specifically (plan #33
                // decision E2) - Detail is then visible again and already shows any saved
                // values (its flow is live); a NotFound also pops Detail itself, through
                // Detail's own NotFound handling (#32 V8), once Detail re-collects and sees the
                // row is gone.
                onDone = { navController.popBackStack(BookshelfDestinations.EditBook.route, inclusive = true) },
                onRemoveClick = {} /* #34 wires the confirm + cascade */,
            )
        }
    }
}
