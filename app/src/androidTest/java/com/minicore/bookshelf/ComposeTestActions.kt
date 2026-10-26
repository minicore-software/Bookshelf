package com.minicore.bookshelf

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction

/**
 * Invokes a node's own `OnClick` semantics action directly, instead of [performClick]'s real
 * simulated touch at the node's screen centre (BOOKSHELF-0022). Use this for a node whose
 * "scroll minimally into view" resting position can coincide with the always-on "Add a book"
 * FAB pinned to the bottom-end corner (e.g. a filter chip or a grid card reached via
 * `performScrollTo()`/`performScrollToNode()` on a screen that also seeds #20's rows) - a real
 * touch there would land on the FAB instead of the node under test. It still exercises the exact
 * same production click handler, just without depending on coordinate-based touch dispatch that
 * an unrelated overlay can intercept. Keep `performClick()` where a test's point is real
 * hit-testing (e.g. clicking the FAB itself).
 */
internal fun SemanticsNodeInteraction.performSemanticsClick(): SemanticsNodeInteraction =
    performSemanticsAction(SemanticsActions.OnClick)
