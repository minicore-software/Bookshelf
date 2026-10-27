package com.minicore.bookshelf.core.database

import com.minicore.bookshelf.core.database.migration.BookshelfMigrations
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FF-M3 (plan section 6): the committed schema JSON exists for every version `1..N`,
 * [BookshelfMigrations.ALL] covers every step `k -> k+1`, and no file under `src/main` ever
 * calls `fallbackToDestructiveMigration*` (ADR-002 constraint: it silently wipes user data).
 */
class BookshelfMigrationsCoverageTest {

    /**
     * Parsed from the `@Database(version = N, ...)` annotation's source text, not hardcoded:
     * a future version bump without updating this file would otherwise pass even with missing
     * migrations/schema files.
     */
    private val currentVersion: Int by lazy {
        val source = File(
            "src/main/java/com/minicore/bookshelf/core/database/BookshelfDatabase.kt"
        ).readText()
        val match = Regex("""version\s*=\s*(\d+)""").find(source)
            ?: error("expected to find version = <N> in BookshelfDatabase.kt")
        match.groupValues[1].toInt()
    }

    @Test
    fun schemaJsonExists_forEveryVersionUpToCurrent() {
        val schemaDir = File("schemas/com.minicore.bookshelf.core.database.BookshelfDatabase")
        assertTrue("expected ${schemaDir.path} to exist", schemaDir.exists())

        for (version in 1..currentVersion) {
            val schemaFile = File(schemaDir, "$version.json")
            assertTrue("expected ${schemaFile.path} to exist", schemaFile.exists())
        }
    }

    @Test
    fun migrationsList_coversEveryStepUpToCurrentVersion() {
        assertEquals(currentVersion - 1, BookshelfMigrations.ALL.size)

        val expectedSteps = (1 until currentVersion).map { it to it + 1 }
        val actualSteps = BookshelfMigrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals(expectedSteps, actualSteps)
    }

    @Test
    fun noSourceFile_everFallsBackToDestructiveMigration() {
        val mainRoot = File("src/main/java/com/minicore/bookshelf")
        assertTrue("expected ${mainRoot.path} to exist", mainRoot.exists())

        val offendingFiles = mainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readText().contains("fallbackToDestructiveMigration") }
            .toList()

        assertTrue(
            "no file may call fallbackToDestructiveMigration*, found: ${offendingFiles.map { it.path }}",
            offendingFiles.isEmpty(),
        )
    }
}
