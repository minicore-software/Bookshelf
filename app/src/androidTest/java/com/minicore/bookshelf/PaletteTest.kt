package com.minicore.bookshelf

import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

private const val INK_ARGB = 0xFF231F20.toInt()
private const val FOREST_ARGB = 0xFF2F4538.toInt()

// F7/FF4: pins the AC's ink/forest token values, the same way SplashThemeTest pins paper.
@RunWith(AndroidJUnit4::class)
class PaletteTest {

    @Test
    fun inkAndForestColorsMatchTheDesignTokens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals(INK_ARGB, ContextCompat.getColor(context, R.color.ink))
        assertEquals(FOREST_ARGB, ContextCompat.getColor(context, R.color.forest))
    }
}
