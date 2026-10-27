package com.minicore.bookshelf.navigation

sealed class BookshelfDestinations(val route: String) {
    data object Home : BookshelfDestinations("home")

    /** The manual "add a book" form (plan #31 decision A2). Navigated to only via [navigateToAddBook]. */
    data object AddBookManual : BookshelfDestinations("add_book/manual")
}
