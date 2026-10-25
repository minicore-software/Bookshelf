package com.minicore.bookshelf.features.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minicore.bookshelf.features.library.domain.ObserveLibraryRowsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the Library screen's rows (plan R7). `WhileSubscribed(5_000)` survives rotation and
 * restarts [com.minicore.bookshelf.features.library.domain.CurrentDateProvider]'s midnight
 * ticker when the app returns from background.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeLibraryRows: ObserveLibraryRowsUseCase,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = observeLibraryRows()
        .map<_, LibraryUiState> { LibraryUiState.Content(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState.Loading,
        )
}
