package com.minicore.bookshelf.navigation

import androidx.navigation.NavController

/**
 * The only way into the Book Detail modal (plan #32 decision V2). `launchSingleTop` stops a fast
 * double tap on a Library card from stacking two modals for the same book (QA F16).
 */
fun NavController.navigateToBookDetail(bookId: Long) {
    navigate(BookshelfDestinations.BookDetail.route(bookId)) { launchSingleTop = true }
}
