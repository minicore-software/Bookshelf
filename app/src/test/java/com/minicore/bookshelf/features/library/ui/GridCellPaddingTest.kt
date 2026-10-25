package com.minicore.bookshelf.features.library.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

private fun PaddingValues.start(): Dp = calculateStartPadding(LayoutDirection.Ltr)
private fun PaddingValues.end(): Dp = calculateEndPadding(LayoutDirection.Ltr)
private fun PaddingValues.bottom(): Dp = calculateBottomPadding()

private fun assertEqualsDp(expected: Dp, actual: Dp) {
    assertEquals(expected.value, actual.value, 0.01f)
}

/** AC3/plan FF4, F17: the gutter math behind the three grid columns. */
class GridCellPaddingTest {

    @Test
    fun column0_startsAtTheOuterMargin() {
        assertEqualsDp(20.dp, gridCellPadding(0).start())
    }

    @Test
    fun column2_endsAtTheOuterMargin() {
        assertEqualsDp(20.dp, gridCellPadding(2).end())
    }

    @Test
    fun adjacentColumns_gapSumsToFourteenDp() {
        val gap01 = gridCellPadding(0).end() + gridCellPadding(1).start()
        val gap12 = gridCellPadding(1).end() + gridCellPadding(2).start()

        assertEqualsDp(14.dp, gap01)
        assertEqualsDp(14.dp, gap12)
    }

    @Test
    fun everyColumn_hasTheSameTotalHorizontalInset_soContentWidthsAreEqual() {
        val insets = (0..2).map { gridCellPadding(it).start() + gridCellPadding(it).end() }

        assertEqualsDp(insets[0], insets[1])
        assertEqualsDp(insets[1], insets[2])
    }

    @Test
    fun everyColumn_hasAFourteenDpBottomPadding() {
        for (column in 0..2) {
            assertEqualsDp(14.dp, gridCellPadding(column).bottom())
        }
    }

    @Test
    fun columnIndexWrapsModuloThree() {
        assertEqualsDp(gridCellPadding(0).start(), gridCellPadding(3).start())
        assertEqualsDp(gridCellPadding(1).start(), gridCellPadding(4).start())
    }
}
