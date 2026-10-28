package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.core.coverimage.CoverImageStore
import com.minicore.bookshelf.core.model.Cover
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** [UpdateBookUseCase]'s outcome (plan #33 section 4.3). */
sealed interface UpdateBookResult {
    data object Updated : UpdateBookResult

    /** The row vanished before or during the save (plan #33 decision E7 step 2). */
    data object NotFound : UpdateBookResult
}

/**
 * The single write-boundary for turning a [BookDraft] into an edit of an existing [com.minicore.bookshelf.core.model.Book]
 * (plan #33 section 4.3, decision E6). Re-reads the row at save time and copies only the form's
 * fields onto it - `id`, `status`, `isbn`, `genre`, `pageCount`, `synopsis` and `createdAt` always
 * come from the fresh row, never from the form, so a concurrent change to a field this form does
 * not show survives an edit.
 *
 * Rules (E6), throwing [IllegalArgumentException] on violation (unreachable from the UI, so a
 * throw here is a programming error, not a user outcome):
 * - `title.trim()` must be non-empty.
 * - `author.trim()`, blank -> `""`.
 * - `publishedYear` must be `null` or in `1..9999`.
 * - `rating` must be `null` or in `1..5`.
 * - `comment` and each memory field `trim()`, blank -> `null`.
 *
 * Cover commit order (decision E7, binding, ADR-003): (1) validate; (2) read the current book,
 * `null` -> [UpdateBookResult.NotFound] with no file touched; (3) [CoverDraft.Image] -> commit;
 * (4) `updateBook`; on throw or `false` -> uncommit the new file, then rethrow / return
 * `NotFound`; (5) only after success, if the old cover was [Cover.Image] and the new cover is not
 * that same ref -> delete the old file (never throws). [CoverDraft.Stored] is never committed,
 * uncommitted or deleted by this use case - it must already equal the current row's own
 * [Cover.Image] ref, else [IllegalStateException] (a crafted/stale ref is a programming error).
 */
class UpdateBookUseCase @Inject constructor(
    private val repository: LibraryRepository,
    private val coverImages: CoverImageStore,
) {

    suspend operator fun invoke(bookId: Long, draft: BookDraft): UpdateBookResult {
        val title = draft.title.trim()
        require(title.isNotEmpty()) { "title must not be blank" }
        require(draft.publishedYear == null || draft.publishedYear in 1..9999) {
            "publishedYear must be null or in 1..9999, was ${draft.publishedYear}"
        }
        require(draft.rating == null || draft.rating in 1..5) {
            "rating must be null or in 1..5, was ${draft.rating}"
        }
        val author = draft.author.trim()
        val comment = draft.comment.trim().ifEmpty { null }
        val memoryWhy = draft.memoryWhy.trim().ifEmpty { null }
        val memoryWhere = draft.memoryWhere.trim().ifEmpty { null }
        val memoryWhat = draft.memoryWhat.trim().ifEmpty { null }

        val current = repository.observeBook(bookId).first() ?: return UpdateBookResult.NotFound
        val previousCover = current.cover

        var committedCoverRef: String? = null
        var originalDraftRef: String? = null
        val newCover: Cover = when (val coverDraft = draft.cover) {
            is CoverDraft.Preset -> Cover.Preset(coverDraft.preset)
            is CoverDraft.Stored -> {
                check(previousCover is Cover.Image && previousCover.uri == coverDraft.storedRef) {
                    "CoverDraft.Stored(${coverDraft.storedRef}) does not match the current row's cover"
                }
                previousCover
            }
            is CoverDraft.Image -> {
                val ref = coverImages.commit(coverDraft.draftRef)
                committedCoverRef = ref
                originalDraftRef = coverDraft.draftRef
                Cover.Image(ref)
            }
        }

        val updated = current.copy(
            title = title,
            author = author,
            publishedYear = draft.publishedYear,
            rating = draft.rating,
            comment = comment,
            memoryWhy = memoryWhy,
            memoryWhere = memoryWhere,
            memoryWhat = memoryWhat,
            cover = newCover,
        )

        val result = try {
            if (repository.updateBook(updated)) UpdateBookResult.Updated else UpdateBookResult.NotFound
        } catch (e: Exception) {
            val ref = committedCoverRef
            val draftRef = originalDraftRef
            if (ref != null && draftRef != null) coverImages.uncommit(ref, draftRef)
            throw e
        }

        if (result is UpdateBookResult.NotFound) {
            val ref = committedCoverRef
            val draftRef = originalDraftRef
            if (ref != null && draftRef != null) coverImages.uncommit(ref, draftRef)
            return result
        }

        if (previousCover is Cover.Image && newCover != previousCover) {
            coverImages.delete(previousCover.uri)
        }

        return result
    }
}
