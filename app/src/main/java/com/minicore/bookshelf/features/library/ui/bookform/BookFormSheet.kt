package com.minicore.bookshelf.features.library.ui.bookform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The sheet chrome shared by Add and Edit (plan #33 decision E9): the full-screen scrim, the 92%
 * (minus the status bar) height ceiling, the title row with a close button, the scrollable body
 * slot, an optional error slot pinned above the footer, and the pinned footer slot itself. This
 * code is behaviour-preserving - it was `ui/addbook/AddBookRoute.kt`'s private `AddBookSheet`
 * before this extraction, and encodes three review rounds of fixes (scrim semantics order, IME +
 * pinned footer, 92% minus status bar) that must not drift between two copies.
 *
 * @param testTags the tags the caller's own screen already exposes to its tests - passed in so
 *   Add keeps its `add_book_*` tags and every #31 test stays green unmodified (#33 uses its own
 *   `edit_book_*` tags instead).
 */
data class BookFormSheetTestTags(
    val sheet: String,
    val scrim: String,
    val closeButton: String,
)

@Composable
fun BookFormSheet(
    title: String,
    closeContentDescription: String,
    testTags: BookFormSheetTestTags,
    onClose: () -> Unit,
    onScrimClick: () -> Unit,
    errorSlot: @Composable (() -> Unit)? = null,
    footer: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    // BoxWithConstraints (not LocalConfiguration.screenHeightDp, which is the whole device
    // screen) so the 92% ceiling tracks this composable's own available height - correct in
    // multi-window/freeform, not just full-screen.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The 92% ceiling applies after subtracting the status bar inset, so the sheet never
        // visually touches it (decorFitsSystemWindows = false means nothing does this for us
        // automatically).
        val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val maxHeight = (maxHeight - statusBarInset) * 0.92f

        // The scrim is a sibling *behind* the Surface, not an ancestor - a `clickable` on an
        // ancestor can't be "swallowed" by a disabled clickable on the Surface in Compose
        // foundation, so taps anywhere on the sheet (title, labels, gaps) would otherwise also
        // dismiss it. Hidden from the semantics tree since it carries no information of its own -
        // `clearAndSetSemantics {}` must come BEFORE (outside) `.clickable(...)` in the modifier
        // chain: Compose's semantics walk is inner-to-outer, so a `clearAndSetSemantics` placed
        // after `clickable` only clears what came before it and lets `clickable`'s own semantics
        // (and `onClick` action) re-apply on top, leaving a full-screen unlabeled TalkBack stop
        // that still dismisses the sheet (critical finding #3 round 2 regression; back/X/Cancel
        // already give TalkBack users an exit).
        Box(
            modifier = Modifier
                .testTag(testTags.scrim)
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clearAndSetSemantics {}
                .clickable(onClick = onScrimClick),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .testTag(testTags.sheet)
                .semantics { paneTitle = title },
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            // The IME inset shrinks the body (so the focused field scrolls fully above it) while
            // the footer - the last fixed-height child - lands pinned immediately above the IME,
            // never hidden or pushed off-screen.
            Column(modifier = Modifier.imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .testTag(testTags.closeButton)
                            .semantics { contentDescription = closeContentDescription },
                    ) {
                        Text("✕", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    body()
                }

                errorSlot?.invoke()

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                footer()
            }
        }
    }
}
