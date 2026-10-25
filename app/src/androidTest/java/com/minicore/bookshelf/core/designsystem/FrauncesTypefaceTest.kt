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

// F4/FF2: the bundled font resource loads to a real, distinct Typeface — proof against a
// silent fallback to the system default or serif typeface.
@RunWith(AndroidJUnit4::class)
class FrauncesTypefaceTest {

    @Test
    fun frauncesFontResource_loadsAndDiffersFromSystemTypefaces() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val fraunces = ResourcesCompat.getFont(context, R.font.fraunces_semibold)

        assertNotNull(fraunces)
        assertNotEquals(Typeface.DEFAULT, fraunces)
        assertNotEquals(Typeface.SERIF, fraunces)
    }
}
