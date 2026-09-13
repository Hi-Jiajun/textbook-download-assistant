package com.jiaocai.download.data

import android.util.Base64
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil
import kotlin.random.Random

/**
 * 国家中小学智慧教育平台 UC SDK 的 X-ND-AUTH 签名，从上游 tchMaterial-parser 的
 * auth.py 忠实移植。官网登录后把 access_token / mac_key / diff 写入 localStorage 的
 * ND_UC_AUTH-*&token；页面每次请求都会现算签名，而不是复用整段头。
 *
 * 签名原文（末尾必须有空行，四段都以 \n 分隔）：
 *   {nonce}\n{METHOD}\n{解码后的 path}{?query}\n{hostname}\n
 *
 * ── 合规红线（勿删）──────────────────────────────────────────
 * 这里复刻的是官网页面自身的请求签名方式，签名用的始终是「用户本人在官网登录后
 * 得到的凭据」。本文件不得加入任何形式的匿名访问、共享凭据或绕过登录的降级路径：
 * 上游 tchMaterial-parser 提供的「未登录也可下载」替代方法，本项目明确不实现。
 * 详见 README「本项目的红线」。
 * ─────────────────────────────────────────────────────────
 */
object AuthSigner {
    // 官网 Ze() 的字符表。下标用 Math.ceil(35 * Math.random())，0 几乎抽不到，1–35 对应 1-9A-Z。
    private const val NONCE_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /** 登录凭据。macKey 为空时只能生成占位 X-ND-AUTH。 */
    data class Credentials(
        val accessToken: String,
        val macKey: String? = null,
        val diff: Long = 0, // 毫秒；与官网 Fe(diff) 的 parseInt(diff, 10) 一致
    )

    class TokenInputError(message: String) : Exception(message)

    /** 按官网 Fe(diff) 生成 nonce：用服务器时间近似值，避免本地时钟偏差导致签名被拒。 */
    fun generateNonce(diff: Long): String {
        val suffix = buildString {
            repeat(8) {
                val idx = ceil(35.0 * Random.nextDouble()).toInt()
                append(NONCE_ALPHABET[idx])
            }
        }
        return "${System.currentTimeMillis() + diff}:$suffix"
    }

    /** 解码 %XX，但不把 '+' 当作空格（区别于 URLDecoder，匹配 Python unquote）。 */
    private fun decodePercent(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '%' && i + 2 < raw.length) {
                val byte = raw.substring(i + 1, i + 3).toIntOrNull(16)
                if (byte != null) {
                    out.append(byte.toChar())
                    i += 3
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /** 构造官网 ze() 的 HMAC 原文。path 先解码，query 原样保留，host 不含端口。 */
    fun signatureString(url: String, method: String, nonce: String): String {
        val uri = URI(url)
        val rawPath = uri.rawPath ?: ""
        val query = uri.rawQuery ?: ""
        val relative = decodePercent(rawPath) + (if (query.isNotEmpty()) "?$query" else "")
        return "$nonce\n${method.uppercase()}\n$relative\n${uri.host.orEmpty()}\n"
    }

    /** HMAC-SHA256(mac_key, 原文) 的 Base64，对应官网 CryptoJS.HmacSHA256(...).toString(Base64)。 */
    fun signMac(text: String, macKey: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    /** 生成当前 URL 的 X-ND-AUTH。 */
    fun buildNdAuth(
        url: String,
        method: String = "GET",
        credentials: Credentials,
        nonce: String? = null,
    ): String {
        val tokenId = credentials.accessToken.ifEmpty { "0" }
        val macKey = credentials.macKey
        if (macKey.isNullOrBlank()) {
            // 旧版匿名/仅 Token 格式。CDN 有时只看 id，较严的对象存储会因此 400。
            return "MAC id=\"$tokenId\",nonce=\"0\",mac=\"0\""
        }
        val n = nonce ?: generateNonce(credentials.diff)
        val mac = signMac(signatureString(url, method, n), macKey)
        return "MAC id=\"$tokenId\",nonce=\"$n\",mac=\"$mac\""
    }

    /** 解析用户粘贴/WebView 读取到的登录凭据 JSON。必须是含三项的 JSON 对象。 */
    fun parseTokenInput(raw: String): Credentials {
        val text = raw.trim()
        if (text.isEmpty()) return Credentials("")

        val obj = try {
            val value = JSONTokener(text).nextValue()
            when {
                value is JSONObject -> value
                value is String -> JSONObject(value) // 控制台复制 JSON.stringify 结果可能带一层引号
                else -> throw TokenInputError("登录凭据必须是 JSON 对象")
            }
        } catch (e: Exception) {
            if (e is TokenInputError) throw e
            throw TokenInputError("登录凭据必须是 JSON，且包含 access_token、mac_key、diff 三项。")
        }

        val missing = listOf("access_token", "mac_key", "diff")
            .filter { !obj.has(it) }
        if (missing.isNotEmpty()) {
            throw TokenInputError("登录凭据缺少字段：${missing.joinToString()}。必须包含 access_token、mac_key、diff 三项。")
        }

        val token = obj.optString("access_token")
        val macKey = obj.optString("mac_key")
        if (token.isBlank()) throw TokenInputError("access_token 必须是非空字符串。")
        if (macKey.isBlank()) throw TokenInputError("mac_key 必须是非空字符串。")

        val diff = obj.optLong("diff", 0)
        return Credentials(token.trim(), macKey, diff)
    }
}
