package com.minicore.bookshelf.features.library.domain

import com.minicore.bookshelf.core.coverimage.CoverImageStore
import com.minicore.bookshelf.core.model.Cover
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** [RemoveBookUseCase]'s outcome (plan #34 section 4.2). */
sealed interface RemoveBookResult {
    data object Removed : RemoveBookResult

    /** The row did not exist before the delete, or the delete itself found nothing to remove. */
    data object NotFound : RemoveBookResult
}

/**
 * The single write-boundary for removing a book (plan #34 section 4.2, decision R3). Order,
 * binding: (1) read the current row - `null` -> [RemoveBookResult.NotFound], no file touched;
 * (2) [com.minicore.bookshelf.features.library.domain.LibraryRepository.removeBook] - its loans
 * cascade through the existing `loans.book_id -> books.id ON DELETE CASCADE` FK in the same
 * `DELETE` statement (decision R2) - `false` -> [RemoveBookResult.NotFound], no file touched; a
 * throw propagates and no file is touched; (3) only after success, if the removed book's cover
 * was [Cover.Image], delete the stored file (idempotent, never throws). Never touches a draft -
 * draft cleanup belongs to the caller's UI state.
 *
 * Step 3 runs inside [NonCancellable] (review finding): the row is already gone by then, so a
 * cancellation racing the delete call (e.g. the caller's `viewModelScope` cleared between the row
 * delete and this coroutine resuming - the exact Detail/Edit pop race plan #34 R7/R8 describes)
 * must not leave the file orphaned. ADR-003's "delete the file after the row delete" promise
 * only holds if this step cannot be skipped once the row delete has committed.
 */
class RemoveBookUseCase @Inject constructor(
    private val repository: LibraryRepository,
    private val coverImages: CoverImageStore,
) {

    suspend operator fun invoke(bookId: Long): RemoveBookResult {
        val current = repository.observeBook(bookId).first() ?: return RemoveBookResult.NotFound
        if (!repository.removeBook(bookId)) return RemoveBookResult.NotFound

        val cover = current.cover
        if (cover is Cover.Image) withContext(NonCancellable) { coverImages.delete(cover.uri) }

        return RemoveBookResult.Removed
    }
}
