package com.minicore.bookshelf.features.library.ui.editbook

import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.BookDraft
import com.minicore.bookshelf.features.library.domain.CoverDraft
import com.minicore.bookshelf.features.library.ui.bookform.BookFormRules

/** [EditBookUiState.error] (plan #33 section 4.4, plan #34 section 4.4); cleared on the next edit
 * or save/remove attempt. */
enum class EditBookError { ImageImportFailed, SaveFailed, RemoveFailed }

/**
 * The Edit Book form's full state (plan #33 section 4.4). One immutable value, backed field by
 * field by `SavedStateHandle` in `EditBookViewModel`. [isLoaded] gates the body on the first
 * `ObserveBookDetailUseCase` emission (decision E10); [initialDraft] is `null` until then, so
 * [isDirty] is `false` while loading.
 */
data class EditBookUiState(
    val isLoaded: Boolean = false,
    val title: String = "",
    val author: String = "",
    val yearText: String = "",
    val rating: Int? = null,
    val comment: String = "",
    val memoryWhy: String = "",
    val memoryWhere: String = "",
    val memoryWhat: String = "",
    val cover: CoverDraft = CoverDraft.Preset(CoverPreset.Preset1),
    val isLentOut: Boolean = false,
    val isImportingImage: Boolean = false,
    val isSaving: Boolean = false,
    val isRemoving: Boolean = false,
    val borrowerName: String? = null,
    val error: EditBookError? = null,
    val initialDraft: BookDraft? = null,
) {
    /** `"0"` -> `null` (plan #33 section 4.4). */
    val publishedYear: Int? get() = BookFormRules.parsePublishedYear(yearText)

    val canSave: Boolean get() = isLoaded && title.isNotBlank() && !isSaving && !isImportingImage && !isRemoving

    val isDirty: Boolean get() = initialDraft != null && toDraft() != initialDraft

    fun toDraft(): BookDraft = BookDraft(
        title = title,
        author = author,
        publishedYear = publishedYear,
        rating = rating,
        comment = comment,
        memoryWhy = memoryWhy,
        memoryWhere = memoryWhere,
        memoryWhat = memoryWhat,
        cover = cover,
    )
}

/** What the sheet did when it closed (plan #33 section 4.1, plan #34 section 4.4). */
sealed interface EditBookResult {
    data object Saved : EditBookResult
    data object Cancelled : EditBookResult

    /** The row vanished before or during save/remove (plan #33 section 4.4, plan #34 decision
     * R7). */
    data object NotFound : EditBookResult

    /** The book was removed via the confirm dialog (plan #34 decision R8). */
    data object Removed : EditBookResult
}
