package com.minicore.bookshelf.core.coverimage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F43, plan #31 section 5: a crafted ref must never resolve outside `cover-drafts/`/`covers/`. */
class CoverRefsTest {

    private val validDraft = "cover-drafts/11111111-1111-1111-1111-111111111111.jpg"
    private val validStored = "covers/22222222-2222-2222-2222-222222222222.jpg"

    @Test
    fun validRefs_areAccepted() {
        assertTrue(CoverRefs.isValidDraftRef(validDraft))
        assertTrue(CoverRefs.isValidStoredRef(validStored))
    }

    @Test
    fun pathTraversal_isRejected() {
        val offenders = listOf(
            "cover-drafts/../../etc/passwd",
            "covers/../../etc/passwd",
            "cover-drafts/..%2F..%2Fetc%2Fpasswd.jpg",
            "/etc/passwd",
            "cover-drafts/11111111-1111-1111-1111-111111111111.jpg/../../x",
        )
        offenders.forEach {
            assertFalse("expected rejection: $it", CoverRefs.isValidDraftRef(it))
            assertFalse("expected rejection: $it", CoverRefs.isValidStoredRef(it))
        }
    }

    @Test
    fun foreignUrisAndWrongDirectory_areRejected() {
        assertFalse(CoverRefs.isValidDraftRef("content://media/external/images/1"))
        assertFalse(CoverRefs.isValidDraftRef(validStored)) // a stored ref is not a draft ref
        assertFalse(CoverRefs.isValidStoredRef(validDraft)) // a draft ref is not a stored ref
        assertFalse(CoverRefs.isValidStoredRef("covers/not-a-uuid.jpg"))
        assertFalse(CoverRefs.isValidStoredRef("covers/22222222-2222-2222-2222-222222222222.png"))
    }
}
