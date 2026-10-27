package com.minicore.bookshelf.core.coverimage

import javax.inject.Qualifier

/** [FileCoverImageStore]'s file IO/decoding dispatcher, injected so tests can substitute one. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * The shape every cover-image ref must match (plan #31 section 4.4/5, security): a draft ref
 * under `cover-drafts/`, or a stored ref under `covers/`, each a lowercase UUID + `.jpg`. No
 * other value resolves to a file - F43/section 5: `../`, absolute paths and `content://` all
 * fail [isValidDraftRef]/[isValidStoredRef], so there is no path traversal.
 */
internal object CoverRefs {
    const val DRAFTS_DIR = "cover-drafts"
    const val COVERS_DIR = "covers"

    private val UUID_JPG = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg"
    val DRAFT_REF_REGEX = Regex("^$DRAFTS_DIR/$UUID_JPG$")
    val STORED_REF_REGEX = Regex("^$COVERS_DIR/$UUID_JPG$")

    fun isValidDraftRef(ref: String): Boolean = DRAFT_REF_REGEX.matches(ref)

    fun isValidStoredRef(ref: String): Boolean = STORED_REF_REGEX.matches(ref)

    fun fileNameOf(ref: String): String = ref.substringAfter('/')
}
