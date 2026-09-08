package com.jiaocai.download.data

import android.content.Context
import com.jiaocai.download.model.SavedItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 「我的课本库」本地索引，存于应用私有目录 library.json。 */
class LibraryStore(private val context: Context) {

    private val file = File(context.filesDir, "library.json")

    fun load(): List<SavedItem> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SavedItem(
                            title = o.optString("title"),
                            path = o.optString("path"),
                            format = o.optString("format", "pdf"),
                            edition = o.optString("edition").ifBlank { null },
                            addedAt = o.optLong("addedAt"),
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun append(item: SavedItem): List<SavedItem> {
        // 同一本书重复下载时用相同路径，直接覆盖旧记录，避免列表出现重复行。
        val list = load().filterNot { it.path == item.path }.toMutableList()
        list.add(item)
        save(list)
        return list
    }

    fun remove(path: String): List<SavedItem> {
        val list = load().filterNot { it.path == path }
        save(list)
        return list
    }

    private fun save(list: List<SavedItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("title", it.title)
                    .put("path", it.path)
                    .put("format", it.format)
                    .put("edition", it.edition ?: "")
                    .put("addedAt", it.addedAt)
            )
        }
        file.writeText(arr.toString())
    }
}
