package com.minicore.bookshelf.features.library.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minicore.bookshelf.features.library.domain.LibraryFilter
import com.minicore.bookshelf.features.library.domain.ObserveLibraryGridUseCase
import com.minicore.bookshelf.features.library.domain.ObserveLibraryRowsUseCase
import com.minicore.bookshelf.features.library.domain.RemoveBookResult
import com.minicore.bookshelf.features.library.domain.RemoveBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_FILTER = "library_filter"
private const val KEY_REMOVE_BOOK_ID = "library_remove_book_id"
private const val KEY_REMOVE_BOOK_TITLE = "library_remove_book_title"
private const val KEY_REMOVE_BOOK_BORROWER = "library_remove_book_borrower"

/** The Library grid card's pending removal (plan #35 L5) - a snapshot, not a value derived live
 * from the grid, so a stale id reused by Room's `autoGenerate` after a delete can never resurrect
 * a ghost dialog for the wrong book (plan 4.2). */
data class RemoveBookPrompt(val bookId: Long, val title: String, val borrowerName: String?)

/** One-shot events the Library screen surfaces as a Snackbar (plan #35 L7). */
sealed interface LibraryEvent {
    data object RemoveFailed : LibraryEvent
}

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
 *
 * Owns the grid card remove flow (plan #35 L5-L7): [removePrompt] is backed by [SavedStateHandle]
 * field by field (a plain data class is not `Parcelable`, so it cannot go through
 * `SavedStateHandle.getStateFlow` directly - mirrors `EditBookViewModel`'s own field-by-field
 * pattern). [onRemoveRequested] is called by either the long-press popover or the "⋮" menu - both
 * call the very same function, so the two entry points cannot diverge (AC3). [onRemoveConfirmed]
 * is single-flight (plan L6, mirrors #34 R6): it clears the prompt and marks [isRemoving]
 * synchronously, before any suspend point, so a double-tap on Confirm can never call
 * [removeBookUseCase] twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeLibraryRows: ObserveLibraryRowsUseCase,
    observeLibraryGrid: ObserveLibraryGridUseCase,
    private val removeBookUseCase: RemoveBookUseCase,
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

    private val removeEvents = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = removeEvents.receiveAsFlow()

    private val _removePrompt = MutableStateFlow(restoreRemovePrompt())
    val removePrompt: StateFlow<RemoveBookPrompt?> = _removePrompt

    /** Not saved: after process death, no remove can still be in flight (plan 4.2). */
    private var isRemoving = false

    fun onFilterSelected(filter: LibraryFilter) {
        savedStateHandle[KEY_FILTER] = filter
    }

    /** No-op if [bookId] is not in the currently-loaded grid, or a remove is already in flight
     * (plan L6/L7). */
    fun onRemoveRequested(bookId: Long) {
        if (isRemoving) return
        val content = uiState.value as? LibraryUiState.Content ?: return
        val book = content.grid.books.firstOrNull { it.book.id == bookId } ?: return
        setRemovePrompt(RemoveBookPrompt(bookId, book.book.title, book.openLoan?.borrowerName))
    }

    fun onRemoveDismissed() {
        setRemovePrompt(null)
    }

    /** Single-flight (plan L6): a no-op without a pending prompt or while already removing.
     * Clears the prompt and marks [isRemoving] synchronously, before the first suspend point.
     * [RemoveBookResult.Removed]/[RemoveBookResult.NotFound] both need no further UI action (plan
     * L7, no Snackbar on success - the Room flows already drop the card); any other exception
     * (rethrowing [CancellationException]) sends one [LibraryEvent.RemoveFailed]. */
    fun onRemoveConfirmed() {
        val prompt = _removePrompt.value
        if (prompt == null || isRemoving) return
        isRemoving = true
        setRemovePrompt(null)
        viewModelScope.launch {
            try {
                when (removeBookUseCase(prompt.bookId)) {
                    RemoveBookResult.Removed -> Unit
                    RemoveBookResult.NotFound -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                removeEvents.send(LibraryEvent.RemoveFailed)
            } finally {
                isRemoving = false
            }
        }
    }

    private fun setRemovePrompt(prompt: RemoveBookPrompt?) {
        _removePrompt.value = prompt
        savedStateHandle[KEY_REMOVE_BOOK_ID] = prompt?.bookId
        savedStateHandle[KEY_REMOVE_BOOK_TITLE] = prompt?.title
        savedStateHandle[KEY_REMOVE_BOOK_BORROWER] = prompt?.borrowerName
    }

    private fun restoreRemovePrompt(): RemoveBookPrompt? {
        val id = savedStateHandle.get<Long>(KEY_REMOVE_BOOK_ID) ?: return null
        val title = savedStateHandle.get<String>(KEY_REMOVE_BOOK_TITLE) ?: return null
        val borrower = savedStateHandle.get<String>(KEY_REMOVE_BOOK_BORROWER)
        return RemoveBookPrompt(id, title, borrower)
    }
}
