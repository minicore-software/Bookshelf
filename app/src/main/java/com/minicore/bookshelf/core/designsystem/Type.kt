package com.minicore.bookshelf.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.minicore.bookshelf.R

/**
 * Fraunces, SemiBold, 24pt optical size, static instance.
 *
 * Provenance (see also `app/src/main/assets/licenses/Fraunces-OFL.txt`):
 * - Upstream: the Fraunces family by Undercase Type (SIL OFL 1.1),
 *   https://github.com/undercasetype/Fraunces, mirrored at
 *   https://github.com/google/fonts/blob/main/ofl/fraunces/Fraunces%5BSOFT,WONK,opsz,wght%5D.ttf
 * - No static 24pt SemiBold cut is published upstream (the `undercasetype/Fraunces` v1.0 release
 *   only ships static 9pt/72pt/144pt cuts; Google Fonts ships the variable font only), so this
 *   file is produced from the variable font by `tools/fraunces-font/generate.py`: pin
 *   `opsz=24 wght=600 SOFT=0 WONK=0` with `fonttools.varLib.instancer` (`updateFontNames=False`,
 *   since the font's STAT table has no Axis Value for `opsz=24` — only 9/72/144 — so
 *   `updateFontNames=True` fails with "Cannot find Axis Values {'opsz': 24.0}"), drop the
 *   resulting STAT table, drop the now-stale STAT axis-value name records, and overwrite the
 *   family/subfamily name records (IDs 1, 2, 3, 4, 5, 6, 16, 17) to "Fraunces"/"SemiBold",
 *   following Google Fonts' own naming convention for Fraunces's static optical-size cuts. See
 *   that script for the exact, pinned, reproducible steps (source commit SHA, fonttools
 *   version, source SHA-256) — this comment only summarizes them.
 * - Run `python tools/fraunces-font/generate.py check` to verify the committed file's
 *   invariants (no `fvar`/`STAT`, family "Fraunces", subfamily "SemiBold") against a fresh
 *   instance built from the pinned source.
 */
private val Fraunces = FontFamily(Font(R.font.fraunces_semibold, FontWeight.SemiBold))

/**
 * Inter, Regular and SemiBold, static instances.
 *
 * Provenance (see also `app/src/main/assets/licenses/Inter-OFL.txt`):
 * - Upstream: the Inter family by Rasmus Andersson (SIL OFL 1.1),
 *   https://github.com/rsms/inter, release `v4.1`
 *   (https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip).
 * - Regular file: `extras/ttf/Inter-Regular.ttf` from that release zip — a static (no `fvar`)
 *   instance published directly by upstream, so no local instancing step is needed (unlike
 *   Fraunces, see below). SHA-256:
 *   `40d692fce188e4471e2b3cba937be967878f631ad3ebbbdcd587687c7ebe0c82`.
 * - `name` table family/subfamily: "Inter" / "Regular".
 * - SemiBold file (BOOKSHELF-0020, plan section 3): `extras/ttf/Inter-SemiBold.ttf` from the
 *   **same** `v4.1` release zip — also a static, non-`fvar` instance. SHA-256:
 *   `78a843fade9d4612a5567302fb595b56976eb5fcebf4fea5a5912d638bafcde3`. `name` table
 *   family/subfamily: "Inter" / "SemiBold". Used by the Library rows' trailing labels, borrower
 *   text and overdue caption (plan R6) — the weight is what makes "overdue" distinct from
 *   "upcoming" without relying on colour alone (WCAG 1.4.1).
 * - Only `FontWeight.Normal` and `FontWeight.SemiBold` are declared here, each from its own
 *   file. Requesting any other weight (or SemiBold from the Regular file) would make Compose
 *   synthesize a fake bold/narrow weight silently — see `InterFontFileTest`.
 */
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

internal val BookshelfTypography = Typography().let { default ->
    default.copy(
        headlineMedium = default.headlineMedium.copy(
            fontFamily = Fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
        ),
        // Library header wordmark title (BOOKSHELF-0019, plan H9): Fraunces SemiBold, not Bold —
        // only a SemiBold cut is bundled (see Fraunces KDoc above), so requesting Bold here would
        // synthesize a fake-bold weight.
        titleLarge = default.titleLarge.copy(
            fontFamily = Fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            letterSpacing = (-0.01).em,
        ),
        // Library rows (BOOKSHELF-0020, plan R6): section titles ("Currently reading", "Lent out").
        titleMedium = default.titleMedium.copy(
            fontFamily = Fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        ),
        // Library rows: cover title overlay. Upright, not italic — see BookCover KDoc (plan R3).
        titleSmall = default.titleSmall.copy(
            fontFamily = Fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 17.5.sp,
        ),
        bodyLarge = default.bodyLarge.copy(fontFamily = Inter),
        // Library header wordmark tagline.
        bodyMedium = default.bodyMedium.copy(fontFamily = Inter, fontSize = 14.sp),
        // letterSpacing is Sp, not Em, on purpose: OutlinedTextField/TextField lerp the label's
        // style between bodyLarge and bodySmall as focus changes, and TextUnit.lerp throws if the
        // two operands don't share a unit type. bodyLarge.letterSpacing is Material3's own
        // default (Sp), so bodySmall must match. 0.25.sp is the Sp equivalent of the previous
        // 0.02.em at this style's own fixed fontSize (0.02em * 12.5sp = 0.25sp) — same visual
        // spacing, compatible unit. See docs/specs/44-outlined-textfield-sp-em-crash.md.
        bodySmall = default.bodySmall.copy(
            fontFamily = Inter,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.25.sp,
        ),
        labelLarge = default.labelLarge.copy(fontFamily = Inter),
        // Library rows: trailing section labels ("2 books", "See all · 3").
        labelMedium = default.labelMedium.copy(
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        ),
        // Library rows: due/overdue caption. Overdue uses `.copy(fontWeight = SemiBold)` on top
        // of this (plan R6) — weight, not just colour, signals overdue (WCAG 1.4.1).
        labelSmall = default.labelSmall.copy(
            fontFamily = Inter,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        ),
    )
}

/** Text styles with no [Typography] slot of their own (plan R6, G8). */
object LibraryTextStyles {
    val borrower: TextStyle
        @Composable get() = BookshelfTypography.labelSmall.copy(
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
        )

    // Library grid filter chips (BOOKSHELF-0021, plan G8). The selected chip layers
    // `.copy(fontWeight = FontWeight.SemiBold)` on top - a real bundled weight, not synthesized.
    val chip: TextStyle
        @Composable get() = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
        )

    val gridTitle: TextStyle
        @Composable get() = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.Normal,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
        )

    val gridLentCaption: TextStyle
        @Composable get() = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            lineHeight = 13.sp,
        )
}
