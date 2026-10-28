package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.core.coverimage.CoverImageStore
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.BookStatus
import com.minicore.bookshelf.core.model.Cover
import java.time.Clock
import javax.inject.Inject

/**
 * The single write-boundary for turning a [BookDraft] into a persisted [Book] (plan #31 section
 * 4.3, decision A6). Every entry point - this ticket's manual form, a future scan/search result -
 * goes through this use case, so a rule lives in exactly one place.
 *
 * Rules (A6), enforced here and nowhere else:
 * - `title.trim()` must be non-empty, else [IllegalArgumentException] - the UI makes this
 *   unreachable (the save button is disabled), so a throw here means a programming error, not a
 *   user outcome.
 * - `author.trim()`, blank -> `""` (the column stays `NOT NULL`, ADR-001).
 * - `publishedYear` must be `null` or in `1..9999`.
 * - `rating` must be `null` or in `1..5`.
 * - `comment.trim()`, blank -> `null` (#30 S7 mapper rule, applied here so the use case's
 *   output already matches what the repository persists).
 * - `status` is always [BookStatus.WantToRead] (AC2) - there is no status picker.
 * - `createdAt = clock.instant()`.
 * - `isbn`, `genre`, `pageCount`, `synopsis` and the memory fields are `null`.
 *
 * Cover commit order (plan step 2-4): a [CoverDraft.Image] is committed to permanent storage
 * *before* the insert; if the insert then throws, the committed file is moved back to its
 * original draft ref (F19) and the original exception is rethrown - so a retry, which still
 * references that same draft ref, can succeed, instead of finding neither file.
 */
class AddBookUseCase @Inject constructor(
    private val repository: LibraryRepository,
    private val coverImages: CoverImageStore,
    private val clock: Clock,
) {

    /** Returns the new book's id. Throws [IllegalArgumentException] on a rule violation. */
    suspend operator fun invoke(draft: BookDraft): Long {
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

        var committedCoverRef: String? = null
        var originalDraftRef: String? = null
        val cover: Cover = when (val coverDraft = draft.cover) {
            is CoverDraft.Preset -> Cover.Preset(coverDraft.preset)
            is CoverDraft.Image -> {
                val ref = coverImages.commit(coverDraft.draftRef)
                committedCoverRef = ref
                originalDraftRef = coverDraft.draftRef
                Cover.Image(ref)
            }
            is CoverDraft.Stored -> error("CoverDraft.Stored is unreachable from AddBookUseCase")
        }

        return try {
            repository.addBook(
                Book(
                    id = 0L,
                    title = title,
                    author = author,
                    isbn = null,
                    cover = cover,
                    genre = null,
                    pageCount = null,
                    publishedYear = draft.publishedYear,
                    status = BookStatus.WantToRead,
                    rating = draft.rating,
                    synopsis = null,
                    comment = comment,
                    createdAt = clock.instant(),
                ),
            )
        } catch (e: Exception) {
            val ref = committedCoverRef
            val draftRef = originalDraftRef
            if (ref != null && draftRef != null) coverImages.uncommit(ref, draftRef)
            throw e
        }
    }
}
