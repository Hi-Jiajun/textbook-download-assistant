package com.jiaocai.download.data

import android.util.JsonReader
import android.util.JsonToken
import com.jiaocai.download.model.Textbook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 拉取国家中小学智慧教育平台的电子教材目录，供应用内浏览与勾选。
 * 接口与解析逻辑参照上游 tchMaterial-parser 的 catalog.py 移植。
 *
 * 说明：目录清单每个文件约 10MB（每本书内嵌大量封面预览数据）。这里用
 * JsonReader 流式读取，只取 id/标题/分类标签，跳过预览等无关大字段，
 * 避免一次性把整份 JSON 加载进内存（旧机型更容易 OOM）。
 */
object CatalogApi {

    private const val DATA_VERSION =
        "https://s-file-1.ykt.cbern.com.cn/zxx/ndrs/resources/tch_material/version/data_version.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val publicHeaders = mapOf(
        "Origin" to "https://basic.smartedu.cn",
        "Referer" to "https://basic.smartedu.cn/",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    )

    class CatalogException(message: String) : Exception(message)

    /** 拉取全部电子教材（去重）。目录接口公开、无需登录。 */
    suspend fun fetchTextbooks(): List<Textbook> = withContext(Dispatchers.IO) {
        val version = JSONObject(getText(DATA_VERSION))
        val urls = version.optString("urls")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (urls.isEmpty()) throw CatalogException("未获取到教材清单。")

        // 并发拉取各清单文件，缩短等待时间
        val lists = urls.map { url -> async(Dispatchers.IO) { parseBookList(url) } }.awaitAll()
        lists.flatten().distinctBy { it.id }
    }

    /** 流式解析一个清单文件里的教材。 */
    private fun parseBookList(url: String): List<Textbook> {
        val builder = Request.Builder().url(url)
        publicHeaders.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw CatalogException("请求失败 HTTP ${resp.code}: $url")
            val body = resp.body ?: throw CatalogException("响应为空: $url")
            val out = mutableListOf<Textbook>()
            JsonReader(body.byteStream().reader(Charsets.UTF_8)).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    readBook(reader)?.let { out.add(it) }
                }
                reader.endArray()
            }
            return out
        }
    }

    /** 读取清单数组中的一个对象，仅抽取 id/标题/分类标签。 */
    private fun readBook(reader: JsonReader): Textbook? {
        var id = ""
        var title = ""
        var stage = ""
        var subject = ""
        var version = ""
        var grade = ""
        var volume = ""
        var thumb: String? = null
        var hasTagPath = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = readString(reader)
                "version_id" -> if (id.isBlank()) id = readString(reader) else reader.skipValue()
                "title" -> title = readString(reader)
                "name" -> if (title.isBlank()) title = readString(reader) else reader.skipValue()
                "tag_paths" -> hasTagPath = readTagPath(reader)
                "thumbnails" -> thumb = readThumb(reader)
                "tag_list" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        var dim = ""
                        var name = ""
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "tag_dimension_id" -> dim = readString(reader)
                                "tag_name" -> name = readString(reader)
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        when (dim) {
                            "zxxxd" -> stage = name
                            "zxxxk" -> subject = name
                            "zxxbb" -> version = name
                            "zxxnj" -> grade = name
                            "zxxcc" -> volume = name
                        }
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (!hasTagPath || id.isBlank()) return null
        if (title.isBlank()) title = "(未命名教材)"
        return Textbook(id, title, stage, subject, version, grade, volume, thumb)
    }

    /** 读取 thumbnails 数组，返回第一个非空字符串。 */
    private fun readThumb(reader: JsonReader): String? {
        return if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray()
            var t: String? = null
            while (reader.hasNext()) {
                if (t == null && reader.peek() == JsonToken.STRING) t = reader.nextString()
                else reader.skipValue()
            }
            reader.endArray()
            t?.takeIf { it.isNotBlank() }
        } else {
            reader.skipValue()
            null
        }
    }

    /** 安全读取字符串字段：若非字符串（null/数字/数组…）则跳过并返回空串。 */
    private fun readString(reader: JsonReader): String {
        return if (reader.peek() == JsonToken.STRING) reader.nextString()
        else {
            reader.skipValue()
            ""
        }
    }

    /** 读取 tag_paths（可能是字符串，也可能是数组），返回是否存在有效的分类路径。 */
    private fun readTagPath(reader: JsonReader): Boolean {
        return when (reader.peek()) {
            JsonToken.STRING -> reader.nextString().isNotBlank()
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                var first = ""
                if (reader.hasNext()) {
                    if (reader.peek() == JsonToken.STRING) first = reader.nextString() else reader.skipValue()
                    while (reader.hasNext()) reader.skipValue()
                }
                reader.endArray()
                first.isNotBlank()
            }
            else -> {
                reader.skipValue()
                false
            }
        }
    }

    private fun getText(url: String): String {
        val builder = Request.Builder().url(url)
        publicHeaders.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string()
            if (!resp.isSuccessful || text == null) {
                throw CatalogException("请求失败 HTTP ${resp.code}: $url")
            }
            return text
        }
    }
}
