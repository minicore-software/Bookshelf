package com.minicore.bookshelf.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FF-A3/FF-A4 (plan #31 section 6, F42): the manual-form seam stays swappable (decision D12) -
 * [BookshelfDestinations.AddBookManual] is only ever referenced from `navigation/`, and
 * `features/library/ui/bookform/` never imports `features/library/ui/addbook/` (so #33 can reuse
 * `BookForm` without pulling in Add Book's own screen/view-model).
 */
class AddBookSeamTest {

    private fun ktFiles(root: String): List<File> {
        val dir = File(root)
        assertTrue("expected $root to exist", dir.exists())
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun addBookManual_isReferencedOnlyFromNavigation() {
        val offenders = ktFiles("src/main/java/com/minicore/bookshelf/features")
            .filter { it.readText().contains("AddBookManual") }
        assertTrue("AddBookManual must only be referenced from navigation/, found: ${offenders.map { it.path }}", offenders.isEmpty())
    }

    @Test
    fun bookform_neverImportsAddbook() {
        val offenders = ktFiles("src/main/java/com/minicore/bookshelf/features/library/ui/bookform")
            .filter { it.readText().contains("import com.minicore.bookshelf.features.library.ui.addbook") }
        assertTrue("bookform/ must never import ui.addbook/, found: ${offenders.map { it.path }}", offenders.isEmpty())
    }

    @Test
    fun libraryScreenAndRoute_doNotImportAddbook() {
        val offenders = listOf(
            File("src/main/java/com/minicore/bookshelf/features/library/ui/LibraryScreen.kt"),
            File("src/main/java/com/minicore/bookshelf/features/library/ui/LibraryRoute.kt"),
        ).filter { it.readText().contains("com.minicore.bookshelf.features.library.ui.addbook") }
        assertTrue("found: ${offenders.map { it.path }}", offenders.isEmpty())
    }
}
