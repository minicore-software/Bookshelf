package com.minicore.bookshelf.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FF-V1/FF-V2 (plan #32 section 5): the Detail/Edit/Lend seams stay swappable -
 * [BookshelfDestinations.BookDetail] and `navigateToEditBook` are only ever referenced from
 * `navigation/`, `LibraryScreen.kt`/`LibraryRoute.kt` never import `ui.bookdetail`, and nothing
 * under `ui/bookdetail/` reaches into `addBook`, `updateBook`, `LoanDao` or `BookDao` directly -
 * the ViewModel depends only on [com.minicore.bookshelf.features.library.domain.ObserveBookDetailUseCase].
 */
class BookDetailSeamTest {

    private fun ktFiles(root: String): List<File> {
        val dir = File(root)
        assertTrue("expected $root to exist", dir.exists())
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun bookDetailDestination_isReferencedOnlyFromNavigation() {
        val offenders = ktFiles("src/main/java/com/minicore/bookshelf/features")
            .filter { it.readText().contains("BookshelfDestinations.BookDetail") }
        assertTrue("BookDetail must only be referenced from navigation/, found: ${offenders.map { it.path }}", offenders.isEmpty())
    }

    @Test
    fun navigateToEditBook_isReferencedOnlyFromNavigation() {
        val offenders = ktFiles("src/main/java/com/minicore/bookshelf/features")
            .filter { it.readText().contains("navigateToEditBook") }
        assertTrue("navigateToEditBook must only be referenced from navigation/, found: ${offenders.map { it.path }}", offenders.isEmpty())
    }

    @Test
    fun libraryScreenAndRoute_doNotImportBookdetail() {
        val offenders = listOf(
            File("src/main/java/com/minicore/bookshelf/features/library/ui/LibraryScreen.kt"),
            File("src/main/java/com/minicore/bookshelf/features/library/ui/LibraryRoute.kt"),
        ).filter { it.readText().contains("com.minicore.bookshelf.features.library.ui.bookdetail") }
        assertTrue("found: ${offenders.map { it.path }}", offenders.isEmpty())
    }

    @Test
    fun bookdetailUi_neverReferencesWriteApisOrOtherDaosDirectly() {
        val offenders = ktFiles("src/main/java/com/minicore/bookshelf/features/library/ui/bookdetail")
            .filter { file ->
                val text = file.readText()
                listOf("addBook(", "updateBook(", "LoanDao", "BookDao").any { text.contains(it) }
            }
        assertTrue("found: ${offenders.map { it.path }}", offenders.isEmpty())
    }
}
