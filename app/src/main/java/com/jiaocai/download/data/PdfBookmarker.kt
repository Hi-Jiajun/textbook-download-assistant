package com.jiaocai.download.data

import android.util.Log
import com.jiaocai.download.model.Chapter
import com.tom_roush.pdfbox.io.MemoryUsageSetting
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
        // 先在临时文件里生成结果，成功后再原子替换原文件：
        // 1) 避免「读写同一个文件」把课本写坏；
        // 2) 大课本（100MB 级）用 MemoryUsageSetting 把工作数据放到临时文件，
        //    默认的「全量载入内存」在旧机型上会 OOM。
        val scratch = File(file.parentFile, file.name + ".bookmark.tmp")
        var doc: PDDocument? = null
        return try {
            doc = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
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
            doc!!.save(scratch)
            // 必须先关掉文档：源文件句柄没释放时，替换目标文件在 Windows 上会失败
            // （Android/Linux 允许覆盖，但这个差异会让单元测试和桌面调试很难受）。
            doc!!.close()
            doc = null
            if (!scratch.renameTo(file)) {
                // 目标已存在时部分平台 renameTo 会失败，退化为覆盖复制；此时句柄已释放。
                scratch.copyTo(file, overwrite = true)
                scratch.delete()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "写入书签失败（不影响已下载文件）", e)
            false
        } finally {
            try { doc?.close() } catch (_: Exception) {}
            // 成功时已经改名，这里只在失败或回退复制后清理残留。
            try { scratch.delete() } catch (_: Exception) {}
        }
    }
}
