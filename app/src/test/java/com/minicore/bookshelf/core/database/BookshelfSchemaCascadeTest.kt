package com.minicore.bookshelf.core.database

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FF-R1 (plan #34 section 5, decision R1): every foreign key in the latest exported Room schema
 * that references `books` must be `ON DELETE CASCADE`. Today that is only `loans.book_id`
 * (plan #34 R2), but this guards every future child table (for example a `reading_log_entries`
 * the progress epic might add) - it fails the moment one forgets the cascade, which is the only
 * guarantee this ticket has that "cascade to history" stays true once more tables exist (there is
 * no `reading_log_entries` table to test directly yet).
 *
 * Parses the committed schema JSON's text directly (same approach as
 * [BookshelfMigrationsCoverageTest] parsing `BookshelfDatabase.kt`'s source), rather than adding a
 * JSON-parsing dependency the project does not otherwise need, because the exported schema's
 * `"table": "<name>", "onDelete": "<RULE>"` pair always appears in that order inside a single
 * `foreignKeys` entry (Room's own exporter format).
 */
class BookshelfSchemaCascadeTest {

    private val currentVersion: Int by lazy {
        val source = File(
            "src/main/java/com/minicore/bookshelf/core/database/BookshelfDatabase.kt"
        ).readText()
        val match = Regex("""version\s*=\s*(\d+)""").find(source)
            ?: error("expected to find version = <N> in BookshelfDatabase.kt")
        match.groupValues[1].toInt()
    }

    private val foreignKeyOnBooks = Regex(""""table"\s*:\s*"books",\s*"onDelete"\s*:\s*"(\w+)"""")

    @Test
    fun everyForeignKeyReferencingBooks_isCascade() {
        val schemaFile = File(
            "schemas/com.minicore.bookshelf.core.database.BookshelfDatabase/$currentVersion.json"
        )
        assertTrue("expected ${schemaFile.path} to exist", schemaFile.exists())

        val matches = foreignKeyOnBooks.findAll(schemaFile.readText()).map { it.groupValues[1] }.toList()

        assertFalse("expected at least one foreign key referencing books (loans.book_id)", matches.isEmpty())
        matches.forEach { onDelete ->
            assertEquals("every foreign key referencing books must be ON DELETE CASCADE", "CASCADE", onDelete)
        }
    }
}
