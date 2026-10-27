package com.minicore.bookshelf.navigation

import androidx.navigation.NavController

/**
 * The only way into "add a book" (epic #29 decision D12, plan #31 decision A2). Today it always
 * opens the manual form. A future scan/search epic changes only this function's body (to open a
 * chooser first, for example) - the FAB, the Library and the manual form never change.
 * `launchSingleTop` stops a fast double tap on the FAB from stacking two forms (F16).
 */
fun NavController.navigateToAddBook() {
    navigate(BookshelfDestinations.AddBookManual.route) { launchSingleTop = true }
}
