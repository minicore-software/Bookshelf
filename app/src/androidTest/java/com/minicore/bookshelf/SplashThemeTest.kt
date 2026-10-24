package com.minicore.bookshelf

import androidx.core.content.ContextCompat
import androidx.core.splashscreen.R as SplashScreenR
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

private const val PAPER_ARGB = 0xFFF3EEE3.toInt()

@RunWith(AndroidJUnit4::class)
class SplashThemeTest {

    // F5: proves the "to settle" item (plan S5) without aliasing the two resources.
    @Test
    fun paperColorMatchesLauncherIconBackgroundColor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val paper = ContextCompat.getColor(context, R.color.paper)
        val iconBackground = ContextCompat.getColor(context, R.color.ic_launcher_background)

        assertEquals(PAPER_ARGB, paper)
        assertEquals(PAPER_ARGB, iconBackground)
    }

    // F4: Theme.Bookshelf.Splash's background colors, icon and post-splash theme.
    @Test
    fun splashThemeResolvesToPaperIconAndPostSplashTheme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val background =
            resolveSplashColorAttr(context, SplashScreenR.attr.windowSplashScreenBackground)
        val iconBackground =
            resolveSplashColorAttr(context, SplashScreenR.attr.windowSplashScreenIconBackgroundColor)
        val icon = resolveSplashResourceIdAttr(context, SplashScreenR.attr.windowSplashScreenAnimatedIcon)
        val postSplashTheme =
            resolveSplashResourceIdAttr(context, SplashScreenR.attr.postSplashScreenTheme)

        assertEquals(PAPER_ARGB, background)
        assertEquals(PAPER_ARGB, iconBackground)
        assertEquals(R.mipmap.ic_launcher, icon)
        assertEquals(R.style.Theme_Bookshelf, postSplashTheme)
    }

    private fun resolveSplashColorAttr(context: android.content.Context, attr: Int): Int {
        val typedArray =
            context.obtainStyledAttributes(R.style.Theme_Bookshelf_Splash, intArrayOf(attr))
        try {
            return typedArray.getColor(0, 0)
        } finally {
            typedArray.recycle()
        }
    }

    private fun resolveSplashResourceIdAttr(context: android.content.Context, attr: Int): Int {
        val typedArray =
            context.obtainStyledAttributes(R.style.Theme_Bookshelf_Splash, intArrayOf(attr))
        try {
            return typedArray.getResourceId(0, 0)
        } finally {
            typedArray.recycle()
        }
    }

    // F6: no near-white flash between the system splash and Compose content (plan S6).
    @Test
    fun bookshelfThemeWindowBackgroundResolvesToPaper() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val typedArray = context.obtainStyledAttributes(
            R.style.Theme_Bookshelf,
            intArrayOf(android.R.attr.windowBackground)
        )
        val windowBackground: Int
        try {
            windowBackground = typedArray.getColor(0, 0)
        } finally {
            typedArray.recycle()
        }

        assertEquals(PAPER_ARGB, windowBackground)
    }

    // F7: the manifest wires MainActivity to the splash theme, not Theme.Bookshelf directly.
    @Test
    fun mainActivityManifestThemeIsSplashTheme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activityInfo = context.packageManager.getActivityInfo(
            android.content.ComponentName(context, MainActivity::class.java),
            0
        )

        assertEquals(R.style.Theme_Bookshelf_Splash, activityInfo.theme)
    }
}
