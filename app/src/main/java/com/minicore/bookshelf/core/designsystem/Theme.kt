package com.minicore.bookshelf.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Bookshelf's single theming entry point: paper/ink/forest palette, Fraunces headline typography.
 * Light-only, no dynamic colour — the app has no dark palette (see `docs/plans/10-blank-landing-screen.md`).
 */
@Composable
fun BookshelfTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBookshelfExtendedColors provides bookshelfExtendedColors()) {
        MaterialTheme(
            colorScheme = bookshelfColorScheme(),
            typography = BookshelfTypography,
            content = content,
        )
    }
}

/** Tokens that have no M3 role (plan R5) - access only inside [BookshelfTheme]. */
object BookshelfTheme {
    val extendedColors: BookshelfExtendedColors
        @Composable get() = LocalBookshelfExtendedColors.current
}
