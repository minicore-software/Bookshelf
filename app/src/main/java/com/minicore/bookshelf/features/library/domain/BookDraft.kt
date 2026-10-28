package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.core.model.CoverPreset

/**
 * The in-progress "add a book" / "edit a book" form, before any write-boundary rule has been
 * applied (plan #31 section 4.2, decision A3; plan #33 decision E4 adds the memory fields).
 * Always seeded as [Empty] by Add; #33 Edit seeds it from the existing [com.minicore.bookshelf.core.model.Book]
 * instead. Pure Kotlin - no Android dependency, so it is reusable from [AddBookUseCase] /
 * `UpdateBookUseCase` and tested on the JVM without Robolectric.
 */
data class BookDraft(
    val title: String = "",
    val author: String = "",
    val publishedYear: Int? = null,
    val rating: Int? = null,
    val comment: String = "",
    val memoryWhy: String = "",
    val memoryWhere: String = "",
    val memoryWhat: String = "",
    val cover: CoverDraft = CoverDraft.Preset(CoverPreset.Preset1),
) {
    companion object {
        val Empty = BookDraft()
    }
}

/**
 * The form's cover choice: exactly one of a preset colour, an uploaded image draft or (Edit only)
 * the book's already-stored image, never more than one (epic D9). Mirrors
 * [com.minicore.bookshelf.core.model.Cover] but for a not-yet-saved draft.
 */
sealed interface CoverDraft {
    data class Preset(val preset: CoverPreset) : CoverDraft

    /**
     * A downscaled app-private draft file produced by
     * `com.minicore.bookshelf.core.coverimage.CoverImageStore.importDraft` - not yet a stored
     * cover. [draftRef] matches `cover-drafts/<uuid>.jpg`.
     */
    data class Image(val draftRef: String) : CoverDraft

    /**
     * The book's existing, already-committed image (plan #33 decision E5). [storedRef] matches
     * `covers/<uuid>.jpg`. No intent ever deletes or moves a `Stored` file; only a successful save
     * may delete it, and only once replaced. Unreachable from Add (`AddBookUseCase`/
     * `AddBookViewModel` treat it as a programming error).
     */
    data class Stored(val storedRef: String) : CoverDraft
}
