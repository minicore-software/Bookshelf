package com.minicore.bookshelf.core.designsystem

import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.R
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

// FF2b: the bundled font resource loads to a real, distinct Typeface — proof against a silent
// fallback to the system default typeface. Mirrors [FrauncesTypefaceTest].
@RunWith(AndroidJUnit4::class)
class InterTypefaceTest {

    @Test
    fun interFontResource_loadsAndDiffersFromSystemTypefaces() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val inter = ResourcesCompat.getFont(context, R.font.inter_regular)

        assertNotNull(inter)
        assertNotEquals(Typeface.DEFAULT, inter)
    }
}
