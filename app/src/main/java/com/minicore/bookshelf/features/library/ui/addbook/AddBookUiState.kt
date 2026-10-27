package com.minicore.bookshelf.features.library.ui.addbook

import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.BookDraft
import com.minicore.bookshelf.features.library.domain.CoverDraft

/** [AddBookUiState.error] (plan #31 section 4.2); cleared on the next edit or save attempt. */
enum class AddBookError { ImageImportFailed, SaveFailed }

/**
 * The Add Book form's full state (plan #31 section 4.2). One immutable value, backed field by
 * field by `SavedStateHandle` in [AddBookViewModel].
 */
data class AddBookUiState(
    val title: String = "",
    val author: String = "",
    val yearText: String = "",
    val rating: Int? = null,
    val comment: String = "",
    val cover: CoverDraft = CoverDraft.Preset(CoverPreset.Preset1),
    val isImportingImage: Boolean = false,
    val isSaving: Boolean = false,
    val error: AddBookError? = null,
) {
    /** `"0"` -> `null` (plan #31 section 4.4 / UX spec section 4). */
    val publishedYear: Int? get() = yearText.toIntOrNull()?.takeIf { it in 1..9999 }

    val canSave: Boolean get() = title.isNotBlank() && !isSaving && !isImportingImage

    val isDirty: Boolean get() = toDraft() != BookDraft.Empty

    fun toDraft(): BookDraft = BookDraft(
        title = title,
        author = author,
        publishedYear = publishedYear,
        rating = rating,
        comment = comment,
        cover = cover,
    )
}

/** What the sheet did when it closed (plan #31 section 4.2). */
sealed interface AddBookResult {
    data class Saved(val bookId: Long) : AddBookResult
    data object Cancelled : AddBookResult
}
