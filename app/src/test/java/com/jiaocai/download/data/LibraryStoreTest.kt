package com.jiaocai.download.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jiaocai.download.model.SavedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryStoreTest {

    private lateinit var context: Context
    private lateinit var store: LibraryStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "library.json").delete()
        store = LibraryStore(context)
    }

    @Test
    fun appendLoadAndRemove_deletesLocalFile() {
        val file = File(context.filesDir, "test-book.pdf").apply { writeText("pdf") }
        val item = SavedItem("测试教材", file.absolutePath, "pdf", "人教版", 123L)

        store.append(item)

        assertEquals(listOf(item), store.load())
        assertTrue(store.remove(file.absolutePath))
        assertFalse(file.exists())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun appendSamePath_replacesOldRecord() {
        val file = File(context.filesDir, "same.pdf").apply { writeText("pdf") }
        store.append(SavedItem("旧标题", file.absolutePath, "pdf", null, 1L))
        store.append(SavedItem("新标题", file.absolutePath, "pdf", null, 2L))

        val items = store.load()

        assertEquals(1, items.size)
        assertEquals("新标题", items.single().title)
    }
}
