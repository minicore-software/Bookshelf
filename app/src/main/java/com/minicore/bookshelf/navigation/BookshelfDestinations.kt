package com.minicore.bookshelf.navigation

sealed class BookshelfDestinations(val route: String) {
    data object Home : BookshelfDestinations("home")
}
