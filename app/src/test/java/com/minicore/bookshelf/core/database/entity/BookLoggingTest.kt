package com.minicore.bookshelf.core.database.entity

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FF-M? / R7 (plan section 5, 10): `comment` and the `memory_*` fields are the user's own
 * personal reflections, so they (and anything derived from a [com.minicore.bookshelf.core.model.Book]
 * or [BookEntity]) must never be logged (the rule the plan states for #18 and extends here).
 *
 * A regex keyed on the literal type name (`Book`/`BookEntity`) is easy to defeat by accident -
 * `Log.d(TAG, "loaded $book")`, `println(entity)`, `it.toString()` or a `Timber` call would all
 * leak the memory fields without ever mentioning the type name on that line. Forbidding
 * `android.util.Log`/`println` in `src/main` entirely is cheaper and gives real assurance: today
 * there is no logging abstraction in the project and no call site at all, so this cannot produce
 * a false positive, and it blocks the actual risk - any future logging statement anywhere in
 * `src/main`, not just ones that happen to type `Book` on the same line - until a vetted logger
 * is introduced and allow-listed here.
 */
class BookLoggingTest {

    @Test
    fun noSourceFile_usesLogOrPrintln() {
        val mainRoot = File("src/main/java/com/minicore/bookshelf")
        assertTrue("expected ${mainRoot.path} to exist", mainRoot.exists())

        val loggingCallRegex = Regex("""\b(android\.util\.)?Log\.(v|d|i|w|e|wtf)\s*\(|\bprintln\s*\(""")

        val offendingLines = mainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> file.readLines().mapIndexed { index, line -> file to (index + 1) to line } }
            .filter { (_, line) -> loggingCallRegex.containsMatchIn(line) }
            .map { (location, line) -> "${location.first.path}:${location.second}: $line" }
            .toList()

        assertTrue(
            "android.util.Log/println must never be used in src/main - a Book/BookEntity " +
                "memory field could reach a log without ever naming the type on that line - " +
                "found: $offendingLines",
            offendingLines.isEmpty(),
        )
    }
}
