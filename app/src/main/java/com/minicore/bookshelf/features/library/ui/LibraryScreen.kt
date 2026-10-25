package com.minicore.bookshelf.features.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.minicore.bookshelf.core.designsystem.BookshelfTheme

/**
 * The Library home screen. Shows the wordmark header (brand mark, title, tagline) plus a stub
 * search bar (ticket #19, closes the #10 placeholder); the Library epic grows this same screen
 * on this same route.
 *
 * @param onSearchClick forwarded to [LibraryHeader]'s search bar. Defaults to a no-op: this
 *   ticket adds no navigation, so `BookshelfNavGraph` mounts this screen without wiring one yet.
 */
@Composable
fun LibraryScreen(onSearchClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        LibraryHeader(onSearchClick = onSearchClick)
    }
}

@Preview(name = "Library")
@Composable
private fun LibraryScreenPreview() {
    BookshelfTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibraryScreen()
        }
    }
}
