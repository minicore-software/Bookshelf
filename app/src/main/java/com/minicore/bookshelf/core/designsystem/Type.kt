package com.minicore.bookshelf.core.designsystem

import androidx.compose.material3.Typography
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
 * Inter, Regular, static instance.
 *
 * Provenance (see also `app/src/main/assets/licenses/Inter-OFL.txt`):
 * - Upstream: the Inter family by Rasmus Andersson (SIL OFL 1.1),
 *   https://github.com/rsms/inter, release `v4.1`
 *   (https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip).
 * - File: `extras/ttf/Inter-Regular.ttf` from that release zip — a static (no `fvar`) instance
 *   published directly by upstream, so no local instancing step is needed (unlike Fraunces,
 *   see below). SHA-256:
 *   `40d692fce188e4471e2b3cba937be967878f631ad3ebbbdcd587687c7ebe0c82`.
 * - `name` table family/subfamily: "Inter" / "Regular".
 * - Only `FontWeight.Normal` is declared here. Requesting any other weight from this file would
 *   make Compose synthesize a fake bold/narrow weight silently — see `InterFontFileTest`.
 */
private val Inter = FontFamily(Font(R.font.inter_regular, FontWeight.Normal))

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
        bodyLarge = default.bodyLarge.copy(fontFamily = Inter),
        // Library header wordmark tagline.
        bodyMedium = default.bodyMedium.copy(fontFamily = Inter, fontSize = 14.sp),
        bodySmall = default.bodySmall.copy(
            fontFamily = Inter,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.02.em,
        ),
        labelLarge = default.labelLarge.copy(fontFamily = Inter),
        labelMedium = default.labelMedium.copy(fontFamily = Inter),
        labelSmall = default.labelSmall.copy(fontFamily = Inter),
    )
}
