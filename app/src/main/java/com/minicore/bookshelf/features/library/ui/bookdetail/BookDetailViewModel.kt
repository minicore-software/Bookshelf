package com.minicore.bookshelf.features.library.ui.bookdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minicore.bookshelf.features.library.domain.ObserveBookDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The `book/{bookId}` route argument name (plan #32 section 4.1) - duplicated here, not
 * imported from `navigation/BookshelfDestinations`, so `ui/bookdetail/` stays referenced only
 * from `navigation/` in the other direction (FF-V1) and never imports it back. */
private const val KEY_BOOK_ID = "bookId"

/**
 * Exposes the Book Detail modal's state (plan #32 decision V8). [bookId] is read once from
 * [SavedStateHandle] - set by the nav graph's `book/{bookId}` route argument - so it survives
 * process death (QA risk "process death with the modal open"). `WhileSubscribed(5_000)` mirrors
 * [com.minicore.bookshelf.features.library.ui.LibraryViewModel]'s rotation/background tolerance.
 */
@HiltViewModel
class BookDetailViewModel @Inject constructor(
    observeBookDetail: ObserveBookDetailUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[KEY_BOOK_ID]) {
        "BookDetailViewModel requires a $KEY_BOOK_ID saved-state argument"
    }

    val uiState: StateFlow<BookDetailUiState> = observeBookDetail(bookId)
        .map { detail -> if (detail != null) BookDetailUiState.Loaded(detail) else BookDetailUiState.NotFound }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BookDetailUiState.Loading,
        )
}
