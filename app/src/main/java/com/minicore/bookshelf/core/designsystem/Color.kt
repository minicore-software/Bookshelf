package com.minicore.bookshelf.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

    return lightColorScheme(
        primary = forest,
        onPrimary = paper,
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
    )
}
