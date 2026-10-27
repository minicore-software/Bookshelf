package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.core.model.CoverPreset

/**
 * The in-progress "add a book" form, before any write-boundary rule has been applied
 * (plan #31 section 4.2, decision A3). Always seeded as [Empty] by this ticket; a future
 * scan/search result hands over a pre-filled [BookDraft] instead, and #33 Edit reuses the same
 * type. Pure Kotlin - no Android dependency, so it is reusable from [AddBookUseCase] and tested
 * on the JVM without Robolectric.
 */
data class BookDraft(
    val title: String = "",
    val author: String = "",
    val publishedYear: Int? = null,
    val rating: Int? = null,
    val comment: String = "",
    val cover: CoverDraft = CoverDraft.Preset(CoverPreset.Preset1),
) {
    companion object {
        val Empty = BookDraft()
    }
}

/**
 * The form's cover choice: exactly one of a preset colour or an uploaded image draft, never both
 * (epic D9). Mirrors [com.minicore.bookshelf.core.model.Cover] but for a not-yet-saved draft.
 */
sealed interface CoverDraft {
    data class Preset(val preset: CoverPreset) : CoverDraft

    /**
     * A downscaled app-private draft file produced by
     * `com.minicore.bookshelf.core.coverimage.CoverImageStore.importDraft` - not yet a stored
     * cover. [draftRef] matches `cover-drafts/<uuid>.jpg`.
     */
    data class Image(val draftRef: String) : CoverDraft
}
