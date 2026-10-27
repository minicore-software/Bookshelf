package com.minicore.bookshelf.core.coverimage

/** A draft or stored cover-image reference could not be produced (plan #31 section 4.4). */
class CoverImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Owns the lifecycle of an uploaded cover image file (ADR-003): import a picked image into an
 * app-private **draft**, commit a draft into a permanent **stored** file on save, or discard /
 * delete either. Implemented by `FileCoverImageStore` (Android `Context`-backed, #31 task 6);
 * this interface lives in `core/coverimage` (not `features`) because
 * [com.minicore.bookshelf.core.designsystem.component.BookCover] must draw stored covers and
 * `core` must never import `features` (`LayeringTest`).
 *
 * Stored refs match `^covers/[0-9a-f-]{36}\.jpg$`; draft refs match the same pattern under
 * `cover-drafts/`. Resolution rejects anything else, so there is no path traversal.
 */
interface CoverImageStore {

    /**
     * Decodes [sourceUri] (any `content://` or `file://` image), downscales it to at most 600px
     * on its long edge, strips EXIF/GPS, and re-encodes it as JPEG into an app-private draft
     * file. Returns the draft ref. Throws [CoverImportException] on any decode/IO failure.
     */
    suspend fun importDraft(sourceUri: String): String

    /** Idempotent; never throws. */
    suspend fun discardDraft(draftRef: String)

    /** Moves the draft into permanent storage. Returns the stored ref. */
    suspend fun commit(draftRef: String): String

    /**
     * Reverses [commit]: moves [storedRef] back to [draftRef] (F19). Used when a save fails
     * *after* the cover was committed, so the retry - which still references the original draft
     * ref - can succeed instead of finding neither file. Idempotent; never throws.
     */
    suspend fun uncommit(storedRef: String, draftRef: String)

    /** Idempotent; never throws. Also #34's cleanup hook when a book is removed. */
    suspend fun delete(storedRef: String)

    /**
     * `true` when [ref] both matches the expected shape and resolves to a file that is actually
     * on disk. Used to detect a draft purged from the cache after process death (F24): the
     * restored state trusts a `SavedStateHandle` ref optimistically, then corrects itself to the
     * last preset if this returns `false`.
     */
    suspend fun exists(ref: String): Boolean
}
