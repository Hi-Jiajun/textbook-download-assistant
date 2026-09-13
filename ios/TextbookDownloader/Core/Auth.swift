import Foundation
import CryptoKit

/// 国家中小学智慧教育平台 UC SDK 的 X-ND-AUTH 签名，与 Android 版 Auth.kt 逐行对齐
/// （Auth.kt 又是从上游 tchMaterial-parser 的 auth.py 移植的）。
///
/// 签名原文（末尾必须有空行，四段都以 \n 分隔）：
///   {nonce}\n{METHOD}\n{解码后的 path}{?query}\n{hostname}\n
///
/// ── 合规红线（勿删）──────────────────────────────────────────
/// 这里复刻的是官网页面自身的请求签名方式，签名用的始终是「用户本人在官网登录后
/// 得到的凭据」。本文件不得加入任何形式的匿名访问、共享凭据或绕过登录的降级路径：
/// 上游 tchMaterial-parser 提供的「未登录也可下载」替代方法，本项目明确不实现。
/// 详见 README「本项目的红线」。
/// ─────────────────────────────────────────────────────────
enum AuthSigner {

    /// 官网 Ze() 的字符表。下标用 ceil(35 * random)，0 几乎抽不到，1–35 对应 1-9A-Z。
    private static let nonceAlphabet = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ")

    struct Credentials {
        let accessToken: String
        let macKey: String?
        let diff: Int64
    }

    enum TokenInputError: LocalizedError {
        case invalid(String)

        var errorDescription: String? {
            switch self {
            case .invalid(let message): return message
            }
        }
    }

    /// 按官网 Fe(diff) 生成 nonce：用服务器时间近似值，避免本地时钟偏差导致签名被拒。
    static func generateNonce(diff: Int64) -> String {
        var suffix = ""
        for _ in 0..<8 {
            let index = Int(ceil(35.0 * Double.random(in: 0..<1)))
            suffix.append(nonceAlphabet[min(max(index, 0), nonceAlphabet.count - 1)])
        }
        let millis = Int64((Date().timeIntervalSince1970 * 1000).rounded(.towardZero))
        return "\(millis + diff):\(suffix)"
    }

    /// 解码 %XX，但不把 '+' 当作空格（区别于 URLComponents 的行为，匹配 Python unquote）。
    /// 注意：这里按字节逐个映射到 U+00XX（与 Kotlin 的 byte.toChar() 完全一致），
    /// 不能换成 URLComponents 的 UTF-8 解码，否则签名会和 Android 版不一致。
    private static func decodePercent(_ raw: String) -> String {
        let chars = Array(raw)
        var out = ""
        out.reserveCapacity(chars.count)
        var i = 0
        while i < chars.count {
            let c = chars[i]
            if c == "%", i + 2 < chars.count,
               let byte = UInt8(String(chars[(i + 1)...(i + 2)]), radix: 16) {
                out.append(Character(UnicodeScalar(byte)))
                i += 3
                continue
            }
            out.append(c)
            i += 1
        }
        return out
    }

    /// 构造官网 ze() 的 HMAC 原文。path 先解码，query 原样保留，host 不含端口。
    static func signatureString(url: String, method: String, nonce: String) -> String {
        guard let comps = URLComponents(string: url) else { return "" }
        let rawPath = comps.percentEncodedPath
        let query = comps.percentEncodedQuery ?? ""
        let relative = decodePercent(rawPath) + (query.isEmpty ? "" : "?\(query)")
        return "\(nonce)\n\(method.uppercased())\n\(relative)\n\(comps.host ?? "")\n"
    }

    /// HMAC-SHA256(mac_key, 原文) 的 Base64，对应官网 CryptoJS.HmacSHA256(...).toString(Base64)。
    static func signMac(text: String, macKey: String) -> String {
        let key = SymmetricKey(data: Data(macKey.utf8))
        let mac = HMAC<SHA256>.authenticationCode(for: Data(text.utf8), using: key)
        return Data(mac).base64EncodedString()
    }

    /// 生成当前 URL 的 X-ND-AUTH。
    static func buildNdAuth(
        url: String,
        method: String = "GET",
        credentials: Credentials,
        nonce: String? = nil
    ) -> String {
        let tokenId = credentials.accessToken.isEmpty ? "0" : credentials.accessToken
        guard let macKey = credentials.macKey, !macKey.isEmpty else {
            // 旧版匿名/仅 Token 格式。CDN 有时只看 id，较严的对象存储会因此 400。
            return "MAC id=\"\(tokenId)\",nonce=\"0\",mac=\"0\""
        }
        let n = nonce ?? generateNonce(diff: credentials.diff)
        let mac = signMac(text: signatureString(url: url, method: method, nonce: n), macKey: macKey)
        return "MAC id=\"\(tokenId)\",nonce=\"\(n)\",mac=\"\(mac)\""
    }

    /// 解析用户粘贴/WebView 读取到的登录凭据 JSON。必须含 access_token / mac_key / diff。
    static func parseTokenInput(_ raw: String) throws -> Credentials {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return Credentials(accessToken: "", macKey: nil, diff: 0) }

        var jsonText = text
        // 控制台复制 JSON.stringify 的结果可能带一层引号。
        if jsonText.hasPrefix("\""), let data = jsonText.data(using: .utf8),
           let unquoted = try? JSONDecoder().decode(String.self, from: data) {
            jsonText = unquoted
        }
        guard let data = jsonText.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw TokenInputError.invalid("登录凭据必须是 JSON，且包含 access_token、mac_key、diff 三项。")
        }

        let missing = ["access_token", "mac_key", "diff"].filter { obj[$0] == nil }
        if !missing.isEmpty {
            throw TokenInputError.invalid("登录凭据缺少字段：\(missing.joined(separator: "、"))。必须包含 access_token、mac_key、diff 三项。")
        }
        guard let token = obj["access_token"] as? String, !token.isEmpty else {
            throw TokenInputError.invalid("access_token 必须是非空字符串。")
        }
        guard let macKey = obj["mac_key"] as? String, !macKey.isEmpty else {
            throw TokenInputError.invalid("mac_key 必须是非空字符串。")
        }
        let diff = (obj["diff"] as? NSNumber)?.int64Value ?? Int64(obj["diff"] as? String ?? "0") ?? 0
        return Credentials(accessToken: token.trimmingCharacters(in: .whitespacesAndNewlines), macKey: macKey, diff: diff)
    }
}
