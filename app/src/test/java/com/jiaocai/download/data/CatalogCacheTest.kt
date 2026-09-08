package com.jiaocai.download.data

import com.jiaocai.download.model.Textbook
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 教材目录本地缓存的读写回环测试（JSONL 格式、字段完整性、脏行容错）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `write then read cache keeps all fields`() {
        val file = tmp.newFile("catalog_cache.jsonl")
        val books = listOf(
            Textbook(
                id = "id-1",
                title = "义务教育教科书·语文一年级上册",
                stage = "小学",
                subject = "语文",
                version = "统编版",
                grade = "一年级",
                volume = "上册",
                thumb = "https://example.com/cover-1.jpg",
            ),
            Textbook(
                id = "id-2",
                title = "义务教育教科书·数学七年级下册",
                stage = "初中",
                subject = "数学",
                version = "人教版",
                grade = "七年级",
                volume = "下册",
                thumb = null,
            ),
        )

        CatalogApi.writeCache(file, books)

        assertEquals(books, CatalogApi.readCache(file))
    }

    @Test
    fun `read cache ignores blank lines`() {
        val file = tmp.newFile("catalog_cache.jsonl")
        file.writeText("\n  \n")

        assertEquals(emptyList<Textbook>(), CatalogApi.readCache(file))
    }
}
