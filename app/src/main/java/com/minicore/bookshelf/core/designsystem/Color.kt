package com.minicore.bookshelf.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.minicore.bookshelf.R

/**
 * Builds the Bookshelf light [ColorScheme] from [R.color] (single-sourced in `colors.xml`, not
 * duplicated as Kotlin hex literals). Bookshelf is light-only: no dynamic colour, no dark theme.
 */
@Composable
internal fun bookshelfColorScheme(): ColorScheme {
    val paper = colorResource(R.color.paper)
    val ink = colorResource(R.color.ink)
    val forest = colorResource(R.color.forest)
    val inkMuted = colorResource(R.color.ink_muted)
    val paperBright = colorResource(R.color.paper_bright)
    val line = colorResource(R.color.line)
    val overdue = colorResource(R.color.overdue)

    return lightColorScheme(
        primary = forest,
        onPrimary = paper,
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        onSurfaceVariant = inkMuted,
        outlineVariant = line,
        surfaceContainerLowest = paperBright,
        // Library rows (BOOKSHELF-0020, R5): overdue loans map onto M3's error role.
        error = overdue,
    )
}

/** A single cover-placeholder colour pair (plan R2): a background and its AA-passing foreground. */
@Immutable
data class CoverColors(val background: Color, val content: Color)

/**
 * Tokens used by the Library rows (plan R5) that have no natural M3 role: borrower text,
 * due/overdue captions, and the six-colour cover placeholder palette (plan R2). Single-sourced
 * from `colors.xml`, read through [BookshelfTheme.extendedColors] - never a hex literal in a
 * feature (`core/designsystem` review rule).
 */
@Immutable
data class BookshelfExtendedColors(
    val inkSoft: Color,
    val dueUpcoming: Color,
    val overdue: Color,
    val coverPalette: List<CoverColors>,
)

internal val LocalBookshelfExtendedColors = staticCompositionLocalOf<BookshelfExtendedColors> {
    error("No BookshelfExtendedColors provided - wrap content in BookshelfTheme")
}

@Composable
internal fun bookshelfExtendedColors(): BookshelfExtendedColors {
    val cream = colorResource(R.color.cover_cream)
    val ink = colorResource(R.color.ink)
    return BookshelfExtendedColors(
        inkSoft = colorResource(R.color.ink_soft),
        dueUpcoming = colorResource(R.color.due_upcoming),
        overdue = colorResource(R.color.overdue),
        coverPalette = listOf(
            CoverColors(colorResource(R.color.cover_1_bg), cream),
            CoverColors(colorResource(R.color.cover_2_bg), cream),
            CoverColors(colorResource(R.color.cover_3_bg), ink),
            CoverColors(colorResource(R.color.cover_4_bg), cream),
            CoverColors(colorResource(R.color.cover_5_bg), cream),
            CoverColors(colorResource(R.color.cover_6_bg), ink),
        ),
    )
}
