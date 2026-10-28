package com.minicore.bookshelf.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FF-L1/FF-L2 (plan #35 section 5): the Library grid's remove entry points (long-press, "⋮")
 * reuse #34's dialog and use case verbatim - structurally, not just by convention - and the
 * Library files stay free of any `ui.editbook`/`ui.bookform` import (extends FF-E4/FF-R3).
 */
class LibraryRemoveSeamTest {

    private fun ktFiles(root: String): List<File> {
        val dir = File(root)
        assertTrue("expected $root to exist", dir.exists())
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    // FF-L1: RemoveBookDialog( is called only from EditBookRoute.kt and LibraryRoute.kt.
    @Test
    fun removeBookDialog_isCalledOnlyFromEditBookRouteAndLibraryRoute() {
        val offenders = ktFiles("src/main/java/com/minicore/bookshelf")
            .filter { it.readText().contains("RemoveBookDialog(") }
            .filterNot { it.name == "EditBookRoute.kt" || it.name == "LibraryRoute.kt" || it.name == "RemoveBookDialog.kt" }
        assertTrue("RemoveBookDialog( must only be called from EditBookRoute.kt/LibraryRoute.kt, found: ${offenders.map { it.path }}", offenders.isEmpty())
    }

    // FF-L1: RemoveBookUseCase is injected only into EditBookViewModel and LibraryViewModel.
    @Test
    fun removeBookUseCase_isInjectedOnlyIntoEditBookAndLibraryViewModels() {
        val offenders = ktFiles("src/main/java/com/minicore/bookshelf")
            .filter { it.readText().contains("RemoveBookUseCase") }
            .filterNot { it.name == "EditBookViewModel.kt" || it.name == "LibraryViewModel.kt" || it.name == "RemoveBookUseCase.kt" }
        assertTrue("RemoveBookUseCase must only be referenced from EditBookViewModel.kt/LibraryViewModel.kt, found: ${offenders.map { it.path }}", offenders.isEmpty())
    }

    // FF-L1: the dialog's own strings are referenced only from ui/removebook/.
    @Test
    fun removeBookDialogStrings_areReferencedOnlyFromRemovebookPackage() {
        val strings = listOf("remove_book_title", "remove_book_body", "remove_book_open_loan_warning")
        val offenders = ktFiles("src/main/java/com/minicore/bookshelf/features")
            .filterNot { it.path.replace('\\', '/').contains("/ui/removebook/") }
            .filter { file -> strings.any { file.readText().contains("R.string.$it") } }
        assertTrue("found: ${offenders.map { it.path }}", offenders.isEmpty())
    }

    // FF-L2: LibraryRoute, LibraryScreen, LibraryGridSection and LibraryViewModel import neither
    // ui.editbook nor ui.bookform.
    @Test
    fun libraryRemoveFiles_neverImportEditbookOrBookform() {
        val files = listOf(
            "src/main/java/com/minicore/bookshelf/features/library/ui/LibraryRoute.kt",
            "src/main/java/com/minicore/bookshelf/features/library/ui/LibraryScreen.kt",
            "src/main/java/com/minicore/bookshelf/features/library/ui/LibraryGridSection.kt",
            "src/main/java/com/minicore/bookshelf/features/library/ui/LibraryViewModel.kt",
        ).map(::File)
        val offenders = files.filter { file ->
            val text = file.readText()
            text.contains("import com.minicore.bookshelf.features.library.ui.editbook") ||
                text.contains("import com.minicore.bookshelf.features.library.ui.bookform")
        }
        assertTrue("found: ${offenders.map { it.path }}", offenders.isEmpty())
    }
}
