package com.minicore.bookshelf.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FF5 (plan section 7, A19): dependency direction is `features -> core`, never the reverse
 * (plan decision S1). Walks every source file under core (recursively) and fails on any
 * `import com.minicore.bookshelf.features`.
 */
class LayeringTest {

    @Test
    fun coreSources_neverImportFeatures() {
        val coreRoot = File("src/main/java/com/minicore/bookshelf/core")
        assertTrue("expected ${coreRoot.path} to exist", coreRoot.exists())

        val offendingFiles = coreRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readText().contains("import com.minicore.bookshelf.features") }
            .toList()

        assertTrue(
            "core must never import features, found: ${offendingFiles.map { it.path }}",
            offendingFiles.isEmpty(),
        )
    }
}
