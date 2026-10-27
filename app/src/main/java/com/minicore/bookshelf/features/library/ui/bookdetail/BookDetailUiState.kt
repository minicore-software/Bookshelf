package com.minicore.bookshelf.features.library.ui.bookdetail

import com.minicore.bookshelf.features.library.domain.model.BookDetail

/**
 * The Book Detail modal's view state (plan #32 section 4.3). [NotFound] covers both a stale
 * back-stack id and a book deleted while the modal is open (QA F17) - the route reacts to it by
 * calling `onClose()` exactly once.
 */
sealed interface BookDetailUiState {
    data object Loading : BookDetailUiState
    data class Loaded(val detail: BookDetail) : BookDetailUiState
    data object NotFound : BookDetailUiState
}
