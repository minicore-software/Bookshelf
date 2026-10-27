package com.minicore.bookshelf.navigation

import androidx.navigation.NavController

/**
 * The Book Detail modal's "Edit" seam (plan #32 decision V6). Its body is a documented no-op
 * until #33 registers `book/{bookId}/edit` and navigates here - until then, no `EditBook` route
 * exists, so navigating would crash with `IllegalArgumentException`. #33 changes only this
 * function's body; the Book Detail screen and its call site never change.
 */
fun NavController.navigateToEditBook(bookId: Long) {
    // #33 registers book/{bookId}/edit and navigates here.
}
