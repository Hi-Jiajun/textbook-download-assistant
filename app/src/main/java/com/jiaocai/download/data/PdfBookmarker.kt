package com.jiaocai.download.data

import android.util.Log
import com.jiaocai.download.model.Chapter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import java.io.File

/**
 * 为下载好的 PDF 写入章节书签。逻辑对应上游 tchMaterial-parser 的 bookmarks.py
 * （pypdf 实现），Android 侧用 PdfBox-Android 等价实现。
 *
 * 规则：
 * - page_index 来自平台 mapping，1 起，写入时减 1 得到 0 起的 PDF 页码。
 * - 页码越界或 page_index 缺失的书签跳过（不阻断其余书签）。
 * - 任一出错则静默返回 false，不影响已下载的 PDF 文件本身。
 */
object PdfBookmarker {
    private const val TAG = "PdfBookmarker"

    /** 写入书签，成功返回 true。chapters 为空或不带页码时直接返回 false。 */
    fun addBookmarks(file: File, chapters: List<Chapter>): Boolean {
        if (chapters.isEmpty()) return false
        var doc: PDDocument? = null
        return try {
            doc = PDDocument.load(file)
            val catalog = doc.documentCatalog
            val root = PDDocumentOutline()
            catalog.documentOutline = root

            fun addTo(parent: PDOutlineNode, items: List<Chapter>) {
                for (c in items) {
                    val item = PDOutlineItem()
                    item.title = c.title
                    val zeroBased = c.pageIndex?.minus(1)
                    if (zeroBased != null && zeroBased in 0 until doc!!.numberOfPages) {
                        val dest = PDPageFitWidthDestination()
                        dest.page = doc!!.getPage(zeroBased)
                        item.destination = dest
                    }
                    parent.addLast(item)
                    if (c.children.isNotEmpty()) addTo(item, c.children)
                }
            }

            addTo(root, chapters)
            doc!!.save(file)
            true
        } catch (e: Exception) {
            Log.w(TAG, "写入书签失败（不影响已下载文件）", e)
            false
        } finally {
            try { doc?.close() } catch (_: Exception) {}
        }
    }
}
