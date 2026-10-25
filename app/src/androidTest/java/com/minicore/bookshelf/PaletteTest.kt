package com.minicore.bookshelf

import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

private const val INK_ARGB = 0xFF231F20.toInt()
private const val FOREST_ARGB = 0xFF2F4538.toInt()
private const val INK_MUTED_ARGB = 0xFF6B5E53.toInt()
private const val PAPER_BRIGHT_ARGB = 0xFFFBF8F1.toInt()
private const val LINE_ARGB = 0xFFE3DCC9.toInt()

// F7/FF4: pins the AC's ink/forest token values, the same way SplashThemeTest pins paper.
@RunWith(AndroidJUnit4::class)
class PaletteTest {

    @Test
    fun inkAndForestColorsMatchTheDesignTokens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals(INK_ARGB, ContextCompat.getColor(context, R.color.ink))
        assertEquals(FOREST_ARGB, ContextCompat.getColor(context, R.color.forest))
    }

    // F7: the library header's new tokens (BOOKSHELF-0019) — a low-contrast grey regression
    // (risk R6 in the plan) must fail this test.
    @Test
    fun libraryHeaderTokensMatchTheDesignTokens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals(INK_MUTED_ARGB, ContextCompat.getColor(context, R.color.ink_muted))
        assertEquals(PAPER_BRIGHT_ARGB, ContextCompat.getColor(context, R.color.paper_bright))
        assertEquals(LINE_ARGB, ContextCompat.getColor(context, R.color.line))
    }
}
