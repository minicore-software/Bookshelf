package com.minicore.bookshelf.core.designsystem

import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F24/FF3 (plan section 6): parses `res/values/colors.xml` from disk (mirrors [LayeringTest]'s
 * own file-reading style) and asserts WCAG contrast >= 4.5:1 for the Library rows' text tokens
 * against their background, so a colour change fails a test rather than only an audit.
 */
class LibraryContrastTest {

    private val colors: Map<String, String> by lazy {
        val file = File("src/main/res/values/colors.xml")
        val regex = Regex("""<color name="([^"]+)">(#[0-9A-Fa-f]{6})</color>""")
        regex.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun contrastRatio(hex1: String, hex2: String): Double {
        val l1 = relativeLuminance(hex1)
        val l2 = relativeLuminance(hex2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(hex: String): Double {
        val r = channel(hex, 1)
        val g = channel(hex, 3)
        val b = channel(hex, 5)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channel(hex: String, startIndex: Int): Double {
        val value = hex.substring(startIndex, startIndex + 2).toInt(16) / 255.0
        return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    private fun assertAA(name: String, foreground: String, background: String) {
        val ratio = contrastRatio(colors.getValue(foreground), colors.getValue(background))
        assertTrue(
            "$foreground on $background must be >= 4.5:1 for AA, was %.2f:1".format(ratio),
            ratio >= 4.5,
        )
    }

    @Test
    fun dueUpcoming_onPaper_passesAA() = assertAA("due_upcoming", "due_upcoming", "paper")

    @Test
    fun overdue_onPaper_passesAA() = assertAA("overdue", "overdue", "paper")

    @Test
    fun inkSoft_onPaper_passesAA() = assertAA("ink_soft", "ink_soft", "paper")

    @Test
    fun everyCoverBackground_passesAA_againstItsAssignedForeground() {
        // Mirrors Color.kt's bookshelfExtendedColors() pairing (plan R2).
        val pairs = listOf(
            "cover_1_bg" to "cover_cream",
            "cover_2_bg" to "cover_cream",
            "cover_3_bg" to "ink",
            "cover_4_bg" to "cover_cream",
            "cover_5_bg" to "cover_cream",
            "cover_6_bg" to "ink",
        )
        for ((background, foreground) in pairs) {
            val ratio = contrastRatio(colors.getValue(background), colors.getValue(foreground))
            assertTrue(
                "$foreground on $background must be >= 4.5:1 for AA, was %.2f:1".format(ratio),
                ratio >= 4.5,
            )
        }
    }
}
