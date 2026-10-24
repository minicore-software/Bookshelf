package com.minicore.bookshelf.core.designsystem

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F10: AAPT2 rejects non-font files under `res/font/` (plan section 3), and the OFL requires the
 * license to travel with the redistributed font, outside `res/font/` so the build doesn't break.
 */
class FrauncesLicensingTest {

    @Test
    fun resFontDirectory_containsOnlyFontFiles() {
        val fontDir = File("src/main/res/font")
        assertTrue("expected ${fontDir.path} to exist", fontDir.exists())

        val nonFontFiles = fontDir.listFiles().orEmpty().filterNot { file ->
            file.extension.lowercase() in setOf("ttf", "otf", "ttc", "xml")
        }

        assertTrue("unexpected non-font files in res/font/: $nonFontFiles", nonFontFiles.isEmpty())
    }

    @Test
    fun frauncesLicense_existsUnderAssetsLicenses() {
        val licenseFile = File("src/main/assets/licenses/Fraunces-OFL.txt")
        assertTrue("expected ${licenseFile.path} to exist", licenseFile.exists())
    }
}
