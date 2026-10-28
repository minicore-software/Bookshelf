package com.minicore.bookshelf.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FF-E3/FF-E4 (plan #33 section 5): [BookshelfDestinations.EditBook] stays referenced only from
 * `navigation/`, `ui/bookdetail/` and `LibraryScreen.kt`/`LibraryRoute.kt` never import
 * `ui.editbook`, `bookform/` imports neither `addbook/` nor `editbook/`, and nothing under
 * `ui/editbook/` or `UpdateBookUseCase.kt` references a loan write path (D13).
 */
class EditBookSeamTest {

    private fun ktFiles(root: String): List<File> {
        val dir = File(root)
        assertTrue("expected $root to exist", dir.exists())
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun editBookDestination_isReferencedOnlyFromNavigation() {
        val offenders = ktFiles("src/main/java/com/minicore/bookshelf/features")
            .filter { it.readText().contains("BookshelfDestinations.EditBook") }
        assertTrue("EditBook must only be referenced from navigation/, found: ${offenders.map { it.path }}", offenders.isEmpty())
    }

    @Test
    fun libraryAndBookDetail_doNotImportEditbookUi() {
        val offenders = listOf(
            File("src/main/java/com/minicore/bookshelf/features/library/ui/LibraryScreen.kt"),
            File("src/main/java/com/minicore/bookshelf/features/library/ui/LibraryRoute.kt"),
        ).filter { it.readText().contains("com.minicore.bookshelf.features.library.ui.editbook") } +
            ktFiles("src/main/java/com/minicore/bookshelf/features/library/ui/bookdetail")
                .filter { it.readText().contains("com.minicore.bookshelf.features.library.ui.editbook") }
        assertTrue("found: ${offenders.map { it.path }}", offenders.isEmpty())
    }

    @Test
    fun bookform_neverImportsEditbook() {
        val offenders = ktFiles("src/main/java/com/minicore/bookshelf/features/library/ui/bookform")
            .filter { it.readText().contains("import com.minicore.bookshelf.features.library.ui.editbook") }
        assertTrue("bookform/ must never import ui.editbook/, found: ${offenders.map { it.path }}", offenders.isEmpty())
    }

    // FF-R3 (plan #34 section 5): removebook/ stays reusable by #35 - it must never import
    // editbook/ or bookform/.
    @Test
    fun removebook_neverImportsEditbookOrBookform() {
        val offenders = ktFiles("src/main/java/com/minicore/bookshelf/features/library/ui/removebook")
            .filter { file ->
                val text = file.readText()
                text.contains("import com.minicore.bookshelf.features.library.ui.editbook") ||
                    text.contains("import com.minicore.bookshelf.features.library.ui.bookform")
            }
        assertTrue("removebook/ must never import editbook/ or bookform/, found: ${offenders.map { it.path }}", offenders.isEmpty())
    }

    // FF-E3/D13: no file under ui/editbook/, and not UpdateBookUseCase.kt, references a loan
    // write path or the generic book-insert write path.
    @Test
    fun editBookAndUpdateBookUseCase_neverReferenceLoanOrAddBookWritePaths() {
        val banned = listOf("LoanDao", "LoanEntity", "BookDao", "addBook(")
        val files = ktFiles("src/main/java/com/minicore/bookshelf/features/library/ui/editbook") +
            File("src/main/java/com/minicore/bookshelf/features/library/domain/UpdateBookUseCase.kt")
        val offenders = files.filter { file -> banned.any { file.readText().contains(it) } }
        assertTrue("found: ${offenders.map { it.path }}", offenders.isEmpty())
    }
}
