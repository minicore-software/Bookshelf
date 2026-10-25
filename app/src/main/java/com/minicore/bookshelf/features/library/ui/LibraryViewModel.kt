package com.minicore.bookshelf.features.library.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minicore.bookshelf.features.library.domain.LibraryFilter
import com.minicore.bookshelf.features.library.domain.ObserveLibraryGridUseCase
import com.minicore.bookshelf.features.library.domain.ObserveLibraryRowsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

private const val KEY_FILTER = "library_filter"

/**
 * Exposes the Library screen's rows and grid (plan R7/G3). `WhileSubscribed(5_000)` survives
 * rotation and restarts [com.minicore.bookshelf.features.library.domain.CurrentDateProvider]'s
 * midnight ticker when the app returns from background.
 *
 * The selected [LibraryFilter] is backed by [SavedStateHandle] so it survives process death.
 * [flatMapLatest] cancels the previous filter's Room query as soon as another chip is tapped, so
 * a slow, stale query can never win a race against a faster, newer one (plan G3) - the screen
 * always shows only the latest-selected filter's result. The selected chip is read from the
 * emitted [com.minicore.bookshelf.features.library.domain.LibraryGrid.filter], not from the raw
 * [SavedStateHandle] value, which keeps the chip, its count and its books from ever disagreeing
 * for a frame.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeLibraryRows: ObserveLibraryRowsUseCase,
    observeLibraryGrid: ObserveLibraryGridUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val filter = savedStateHandle.getStateFlow(KEY_FILTER, LibraryFilter.All)

    val uiState: StateFlow<LibraryUiState> = combine(
        observeLibraryRows(),
        filter.flatMapLatest { observeLibraryGrid(it) },
    ) { rows, grid -> LibraryUiState.Content(rows, grid) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState.Loading,
        )

    fun onFilterSelected(filter: LibraryFilter) {
        savedStateHandle[KEY_FILTER] = filter
    }
}
