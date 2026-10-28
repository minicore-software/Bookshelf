package com.minicore.bookshelf.features.library.ui.editbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minicore.bookshelf.core.coverimage.CoverImageStore
import com.minicore.bookshelf.core.coverimage.CoverImportException
import com.minicore.bookshelf.core.model.Book
import com.minicore.bookshelf.core.model.Cover
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.BookDraft
import com.minicore.bookshelf.features.library.domain.CoverDraft
import com.minicore.bookshelf.features.library.domain.ObserveBookDetailUseCase
import com.minicore.bookshelf.features.library.domain.UpdateBookResult
import com.minicore.bookshelf.features.library.domain.UpdateBookUseCase
import com.minicore.bookshelf.features.library.domain.model.BookDetail
import com.minicore.bookshelf.features.library.ui.bookform.BookFormRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Year
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The `book/{bookId}/edit` route argument name (plan #33 section 4.1) - duplicated here, not
 * imported from `navigation/BookshelfDestinations`, for the same reason `BookDetailViewModel`
 * duplicates its own (FF-E4): `ui/editbook/` stays referenced only from `navigation/` in the
 * other direction and never imports it back. */
private const val KEY_BOOK_ID = "bookId"

private const val KEY_TITLE = "title"
private const val KEY_AUTHOR = "author"
private const val KEY_YEAR_TEXT = "year_text"
private const val KEY_RATING = "rating"
private const val KEY_COMMENT = "comment"
private const val KEY_MEMORY_WHY = "memory_why"
private const val KEY_MEMORY_WHERE = "memory_where"
private const val KEY_MEMORY_WHAT = "memory_what"
private const val KEY_COVER_PRESET = "cover_preset"
private const val KEY_COVER_DRAFT_REF = "cover_draft_ref"
private const val KEY_COVER_STORED_REF = "cover_stored_ref"
private const val KEY_LAST_PRESET = "last_preset"
private const val KEY_SEEDED = "seeded"

/**
 * Backs [EditBookUiState] with `SavedStateHandle` field by field (plan #33 section 4.4). Seeds
 * once from the first [ObserveBookDetailUseCase] emission (decision E10); every later emission
 * only updates [EditBookUiState.isLentOut] and re-derives [EditBookUiState.initialDraft] from the
 * fresh row - it never overwrites what the user is typing (F3). Saving is single-flight (mirrors
 * `AddBookViewModel` A8): [onSave] sets `isSaving = true` synchronously, before the first suspend
 * point, so two calls in the same frame can never both pass the guard (F17).
 */
@HiltViewModel
class EditBookViewModel @Inject constructor(
    private val observeBookDetail: ObserveBookDetailUseCase,
    private val updateBookUseCase: UpdateBookUseCase,
    private val coverImages: CoverImageStore,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val bookId: Long = checkNotNull(savedStateHandle[KEY_BOOK_ID]) {
        "EditBookViewModel requires a $KEY_BOOK_ID saved-state argument"
    }

    private val events = Channel<EditBookResult>(Channel.BUFFERED)
    val onDone = events.receiveAsFlow()

    private var seeded = savedStateHandle.get<Boolean>(KEY_SEEDED) ?: false
    private var hasRequestedDismiss = false
    private var hasEmittedResult = false

    private val _uiState = MutableStateFlow(restoreInitialState())
    val uiState: StateFlow<EditBookUiState> = _uiState

    /** `null` while the one-and-only upstream subscription has not emitted yet, distinct from a
     * real "the row is gone" emission ([Loaded] wrapping a `null` [Loaded.detail]) - `stateIn`'s
     * synthetic initial value must never be mistaken for [ObserveBookDetailUseCase] itself saying
     * the book does not exist. */
    private data class Loaded(val detail: BookDetail?)

    private val bookDetail: StateFlow<Loaded?> =
        observeBookDetail(bookId).map { Loaded(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        val restoredCover = _uiState.value.cover
        if (restoredCover is CoverDraft.Image) {
            viewModelScope.launch {
                // F19: a draft file purged from the cache (process death survived longer than the
                // cache entry) falls back to the book's own original cover, never a preset. Waits
                // for the first *real* upstream emission before falling back - reading
                // `bookDetail.value` synchronously here would race Room's first emission: if the
                // file-exists check resolves first, `bookDetail.value` is still the synthetic
                // `null` initial value, so the fallback would silently become a wrong preset, and
                // a later Save would delete the book's real original cover file.
                val original = bookDetail.filterNotNull().first().detail?.book?.cover?.toCoverDraft() ?: return@launch
                if (!coverImages.exists(restoredCover.draftRef) && _uiState.value.cover == restoredCover) {
                    update { it.copy(cover = original) }
                }
            }
        }
        bookDetail.onEach { loaded -> if (loaded != null) onBookDetail(loaded.detail) }.launchIn(viewModelScope)
    }

    private fun onBookDetail(detail: BookDetail?) {
        if (detail == null) {
            emitResult(EditBookResult.NotFound)
            return
        }
        val draft = detail.book.toBookDraft()
        if (!seeded) {
            seeded = true
            savedStateHandle[KEY_SEEDED] = true
            update {
                it.copy(
                    isLoaded = true,
                    title = draft.title,
                    author = draft.author,
                    yearText = draft.publishedYear?.toString() ?: "",
                    rating = draft.rating,
                    comment = draft.comment,
                    memoryWhy = draft.memoryWhy,
                    memoryWhere = draft.memoryWhere,
                    memoryWhat = draft.memoryWhat,
                    cover = draft.cover,
                    isLentOut = detail.loan != null,
                    initialDraft = draft,
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(isLentOut = detail.loan != null, initialDraft = draft)
        }
    }

    private fun restoreInitialState(): EditBookUiState {
        val presetName = savedStateHandle.get<String>(KEY_COVER_PRESET)
        val draftRef = savedStateHandle.get<String>(KEY_COVER_DRAFT_REF)
        val storedRef = savedStateHandle.get<String>(KEY_COVER_STORED_REF)
        val preset = presetName?.let { runCatching { CoverPreset.valueOf(it) }.getOrNull() } ?: CoverPreset.Preset1
        val cover = when {
            draftRef != null -> CoverDraft.Image(draftRef)
            storedRef != null -> CoverDraft.Stored(storedRef)
            else -> CoverDraft.Preset(preset)
        }
        return EditBookUiState(
            isLoaded = seeded,
            title = savedStateHandle.get<String>(KEY_TITLE) ?: "",
            author = savedStateHandle.get<String>(KEY_AUTHOR) ?: "",
            yearText = savedStateHandle.get<String>(KEY_YEAR_TEXT) ?: "",
            rating = savedStateHandle.get<Int>(KEY_RATING),
            comment = savedStateHandle.get<String>(KEY_COMMENT) ?: "",
            memoryWhy = savedStateHandle.get<String>(KEY_MEMORY_WHY) ?: "",
            memoryWhere = savedStateHandle.get<String>(KEY_MEMORY_WHERE) ?: "",
            memoryWhat = savedStateHandle.get<String>(KEY_MEMORY_WHAT) ?: "",
            cover = cover,
        )
    }

    private fun lastPreset(): CoverPreset =
        savedStateHandle.get<String>(KEY_LAST_PRESET)?.let { runCatching { CoverPreset.valueOf(it) }.getOrNull() }
            ?: CoverPreset.Preset1

    private inline fun update(transform: (EditBookUiState) -> EditBookUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next
        savedStateHandle[KEY_TITLE] = next.title
        savedStateHandle[KEY_AUTHOR] = next.author
        savedStateHandle[KEY_YEAR_TEXT] = next.yearText
        savedStateHandle[KEY_RATING] = next.rating
        savedStateHandle[KEY_COMMENT] = next.comment
        savedStateHandle[KEY_MEMORY_WHY] = next.memoryWhy
        savedStateHandle[KEY_MEMORY_WHERE] = next.memoryWhere
        savedStateHandle[KEY_MEMORY_WHAT] = next.memoryWhat
        when (val cover = next.cover) {
            is CoverDraft.Preset -> {
                savedStateHandle[KEY_COVER_PRESET] = cover.preset.name
                savedStateHandle[KEY_COVER_DRAFT_REF] = null
                savedStateHandle[KEY_COVER_STORED_REF] = null
                savedStateHandle[KEY_LAST_PRESET] = cover.preset.name
            }
            is CoverDraft.Image -> {
                savedStateHandle[KEY_COVER_DRAFT_REF] = cover.draftRef
                savedStateHandle[KEY_COVER_STORED_REF] = null
            }
            is CoverDraft.Stored -> {
                savedStateHandle[KEY_COVER_STORED_REF] = cover.storedRef
                savedStateHandle[KEY_COVER_DRAFT_REF] = null
            }
        }
    }

    fun onTitleChange(value: String) = update { it.copy(title = value, error = null) }

    fun onAuthorChange(value: String) = update { it.copy(author = value, error = null) }

    fun onCommentChange(value: String) = update { it.copy(comment = value, error = null) }

    fun onMemoryWhyChange(value: String) = update { it.copy(memoryWhy = value, error = null) }

    fun onMemoryWhereChange(value: String) = update { it.copy(memoryWhere = value, error = null) }

    fun onMemoryWhatChange(value: String) = update { it.copy(memoryWhat = value, error = null) }

    fun onYearTextChange(value: String) {
        val sanitized = BookFormRules.sanitizeYearText(value) ?: return
        update { it.copy(yearText = sanitized, error = null) }
    }

    fun onYearStep(delta: Int) {
        update { state -> state.copy(yearText = BookFormRules.stepYear(state.yearText, delta, currentYear()), error = null) }
    }

    private fun currentYear(): Int = Year.now(clock).value

    fun onRatingTap(star: Int) {
        update { state -> state.copy(rating = BookFormRules.toggleRating(state.rating, star), error = null) }
    }

    fun onPresetSelected(preset: CoverPreset) {
        val previous = _uiState.value.cover
        update { it.copy(cover = CoverDraft.Preset(preset), error = null) }
        if (previous is CoverDraft.Image) {
            viewModelScope.launch { coverImages.discardDraft(previous.draftRef) }
        }
        // E5: CoverDraft.Stored is never discarded/deleted by an intent.
    }

    /**
     * `null` means the picker was cancelled: nothing changes. On failure, the cover is left
     * exactly as it is *at completion time*. On success, the new image is only applied if the
     * cover is still what it was when the import started; otherwise the freshly imported (now
     * orphaned) draft is discarded instead of being applied or leaked (mirrors
     * `AddBookViewModel.onImagePicked`).
     */
    fun onImagePicked(sourceUri: String?) {
        if (sourceUri == null) return
        val coverBeforeImport = _uiState.value.cover
        update { it.copy(isImportingImage = true, error = null) }
        viewModelScope.launch {
            try {
                val ref = coverImages.importDraft(sourceUri)
                if (_uiState.value.cover == coverBeforeImport) {
                    update { it.copy(cover = CoverDraft.Image(ref), isImportingImage = false) }
                    if (coverBeforeImport is CoverDraft.Image) coverImages.discardDraft(coverBeforeImport.draftRef)
                    // E5: a replaced CoverDraft.Stored is never discarded - no file op.
                } else {
                    update { it.copy(isImportingImage = false) }
                    coverImages.discardDraft(ref)
                }
            } catch (e: CoverImportException) {
                update { it.copy(isImportingImage = false, error = EditBookError.ImageImportFailed) }
            }
        }
    }

    fun onRemoveImage() {
        val previous = _uiState.value.cover
        if (previous !is CoverDraft.Image && previous !is CoverDraft.Stored) return
        update { it.copy(cover = CoverDraft.Preset(lastPreset()), error = null) }
        if (previous is CoverDraft.Image) {
            viewModelScope.launch { coverImages.discardDraft(previous.draftRef) }
        }
        // E5: Stored -> no file op.
    }

    /**
     * Single-flight (mirrors A8/FF-A2): a no-op while saving, importing, when the title is
     * blank, when the form has not loaded yet, or once [onDismissRequest] has already been
     * requested in the same frame.
     */
    fun onSave() {
        val state = _uiState.value
        if (!state.canSave || hasRequestedDismiss) return
        update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                when (updateBookUseCase(bookId, state.toDraft())) {
                    UpdateBookResult.Updated -> emitResult(EditBookResult.Saved)
                    UpdateBookResult.NotFound -> emitResult(EditBookResult.NotFound)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update { it.copy(isSaving = false, error = EditBookError.SaveFailed) }
            }
        }
    }

    /**
     * Every exit path (Cancel, X, back, scrim) routes here. Ignored while saving, and
     * single-flight like [onSave]. Discards the current [CoverDraft.Image] draft if any; never
     * touches a [CoverDraft.Stored] file.
     */
    fun onDismissRequest() {
        val state = _uiState.value
        if (state.isSaving || hasRequestedDismiss) return
        hasRequestedDismiss = true
        val cover = state.cover
        if (cover is CoverDraft.Image) {
            viewModelScope.launch { coverImages.discardDraft(cover.draftRef) }
        }
        emitResult(EditBookResult.Cancelled)
    }

    private fun emitResult(result: EditBookResult) {
        if (hasEmittedResult) return
        hasEmittedResult = true
        if (result == EditBookResult.NotFound) {
            val cover = _uiState.value.cover
            if (cover is CoverDraft.Image) {
                viewModelScope.launch { coverImages.discardDraft(cover.draftRef) }
            }
        }
        viewModelScope.launch { events.send(result) }
    }
}

private fun Book.toBookDraft(): BookDraft = BookDraft(
    title = title,
    author = author,
    publishedYear = publishedYear,
    rating = rating,
    comment = comment ?: "",
    memoryWhy = memoryWhy ?: "",
    memoryWhere = memoryWhere ?: "",
    memoryWhat = memoryWhat ?: "",
    cover = cover.toCoverDraft(),
)

private fun Cover.toCoverDraft(): CoverDraft = when (this) {
    is Cover.Image -> CoverDraft.Stored(uri)
    is Cover.Preset -> CoverDraft.Preset(preset)
}
