package com.jiaocai.download.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jiaocai.download.model.Chapter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfBookmarkerTest {

    @Test
    fun addBookmarks_writesNestedOutline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "bookmark-test.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.addPage(PDPage())
            document.save(file)
        }

        val chapters = listOf(
            Chapter("第一章", 1, listOf(Chapter("第一节", 2))),
            Chapter("第二章", 2),
        )

        assertTrue(PdfBookmarker.addBookmarks(file, chapters))

        PDDocument.load(file).use { document ->
            val outline = document.documentCatalog.documentOutline
            assertNotNull(outline)
            val first = outline.firstChild as PDOutlineItem
            assertEquals("第一章", first.title)
            val firstChild = first.firstChild as PDOutlineItem
            assertEquals("第一节", firstChild.title)
        }
    }

    @Test
    fun addBookmarks_emptyChapters_returnsFalse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "empty-bookmark-test.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.save(file)
        }

        assertFalse(PdfBookmarker.addBookmarks(file, emptyList()))
    }
}
