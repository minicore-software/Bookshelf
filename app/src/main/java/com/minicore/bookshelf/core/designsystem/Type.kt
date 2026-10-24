package com.minicore.bookshelf.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.minicore.bookshelf.R

/**
 * Fraunces, SemiBold, 24pt optical size, static instance.
 *
 * Provenance (see also `app/src/main/assets/licenses/Fraunces-OFL.txt`):
 * - Upstream: the Fraunces family by Undercase Type (SIL OFL 1.1),
 *   https://github.com/undercasetype/Fraunces, mirrored at
 *   https://github.com/google/fonts/blob/main/ofl/fraunces/Fraunces%5BSOFT,WONK,opsz,wght%5D.ttf
 * - Source file fetched: `Fraunces[SOFT,WONK,opsz,wght].ttf` from the `google/fonts` `main`
 *   branch (SHA-256 `177ff6c0f14e5550a3c624247cd1189611d4eb65d000b14944c63d967958abbb`).
 * - No static 24pt SemiBold cut is published upstream (the `undercasetype/Fraunces` v1.0 release
 *   only ships static 9pt/72pt/144pt cuts; Google Fonts ships the variable font only). This file
 *   was produced from the variable font above with `fonttools varLib.instancer`, pinning
 *   `opsz=24 wght=600 SOFT=0 WONK=0`, which drops the `fvar` table and yields a genuinely static
 *   instance (verified: no `fvar` table; `name` table reports family "Fraunces", subfamily
 *   "SemiBold"). Committed file SHA-256:
 *   `52cf8171cb5bd629cae5de80beb077762b22ae01478aa07ced585bf192ce110e`.
 */
private val Fraunces = FontFamily(Font(R.font.fraunces_semibold, FontWeight.SemiBold))

internal val BookshelfTypography = Typography().let { default ->
    default.copy(
        headlineMedium = default.headlineMedium.copy(
            fontFamily = Fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
        )
    )
}
