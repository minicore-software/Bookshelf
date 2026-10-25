package com.minicore.bookshelf.core.designsystem

import android.graphics.Typeface
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minicore.bookshelf.R
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// FF2b: the bundled font resource loads to a real, distinct Typeface — proof against a silent
// fallback to the system default typeface. Mirrors [FrauncesTypefaceTest].
@RunWith(AndroidJUnit4::class)
class InterTypefaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun interFontResource_loadsAndDiffersFromSystemTypefaces() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val inter = ResourcesCompat.getFont(context, R.font.inter_regular)

        assertNotNull(inter)
        assertNotEquals(Typeface.DEFAULT, inter)
    }

    // BOOKSHELF-0020 (plan section 3): the SemiBold resource loads to a real, distinct Typeface.
    @Test
    fun interSemiBoldFontResource_loadsAndDiffersFromSystemTypefaces() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val interSemiBold = ResourcesCompat.getFont(context, R.font.inter_semibold)

        assertNotNull(interSemiBold)
        assertNotEquals(Typeface.DEFAULT, interSemiBold)
    }

    // F22: a SemiBold sample must measure wider than the Regular sample at the same size and
    // text — proof the real SemiBold asset is used, not a synthesized fake bold from Regular.
    @Test
    fun semiBoldSample_measuresWider_thanRegularSample_atSameSizeAndText() {
        var regularWidthPx = 0f
        var semiBoldWidthPx = 0f

        composeRule.setContent {
            val textMeasurer = rememberTextMeasurer()
            val text = "Overdue 2 days"
            val regularFamily = FontFamily(
                androidx.compose.ui.text.font.Font(R.font.inter_regular, FontWeight.Normal)
            )
            val semiBoldFamily = FontFamily(
                androidx.compose.ui.text.font.Font(R.font.inter_semibold, FontWeight.SemiBold)
            )

            regularWidthPx = textMeasurer.measure(
                AnnotatedString(text),
                style = TextStyle(fontFamily = regularFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
            ).size.width.toFloat()

            semiBoldWidthPx = textMeasurer.measure(
                AnnotatedString(text),
                style = TextStyle(fontFamily = semiBoldFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            ).size.width.toFloat()
        }
        composeRule.waitForIdle()

        assertTrue(
            "SemiBold ($semiBoldWidthPx px) must measure wider than Regular ($regularWidthPx px) " +
                "at the same size/text, or the weight was synthesized rather than loaded from " +
                "the real asset",
            semiBoldWidthPx > regularWidthPx
        )
    }
}
