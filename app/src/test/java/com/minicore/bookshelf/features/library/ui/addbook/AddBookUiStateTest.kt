package com.minicore.bookshelf.features.library.ui.addbook

import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.CoverDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AddBookUiState.isDirty] backs the discard-confirm policy (UX spec section 2, docs/ux/decisions
 * .md:7): a clean/untouched draft dismisses silently, a dirty one prompts first. Covered directly
 * on the state's own [AddBookUiState.toDraft]/`BookDraft.Empty` comparison, with no ViewModel or
 * coroutine involved.
 */
class AddBookUiStateTest {

    @Test
    fun isDirty_falseForTheUntouchedDefaultState() {
        assertFalse(AddBookUiState().isDirty)
    }

    @Test
    fun isDirty_trueOnceTitleChanges() {
        assertTrue(AddBookUiState(title = "Dune").isDirty)
    }

    @Test
    fun isDirty_trueOnceAuthorChanges() {
        assertTrue(AddBookUiState(author = "Frank Herbert").isDirty)
    }

    @Test
    fun isDirty_trueOnceYearChanges() {
        assertTrue(AddBookUiState(yearText = "1965").isDirty)
    }

    @Test
    fun isDirty_trueOnceRatingChanges() {
        assertTrue(AddBookUiState(rating = 3).isDirty)
    }

    @Test
    fun isDirty_trueOnceCommentChanges() {
        assertTrue(AddBookUiState(comment = "A classic").isDirty)
    }

    @Test
    fun isDirty_trueOnceAnImageIsPicked() {
        assertTrue(AddBookUiState(cover = CoverDraft.Image("cover-drafts/picked.jpg")).isDirty)
    }

    @Test
    fun isDirty_falseAfterRevertingEveryFieldBackToItsInitialValue() {
        val changed = AddBookUiState(
            title = "Dune",
            author = "Frank Herbert",
            yearText = "1965",
            rating = 3,
            comment = "A classic",
            cover = CoverDraft.Image("cover-drafts/picked.jpg"),
        )
        assertTrue(changed.isDirty)

        val reverted = changed.copy(
            title = "",
            author = "",
            yearText = "",
            rating = null,
            comment = "",
            cover = CoverDraft.Preset(CoverPreset.Preset1),
        )
        assertFalse(reverted.isDirty)
    }
}
