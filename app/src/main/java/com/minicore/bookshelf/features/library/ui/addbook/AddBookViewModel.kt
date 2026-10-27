package com.minicore.bookshelf.features.library.ui.addbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minicore.bookshelf.core.coverimage.CoverImageStore
import com.minicore.bookshelf.core.coverimage.CoverImportException
import com.minicore.bookshelf.core.model.CoverPreset
import com.minicore.bookshelf.features.library.domain.AddBookUseCase
import com.minicore.bookshelf.features.library.domain.CoverDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Year
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val KEY_TITLE = "title"
private const val KEY_AUTHOR = "author"
private const val KEY_YEAR_TEXT = "year_text"
private const val KEY_RATING = "rating"
private const val KEY_COMMENT = "comment"
private const val KEY_COVER_PRESET = "cover_preset"
private const val KEY_COVER_DRAFT_REF = "cover_draft_ref"
private const val KEY_LAST_PRESET = "last_preset"

/**
 * Backs [AddBookUiState] with `SavedStateHandle` field by field (plan #31 section 4.2) so
 * rotation and process death keep the whole draft (F23/F24). Saving is single-flight (A8,
 * FF-A2): [onSave] sets `isSaving = true` synchronously, before the first suspend point, so two
 * calls in the same frame can never both pass the guard.
 */
@HiltViewModel
class AddBookViewModel @Inject constructor(
    private val addBookUseCase: AddBookUseCase,
    private val coverImages: CoverImageStore,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val events = Channel<AddBookResult>(Channel.BUFFERED)
    val onDone = events.receiveAsFlow()

    private val _uiState = MutableStateFlow(restoreInitialState())
    val uiState: StateFlow<AddBookUiState> = _uiState

    init {
        val restoredCover = _uiState.value.cover
        if (restoredCover is CoverDraft.Image) {
            viewModelScope.launch {
                // F24: a draft file purged from the cache (process death survived longer than the
                // cache entry) falls back to the last preset, silently.
                if (!coverImages.exists(restoredCover.draftRef)) {
                    update { it.copy(cover = CoverDraft.Preset(lastPreset())) }
                }
            }
        }
    }

    private fun restoreInitialState(): AddBookUiState {
        val presetName = savedStateHandle.get<String>(KEY_COVER_PRESET)
        val draftRef = savedStateHandle.get<String>(KEY_COVER_DRAFT_REF)
        val preset = presetName?.let { runCatching { CoverPreset.valueOf(it) }.getOrNull() } ?: CoverPreset.Preset1
        // F24: a missing/purged draft file falls back to the last preset, not the image.
        val cover = if (draftRef != null) CoverDraft.Image(draftRef) else CoverDraft.Preset(preset)
        return AddBookUiState(
            title = savedStateHandle.get<String>(KEY_TITLE) ?: "",
            author = savedStateHandle.get<String>(KEY_AUTHOR) ?: "",
            yearText = savedStateHandle.get<String>(KEY_YEAR_TEXT) ?: "",
            rating = savedStateHandle.get<Int>(KEY_RATING),
            comment = savedStateHandle.get<String>(KEY_COMMENT) ?: "",
            cover = cover,
        )
    }

    private fun lastPreset(): CoverPreset =
        savedStateHandle.get<String>(KEY_LAST_PRESET)?.let { runCatching { CoverPreset.valueOf(it) }.getOrNull() }
            ?: CoverPreset.Preset1

    private inline fun update(transform: (AddBookUiState) -> AddBookUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next
        savedStateHandle[KEY_TITLE] = next.title
        savedStateHandle[KEY_AUTHOR] = next.author
        savedStateHandle[KEY_YEAR_TEXT] = next.yearText
        savedStateHandle[KEY_RATING] = next.rating
        savedStateHandle[KEY_COMMENT] = next.comment
        when (val cover = next.cover) {
            is CoverDraft.Preset -> {
                savedStateHandle[KEY_COVER_PRESET] = cover.preset.name
                savedStateHandle[KEY_COVER_DRAFT_REF] = null
                savedStateHandle[KEY_LAST_PRESET] = cover.preset.name
            }
            is CoverDraft.Image -> {
                savedStateHandle[KEY_COVER_DRAFT_REF] = cover.draftRef
            }
        }
    }

    fun onTitleChange(value: String) = update { it.copy(title = value, error = null) }

    fun onAuthorChange(value: String) = update { it.copy(author = value, error = null) }

    fun onCommentChange(value: String) = update { it.copy(comment = value, error = null) }

    fun onYearTextChange(value: String) {
        if (value.isNotEmpty() && (value.length > 4 || !value.all(Char::isDigit))) return
        update { it.copy(yearText = value, error = null) }
    }

    fun onYearStep(delta: Int) {
        require(delta == 1 || delta == -1) { "delta must be +1 or -1, was $delta" }
        update { state ->
            val current = state.yearText.toIntOrNull()
            val next = if (current == null || current == 0) currentYear() else (current + delta).coerceIn(1, 9999)
            state.copy(yearText = next.toString(), error = null)
        }
    }

    private fun currentYear(): Int = Year.now(clock).value

    fun onRatingTap(star: Int) {
        require(star in 1..5) { "star must be in 1..5, was $star" }
        update { state -> state.copy(rating = if (state.rating == star) null else star, error = null) }
    }

    fun onPresetSelected(preset: CoverPreset) {
        val previous = _uiState.value.cover
        update { it.copy(cover = CoverDraft.Preset(preset), error = null) }
        if (previous is CoverDraft.Image) {
            viewModelScope.launch { coverImages.discardDraft(previous.draftRef) }
        }
    }

    /**
     * `null` means the picker was cancelled: nothing changes (plan #31 section 4.2).
     *
     * On failure, the cover is left exactly as it is *at completion time* - never force-reset to
     * a `previous` snapshot taken when the import started, which could silently clobber a preset
     * the user picked while the import was still in flight. On success, the new image is only
     * applied if the cover is still what it was when the import started; otherwise the user has
     * since made a different choice, so the freshly imported (now orphaned) draft is discarded
     * instead of being applied or leaked.
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
                } else {
                    update { it.copy(isImportingImage = false) }
                    coverImages.discardDraft(ref)
                }
            } catch (e: CoverImportException) {
                update { it.copy(isImportingImage = false, error = AddBookError.ImageImportFailed) }
            }
        }
    }

    fun onRemoveImage() {
        val previous = _uiState.value.cover
        if (previous !is CoverDraft.Image) return
        update { it.copy(cover = CoverDraft.Preset(lastPreset()), error = null) }
        viewModelScope.launch { coverImages.discardDraft(previous.draftRef) }
    }

    /**
     * A8: single-flight. A no-op while saving, importing, when the title is blank, or once
     * [onDismissRequest] has already been requested in the same frame - otherwise a dismiss+Save
     * race could still insert a book whose draft cover [onDismissRequest] just discarded.
     */
    fun onSave() {
        val state = _uiState.value
        if (!state.canSave || hasRequestedDismiss) return
        update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val id = addBookUseCase(state.toDraft())
                events.send(AddBookResult.Saved(id))
            } catch (e: Exception) {
                update { it.copy(isSaving = false, error = AddBookError.SaveFailed) }
            }
        }
    }

    private var hasRequestedDismiss = false

    /**
     * A9: every exit path (Cancel, X, back, scrim) routes here. Ignored while saving, and
     * single-flight like [onSave] (mirrors `isSaving`'s guard): two dismiss taps in the same
     * frame - before the first's `popBackStack` takes effect - must not both send
     * [AddBookResult.Cancelled] and risk popping the back stack twice.
     */
    fun onDismissRequest() {
        val state = _uiState.value
        if (state.isSaving || hasRequestedDismiss) return
        hasRequestedDismiss = true
        val cover = state.cover
        if (cover is CoverDraft.Image) {
            viewModelScope.launch { coverImages.discardDraft(cover.draftRef) }
        }
        viewModelScope.launch { events.send(AddBookResult.Cancelled) }
    }
}
