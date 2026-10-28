package com.minicore.bookshelf.navigation

sealed class BookshelfDestinations(val route: String) {
    data object Home : BookshelfDestinations("home")

    /** The manual "add a book" form (plan #31 decision A2). Navigated to only via [navigateToAddBook]. */
    data object AddBookManual : BookshelfDestinations("add_book/manual")

    /** The read-only Book Detail modal (plan #32 decision V1). Navigated to only via
     * [navigateToBookDetail]. [route] is named so `book/{bookId}/edit` (#33) becomes a sibling. */
    data object BookDetail : BookshelfDestinations("book/{bookId}") {
        const val ARG_BOOK_ID = "bookId"
        fun route(bookId: Long) = "book/$bookId"
    }

    /** The "edit a book" form (plan #33 decision E1), stacked on top of [BookDetail]. Navigated
     * to only via [com.minicore.bookshelf.navigation.navigateToEditBook]. */
    data object EditBook : BookshelfDestinations("book/{bookId}/edit") {
        const val ARG_BOOK_ID = "bookId"
        fun route(bookId: Long) = "book/$bookId/edit"
    }
}
