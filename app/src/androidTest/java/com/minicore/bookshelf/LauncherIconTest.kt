package com.minicore.bookshelf

import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {

    @Test
    fun applicationHasALauncherIcon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertTrue(applicationInfo.icon != 0)
    }

    @Test
    fun launcherIconResolvesToAnAdaptiveIconWithForegroundAndBackground() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)

        assertTrue(drawable is AdaptiveIconDrawable)
        val adaptiveIcon = drawable as AdaptiveIconDrawable
        assertNotNull(adaptiveIcon.foreground)
        assertNotNull(adaptiveIcon.background)
    }
}
