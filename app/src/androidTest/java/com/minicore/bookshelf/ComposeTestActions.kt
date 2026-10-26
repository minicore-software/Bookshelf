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
 *
 * This overlap is only ever temporary - `LibraryScreen`'s `FabClearance` bottom content padding
 * means the FAB is clear of the content again once scrolled all the way to the end. Using this
 * helper to reach a node under the FAB therefore does **not** prove the FAB never permanently
 * occludes that node; it only sidesteps the overlap so the click still reaches its target. A
 * real hit-testing/occlusion regression (the FAB permanently covering content, e.g. missing or
 * wrong `FabClearance`) is instead guarded by an FF4-style bounds assertion - compare
 * `LibraryEmptyStateTest.populatedLibrary_scrolledToBottom_lastCardNeverOverlapsFab`, which
 * asserts the scrolled-to node's `boundsInRoot.bottom <= fab.boundsInRoot.top`. Don't reuse this
 * helper as a substitute for that assertion.
 */
internal fun SemanticsNodeInteraction.performSemanticsClick(): SemanticsNodeInteraction =
    performSemanticsAction(SemanticsActions.OnClick)
