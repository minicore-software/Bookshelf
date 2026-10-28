package com.minicore.bookshelf.navigation

import androidx.navigation.NavController

/**
 * The Book Detail modal's "Edit" seam (plan #32 decision V6, plan #33 decision E1). `bookId` is
 * the route argument the navigation graph reads back into `EditBookViewModel`.
 * `launchSingleTop = true` stops a double tap on the pencil from stacking two editors (F18).
 */
fun NavController.navigateToEditBook(bookId: Long) {
    navigate(BookshelfDestinations.EditBook.route(bookId)) { launchSingleTop = true }
}
