package com.minicore.bookshelf.core.designsystem

import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fitness function for BOOKSHELF-0044: Material3's `OutlinedTextField`/`TextField` floating
 * label `lerp()`s between `typography.bodyLarge` and `typography.bodySmall` as focus changes,
 * and `TextUnit.lerp` throws `IllegalArgumentException` if the two operands don't share a unit
 * type (`Sp` vs `Em`). This asserts `fontSize`/`letterSpacing` unit-type parity across every
 * [BookshelfTypography] slot pair known to be interpolated by Material3 or this app's own code,
 * so a future edit to either style in a pair can't silently reintroduce the crash - a single
 * happy-path "open Add Book, don't crash" smoke test would only prove this one path, not the
 * underlying invariant. See docs/specs/44-outlined-textfield-sp-em-crash.md.
 *
 * `titleLarge`/`titleMedium` has the identical latent mismatch (`titleLarge.letterSpacing` is
 * `Em`, `titleMedium.letterSpacing` is Material3's default `Sp`) but nothing lerps between them
 * yet, so it is deliberately left out of [interpolatedPairs] - this ticket's fix only covers
 * `bodyLarge`/`bodySmall`. Add that pair here the day something animates between titles (e.g. a
 * collapsing app bar); until then asserting it would just be a pre-existing-and-accepted failure
 * unrelated to this fix.
 */
class TypographyUnitParityTest {

    private val interpolatedPairs: List<Triple<String, TextStyle, TextStyle>> = listOf(
        Triple("bodyLarge/bodySmall", BookshelfTypography.bodyLarge, BookshelfTypography.bodySmall),
    )

    @Test
    fun interpolatedStylePairs_shareFontSizeUnitType() {
        for ((name, a, b) in interpolatedPairs) {
            assertEquals(
                "$name: fontSize unit types must match for TextUnit.lerp to work",
                a.fontSize.type,
                b.fontSize.type,
            )
        }
    }

    @Test
    fun interpolatedStylePairs_shareLetterSpacingUnitType() {
        for ((name, a, b) in interpolatedPairs) {
            assertEquals(
                "$name: letterSpacing unit types must match for TextUnit.lerp to work",
                a.letterSpacing.type,
                b.letterSpacing.type,
            )
        }
    }
}
