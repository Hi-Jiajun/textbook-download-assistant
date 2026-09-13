package com.jiaocai.download.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import com.jiaocai.download.model.Textbook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
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

    /** 目录缓存有效期：12 小时。期间再次启动直接读本地缓存，省流量也更快。 */
    private const val CACHE_TTL_MS = 12 * 60 * 60 * 1000L

    /**
     * 合规约束：拉取多个清单文件之间的固定间隔（毫秒）。
     *
     * 平台《用户协议》第 6.1 条禁止以自动化程序批量获取平台内容。本应用只做用户
     * 主动触发的、串行的少量请求，不做并发抓取。修改前请先阅读 README 的
     * 「本项目的红线」一节。
     */
    private const val REQUEST_INTERVAL_MS = 1500L

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

    /**
     * 拉取全部电子教材（去重）。目录接口公开、无需登录。
     * 优先读本地缓存；缓存过期或强制刷新时才联网，成功后原子写回缓存。
     */
    suspend fun fetchTextbooks(context: Context, forceRefresh: Boolean = false): List<Textbook> =
        withContext(Dispatchers.IO) {
            val cache = File(context.filesDir, "catalog_cache.jsonl")
            if (!forceRefresh && isCacheFresh(cache)) {
                runCatching { readCache(cache) }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { return@withContext it }
            }
            val books = fetchFromNetwork()
            runCatching { writeCache(cache, books) }
            books
        }

    private fun isCacheFresh(cache: File): Boolean =
        cache.isFile && System.currentTimeMillis() - cache.lastModified() < CACHE_TTL_MS

    /** 从平台接口拉取并解析全部教材。 */
    private suspend fun fetchFromNetwork(): List<Textbook> {
        val version = JSONObject(getText(DATA_VERSION))
        val urls = version.optString("urls")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (urls.isEmpty()) throw CatalogException("未获取到教材清单。")

        // 合规约束：串行 + 固定间隔，不做并发批量抓取。清单文件通常只有几个，
        // 串行只多花几秒，换来的是「个人正常使用」而非「爬虫」的形态。
        val lists = urls.mapIndexed { index, url ->
            if (index > 0) delay(REQUEST_INTERVAL_MS)
            parseBookList(url)
        }
        return lists.flatten().distinctBy { it.id }
    }

    /** 缓存为 JSONL（每行一本），流式读写在旧机型上内存占用更小。 */
    internal fun readCache(cache: File): List<Textbook> {
        val out = mutableListOf<Textbook>()
        cache.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val o = JSONObject(line)
            out.add(
                Textbook(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    stage = o.optString("stage"),
                    subject = o.optString("subject"),
                    version = o.optString("version"),
                    grade = o.optString("grade"),
                    volume = o.optString("volume"),
                    thumb = o.optString("thumb").takeIf { it.isNotBlank() },
                ),
            )
        }
        return out
    }

    internal fun writeCache(cache: File, books: List<Textbook>) {
        val tmp = File(cache.parentFile, cache.name + ".tmp")
        tmp.bufferedWriter(Charsets.UTF_8).use { writer ->
            books.forEach { b ->
                val o = JSONObject()
                    .put("id", b.id)
                    .put("title", b.title)
                    .put("stage", b.stage)
                    .put("subject", b.subject)
                    .put("version", b.version)
                    .put("grade", b.grade)
                    .put("volume", b.volume)
                if (!b.thumb.isNullOrBlank()) o.put("thumb", b.thumb)
                writer.write(o.toString())
                writer.newLine()
            }
        }
        if (!tmp.renameTo(cache)) {
            tmp.copyTo(cache, overwrite = true)
            tmp.delete()
        }
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
                "thumbnails" -> if (thumb == null) thumb = readArrayFirstString(reader) else reader.skipValue()
                "custom_properties" -> if (thumb == null) thumb = readCustomPropertiesThumb(reader) else reader.skipValue()
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

    /** 读取 JSON 字符串数组，返回第一个非空字符串（封面预览地址）。 */
    private fun readArrayFirstString(reader: JsonReader): String? {
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

    /**
     * 教材的封面图存放在 custom_properties.thumbnails（数组），
     * 个别条目可能没有 thumbnails，此时回退到 custom_properties.preview 中的第一张图（Slide1）。
     */
    private fun readCustomPropertiesThumb(reader: JsonReader): String? {
        var t: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "thumbnails" -> if (t == null) t = readArrayFirstString(reader) else reader.skipValue()
                "preview" -> if (t == null) t = readPreviewFirst(reader) else reader.skipValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return t
    }

    /** 读取 custom_properties.preview（{Slide1: url, ...} 形式的对象），优先返回 Slide1。 */
    private fun readPreviewFirst(reader: JsonReader): String? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        reader.beginObject()
        var t: String? = null
        var slide1: String? = null
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (reader.peek() == JsonToken.STRING) {
                val value = reader.nextString().takeIf { it.isNotBlank() }
                if (name == "Slide1") slide1 = value
                if (t == null) t = value
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        return slide1 ?: t
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
