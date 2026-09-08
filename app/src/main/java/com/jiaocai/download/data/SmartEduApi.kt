package com.jiaocai.download.data

import com.jiaocai.download.model.Chapter
import com.jiaocai.download.model.ResourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * 解析单个资源页面，获取资源标题与下载直链。接口地址与解析逻辑参照上游
 * tchMaterial-parser 的 api.py 移植。
 */
object SmartEduApi {

    private const val PRIVATE_CDN = "https://r1-ndr-private.ykt.cbern.com.cn"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 公开 JSON 解析用的通用头（不含签名，下载私有资源须另用 DownloadEngine）。 */
    private val publicHeaders = mapOf(
        "Origin" to "https://basic.smartedu.cn",
        "Referer" to "https://basic.smartedu.cn/",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    )

    class ResolveException(message: String) : Exception(message)

    /** 从预览页 URL 提取 contentId / contentType。仅实现主路径，其余走 TODO。 */
    fun parseContentId(previewUrl: String): Pair<String, String>? {
        val uri = URI(previewUrl)
        val query = parseQuery(uri.rawQuery ?: "")
        val contentId = query["contentId"] ?: return null
        val contentType = query["contentType"] ?: "assets_document"
        return contentId to contentType
    }

    /** 解析资源，返回可用于下载的直链信息。bookmarks=true 时尝试读取章节目录（失败静默降级）。 */
    suspend fun resolve(previewUrl: String, credentials: AuthSigner.Credentials, bookmarks: Boolean = true): ResourceInfo =
        withContext(Dispatchers.IO) {
            val (contentId, contentType) = parseContentId(previewUrl)
                ?: throw ResolveException("无法从链接解析出 contentId，请确认是国家中小学智慧教育平台的电子课本预览链接。")

            // 当前聚焦普通电子课本（assets_document）。其余类型见 API 注释 TODO。
            if (contentType != "assets_document") {
                // 专题课等类型：见上游 api.py 的多分支；此处保留扩展点。
                throw ResolveException("当前原型仅支持普通电子课本（assets_document）。")
            }

            buildResource(getJson(detailUrl(contentId)), credentials, bookmarks)
        }

    /** 直接从 contentId 解析（供应用内「浏览勾选」流程使用，无需拼预览链接）。 */
    suspend fun resolveById(contentId: String, credentials: AuthSigner.Credentials, bookmarks: Boolean = true): ResourceInfo =
        withContext(Dispatchers.IO) {
            buildResource(getJson(detailUrl(contentId)), credentials, bookmarks)
        }

    private fun detailUrl(contentId: String): String =
        "https://s-file-1.ykt.cbern.com.cn/zxx/ndrv2/resources/tch_material/details/$contentId.json"

    /** 由详情 JSON 构造可下载的 ResourceInfo。 */
    private fun buildResource(
        data: JSONObject,
        credentials: AuthSigner.Credentials,
        bookmarks: Boolean,
    ): ResourceInfo {
        val rootTitle = data.optString("title")
        val source = pickSource(data) ?: throw ResolveException("未找到可下载的源文件。")
        val title = combineTitle(rootTitle, source.title)
        return ResourceInfo(
            title = title,
            url = source.url,
            format = source.format,
            chapters = if (bookmarks) readChapters(data, credentials) else emptyList(),
            edition = editionOf(data),
        )
    }

    private data class Source(val title: String, val url: String, val format: String)

    /** 在 ti_items 中寻找源文件（ti_is_source_file=true 优先，其次按 file_flag）。 */
    private fun pickSource(data: JSONObject): Source? {
        val title = data.optString("title")
        val items = data.optJSONArray("ti_items") ?: return null

        fun urlOf(item: JSONObject): String? {
            val storage = item.optString("ti_storage")
            if (storage.isNotEmpty()) {
                return storage.replace("cs_path:\${ref-path}", PRIVATE_CDN)
            }
            val storages = item.optJSONArray("ti_storages")
            if (storages != null) {
                for (i in 0 until storages.length()) {
                    val u = storages.optString(i)
                    if (u.isNotEmpty()) return u
                }
            }
            return null
        }

        // 1) 优先源文件
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            if (!item.optBoolean("ti_is_source_file")) continue
            val format = item.optString("ti_format", "pdf")
            if (format == "folder") continue
            val url = urlOf(item) ?: continue
            return Source(title, url, format)
        }

        // 2) 兜底按 file_flag
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val flag = item.optString("ti_file_flag")
            if (flag !in setOf("source", "pdf", "ppt", "pptx", "doc", "docx")) continue
            val format = item.optString("ti_format", "pdf")
            if (format == "folder") continue
            val url = urlOf(item) ?: continue
            return Source(title, url, format)
        }
        return null
    }

    /** 读取 ebook_mapping + tree 生成章节目录。失败时返回空列表，不阻断下载。 */
    private fun readChapters(data: JSONObject, credentials: AuthSigner.Credentials): List<Chapter> {
        return try {
            val items = data.optJSONArray("ti_items") ?: return emptyList()
            var mappingUrl: String? = null
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                if (item.optString("ti_file_flag") != "ebook_mapping") continue
                val storage = item.optString("ti_storage")
                mappingUrl = if (storage.isNotEmpty()) storage.replace("cs_path:\${ref-path}", PRIVATE_CDN)
                else {
                    val storages = item.optJSONArray("ti_storages")
                    if (storages != null && storages.length() > 0) storages.optString(0) else null
                }
                if (!mappingUrl.isNullOrEmpty()) break
            }
            if (mappingUrl.isNullOrEmpty()) return emptyList()

            // mapping 在私有 CDN 上，必须按 URL 现算签名
            val mapData = getJson(mappingUrl, credentials)
            val ebookId = mapData.optString("ebook_id")
            val pageMap = mutableListOf<Pair<String, Int>>()
            val mappings = mapData.optJSONArray("mappings")
            if (mappings != null) {
                for (i in 0 until mappings.length()) {
                    val m = mappings.getJSONObject(i)
                    pageMap.add(m.optString("node_id") to m.optInt("page_number", 1))
                }
            }

            if (ebookId.isEmpty()) return emptyList()
            val treeBody = getText("https://s-file-1.ykt.cbern.com.cn/zxx/ndrv2/national_lesson/trees/$ebookId.json")
            val pageByNode = pageMap.toMap()

            fun build(nodes: JSONArray): List<Chapter> {
                val out = mutableListOf<Chapter>()
                for (i in 0 until nodes.length()) {
                    val node = nodes.getJSONObject(i)
                    val children = if (node.has("child_nodes")) build(node.getJSONArray("child_nodes")) else emptyList()
                    out.add(
                        Chapter(
                            title = node.optString("title"),
                            pageIndex = pageByNode[node.optString("id")],
                            children = children,
                        )
                    )
                }
                return out
            }

            when {
                treeBody.trimStart().startsWith("[") -> build(JSONArray(treeBody))
                JSONObject(treeBody).has("child_nodes") -> build(JSONObject(treeBody).getJSONArray("child_nodes"))
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList() // 书签是增强项，失败静默降级
        }
    }

    private fun editionOf(data: JSONObject): String? {
        val tags = data.optJSONArray("tag_list") ?: return null
        for (i in 0 until tags.length()) {
            val tag = tags.getJSONObject(i)
            if (tag.optString("tag_dimension_id") == "zxxbb") return tag.optString("tag_name")
        }
        return null
    }

    private fun combineTitle(root: String, resource: String): String {
        if (root.isBlank()) return resource
        if (root.trim().lowercase() == resource.trim().lowercase()) return resource
        return "$root - $resource"
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isEmpty()) return emptyMap()
        return rawQuery.split("&").mapNotNull {
            val idx = it.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            java.net.URLDecoder.decode(it.substring(0, idx), "UTF-8") to
                java.net.URLDecoder.decode(it.substring(idx + 1), "UTF-8")
        }.toMap()
    }

    private fun getJson(url: String, credentials: AuthSigner.Credentials? = null): JSONObject {
        return JSONObject(getText(url, credentials))
    }

    private fun getText(url: String, credentials: AuthSigner.Credentials? = null): String {
        val builder = Request.Builder().url(url)
        publicHeaders.forEach { (k, v) -> builder.header(k, v) }
        credentials?.let {
            builder.header("X-ND-AUTH", AuthSigner.buildNdAuth(url, "GET", it))
            builder.header("Authorization", "Bearer ${it.accessToken}")
        }
        client.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string()
            if (!resp.isSuccessful || body == null) {
                throw ResolveException("请求失败 HTTP ${resp.code}: $url")
            }
            return body
        }
    }
}
