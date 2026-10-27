package com.minicore.bookshelf.core.database.entity

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FF-M? / R7 (plan section 5, 10): `comment` and the `memory_*` fields are the user's own
 * personal reflections, so [com.minicore.bookshelf.core.model.Book]/[BookEntity] must never be
 * logged (the rule the plan states for #18 and extends here). Scans every `src/main` file for a
 * `Log.`/`println` call on the same line as a `Book`/`BookEntity` reference.
 */
class BookLoggingTest {

    @Test
    fun noSourceFile_logsBookOrBookEntity() {
        val mainRoot = File("src/main/java/com/minicore/bookshelf")
        assertTrue("expected ${mainRoot.path} to exist", mainRoot.exists())

        val loggingCallRegex = Regex("""(Log\.\w+|println)\s*\([^)]*\b(Book|BookEntity)\b""")

        val offendingLines = mainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> file.readLines().mapIndexed { index, line -> file to (index + 1) to line } }
            .filter { (_, line) -> loggingCallRegex.containsMatchIn(line) }
            .map { (location, line) -> "${location.first.path}:${location.second}: $line" }
            .toList()

        assertTrue(
            "Book/BookEntity must never be logged, found: $offendingLines",
            offendingLines.isEmpty(),
        )
    }
}
