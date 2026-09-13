package com.jiaocai.download.data

import android.content.Context
import android.os.Environment
import com.jiaocai.download.model.ResourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 负责按 URL 现算 X-ND-AUTH 签名头，把资源流式写入本地。
 *
 * ── 合规红线（勿删）──────────────────────────────────────────
 * 1. 下载结果只能写入用户本机（当前为应用专属外部下载目录），不得上传、缓存或
 *    中转到我方服务器——本项目也没有任何服务端。
 * 2. 不得对下载到的 PDF 做任何去水印、去权利标识或解密处理。教材水印属于权利人
 *    的技术措施与权利管理信息，移除它可能独立构成违法。
 * 3. 逐本下载之间保留间隔，不做并发批量抓取（间隔见 DownloadViewModel）。
 * 详见 README「本项目的红线」。
 * ─────────────────────────────────────────────────────────
 */
class DownloadEngine(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    class DownloadException(message: String) : Exception(message)

    /** 下载到应用专属下载目录，返回保存路径。onProgress 参数为 已下载字节 / 总字节（0 表示未知）。 */
    suspend fun download(
        resource: ResourceInfo,
        credentials: AuthSigner.Credentials,
        onProgress: (Long, Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val url = resource.url
        val authHeader = AuthSigner.buildNdAuth(url, "GET", credentials)
        val request = Request.Builder().url(url).header("X-ND-AUTH", authHeader)
            .header("Authorization", "Bearer ${credentials.accessToken}")
            .header("Origin", "https://basic.smartedu.cn")
            .header("Referer", "https://basic.smartedu.cn/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadException("下载失败 HTTP ${response.code}: $url")
            }
            val body = response.body ?: throw DownloadException("响应为空: $url")
            val total = body.contentLength()
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            dir.mkdirs()
            val ext = if (resource.format.isBlank()) "pdf" else resource.format
            val out = File(dir, sanitize(resource.title) + "." + ext)
            // 先写 .part 临时文件，完整落盘后再原子改名，避免下载中断留下半截文件。
            val part = File(dir, out.name + ".part")
            try {
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }
                if (!part.renameTo(out)) {
                    // 极少数文件系统不支持覆盖式改名时回退为复制。
                    part.copyTo(out, overwrite = true)
                    part.delete()
                }
            } catch (e: Exception) {
                part.delete()
                throw e
            }
            out
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifEmpty { "未命名课本" }.take(120)
    }
}
