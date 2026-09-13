import Foundation

/// 平台返回的错误状态码，翻译成用户能看懂的话（与 Android 版 HttpErrors.kt 一致）。
struct AuthExpiredError: LocalizedError {
    var errorDescription: String? { "登录状态已失效，请重新登录后再试。" }
}

struct AccessDeniedError: LocalizedError {
    var errorDescription: String? {
        "平台拒绝了这次请求（HTTP 403）：可能是登录状态失效，或该资源需要权限。请重新登录后再试。"
    }
}

struct ApiError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

enum HttpFailure {
    static func make(code: Int, url: String) -> Error {
        switch code {
        case 401: return AuthExpiredError()
        case 403: return AccessDeniedError()
        case 404: return ApiError(message: "资源不存在（HTTP 404），可能已下架或链接有误。")
        case 500...599: return ApiError(message: "平台服务暂时不可用（HTTP \(code)），请稍后重试。")
        default: return ApiError(message: "请求失败 HTTP \(code)：\(url)")
        }
    }
}

enum PlatformHeaders {
    static let origin = "https://basic.smartedu.cn"
    static let referer = "https://basic.smartedu.cn/"
    static let userAgent =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    static let privateCDN = "https://r1-ndr-private.ykt.cbern.com.cn"

    static func apply(to request: inout URLRequest, credentials: AuthSigner.Credentials? = nil, url: String, method: String = "GET") {
        request.setValue(origin, forHTTPHeaderField: "Origin")
        request.setValue(referer, forHTTPHeaderField: "Referer")
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        if let credentials {
            request.setValue(AuthSigner.buildNdAuth(url: url, method: method, credentials: credentials), forHTTPHeaderField: "X-ND-AUTH")
            if !credentials.accessToken.isEmpty {
                request.setValue("Bearer \(credentials.accessToken)", forHTTPHeaderField: "Authorization")
            }
        }
    }
}

/// 教材目录。缓存 12 小时；清单文件串行拉取、间隔 1.5 秒（合规约束，见 README「本项目的红线」）。
enum CatalogApi {
    private static let dataVersionURL = URL(string:
        "https://s-file-1.ykt.cbern.com.cn/zxx/ndrs/resources/tch_material/version/data_version.json")!
    private static let cacheTTL: TimeInterval = 12 * 60 * 60
    private static let requestIntervalNanos: UInt64 = 1_500_000_000
    private static let session = makeSession()

    private static func makeSession() -> URLSession {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 120
        config.timeoutIntervalForResource = 900
        return URLSession(configuration: config)
    }

    private static var cacheURL: URL {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("catalog_cache.json")
    }

    static func fetchTextbooks(forceRefresh: Bool = false) async throws -> [Textbook] {
        if !forceRefresh, let cached = readFreshCache(), !cached.isEmpty {
            return cached
        }
        let books = try await fetchFromNetwork()
        writeCache(books)
        return books
    }

    private static func readFreshCache() -> [Textbook]? {
        let url = cacheURL
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: url.path),
              let modified = attrs[.modificationDate] as? Date,
              Date().timeIntervalSince(modified) < cacheTTL,
              let data = try? Data(contentsOf: url),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return nil
        }
        return arr.compactMap { dictionary in
            guard let id = dictionary["id"] as? String else { return nil }
            return Textbook(
                id: id,
                title: dictionary["title"] as? String ?? "(未命名教材)",
                stage: dictionary["stage"] as? String ?? "",
                subject: dictionary["subject"] as? String ?? "",
                version: dictionary["version"] as? String ?? "",
                grade: dictionary["grade"] as? String ?? "",
                volume: dictionary["volume"] as? String ?? "",
                thumb: dictionary["thumb"] as? String
            )
        }
    }

    private static func writeCache(_ books: [Textbook]) {
        let arr: [[String: Any]] = books.map { book in
            var dict: [String: Any] = [
                "id": book.id,
                "title": book.title,
                "stage": book.stage,
                "subject": book.subject,
                "version": book.version,
                "grade": book.grade,
                "volume": book.volume,
            ]
            if let thumb = book.thumb, !thumb.isEmpty { dict["thumb"] = thumb }
            return dict
        }
        guard let data = try? JSONSerialization.data(withJSONObject: arr) else { return }
        try? data.write(to: cacheURL, options: .atomic)
    }

    private static func fetchFromNetwork() async throws -> [Textbook] {
        let versionData = try await getData(dataVersionURL)
        let versionObj = (try? JSONSerialization.jsonObject(with: versionData)) as? [String: Any]
        let urls = ((versionObj?["urls"] as? String) ?? "")
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        guard !urls.isEmpty else { throw ApiError(message: "未获取到教材清单。") }

        var books: [Textbook] = []
        var seen = Set<String>()
        for (index, urlString) in urls.enumerated() {
            if index > 0 {
                try? await Task.sleep(nanoseconds: requestIntervalNanos)
            }
            guard let url = URL(string: urlString) else { continue }
            let data = try await getData(url)
            for book in parseList(data) where !seen.contains(book.id) {
                seen.insert(book.id)
                books.append(book)
            }
        }
        return books
    }

    private static func parseList(_ data: Data) -> [Textbook] {
        guard let arr = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] else { return [] }
        return arr.compactMap(readBook)
    }

    /// 读取清单数组中的一个对象，仅抽取 id/标题/分类标签（对应 CatalogApi.kt 的 readBook）。
    private static func readBook(_ obj: [String: Any]) -> Textbook? {
        var id = obj["id"] as? String ?? ""
        if id.isEmpty, let versionId = obj["version_id"] as? String { id = versionId }
        var title = obj["title"] as? String ?? ""
        if title.isEmpty, let name = obj["name"] as? String { title = name }
        guard hasTagPath(obj["tag_paths"]), !id.isEmpty else { return nil }

        var stage = "", subject = "", version = "", grade = "", volume = ""
        if let tags = obj["tag_list"] as? [[String: Any]] {
            for tag in tags {
                let dim = tag["tag_dimension_id"] as? String ?? ""
                let name = tag["tag_name"] as? String ?? ""
                switch dim {
                case "zxxxd": stage = name
                case "zxxxk": subject = name
                case "zxxbb": version = name
                case "zxxnj": grade = name
                case "zxxcc": volume = name
                default: break
                }
            }
        }
        let thumb = firstString(obj["thumbnails"]) ?? customPropertiesThumb(obj["custom_properties"])
        if title.isEmpty { title = "(未命名教材)" }
        return Textbook(id: id, title: title, stage: stage, subject: subject, version: version,
                        grade: grade, volume: volume, thumb: thumb)
    }

    private static func firstString(_ value: Any?) -> String? {
        if let array = value as? [Any] {
            for item in array {
                if let s = item as? String, !s.isEmpty { return s }
            }
        }
        return nil
    }

    private static func customPropertiesThumb(_ value: Any?) -> String? {
        guard let dict = value as? [String: Any] else { return nil }
        if let t = firstString(dict["thumbnails"]) { return t }
        guard let preview = dict["preview"] as? [String: Any] else { return nil }
        if let slide1 = preview["Slide1"] as? String, !slide1.isEmpty { return slide1 }
        for (_, v) in preview {
            if let s = v as? String, !s.isEmpty { return s }
        }
        return nil
    }

    private static func hasTagPath(_ value: Any?) -> Bool {
        if let s = value as? String { return !s.isEmpty }
        if let arr = value as? [Any] { return (arr.first as? String)?.isEmpty == false }
        return false
    }

    private static func getData(_ url: URL) async throws -> Data {
        var request = URLRequest(url: url)
        PlatformHeaders.apply(to: &request, url: url.absoluteString)
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ApiError(message: "响应异常：\(url.absoluteString)")
        }
        guard (200..<300).contains(http.statusCode) else {
            throw HttpFailure.make(code: http.statusCode, url: url.absoluteString)
        }
        return data
    }
}

/// 资源解析与章节目录（对应 SmartEduApi.kt）。
enum SmartEduApi {

    private static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 120
        return URLSession(configuration: config)
    }()

    private struct Source {
        let title: String
        let url: String
        let format: String
    }

    static func resolveById(_ contentId: String, credentials: AuthSigner.Credentials) async throws -> ResourceInfo {
        guard let url = URL(string:
            "https://s-file-1.ykt.cbern.com.cn/zxx/ndrv2/resources/tch_material/details/\(contentId).json") else {
            throw ApiError(message: "资源地址无效。")
        }
        let data = try await getJSON(url, credentials: credentials)
        guard let source = pickSource(data) else {
            throw ApiError(message: "未找到可下载的源文件。")
        }
        let title = combineTitle(data["title"] as? String ?? "", source.title)
        let chapters = await readChapters(data, credentials: credentials)
        return ResourceInfo(title: title, url: source.url, format: source.format,
                            edition: editionOf(data), chapters: chapters)
    }

    /// 在 ti_items 中寻找源文件（ti_is_source_file 优先，其次按 file_flag）。
    private static func pickSource(_ data: [String: Any]) -> Source? {
        guard let items = data["ti_items"] as? [[String: Any]] else { return nil }

        func urlOf(_ item: [String: Any]) -> String? {
            if let storage = item["ti_storage"] as? String, !storage.isEmpty {
                return storage.replacingOccurrences(of: "cs_path:${ref-path}", with: PlatformHeaders.privateCDN)
            }
            if let storages = item["ti_storages"] as? [Any] {
                for case let s as String in storages where !s.isEmpty { return s }
            }
            return nil
        }

        let title = data["title"] as? String ?? ""

        for item in items where (item["ti_is_source_file"] as? Bool) == true {
            let format = item["ti_format"] as? String ?? "pdf"
            if format == "folder" { continue }
            if let url = urlOf(item) { return Source(title: title, url: url, format: format) }
        }
        for item in items {
            let flag = item["ti_file_flag"] as? String ?? ""
            if !["source", "pdf", "ppt", "pptx", "doc", "docx"].contains(flag) { continue }
            let format = item["ti_format"] as? String ?? "pdf"
            if format == "folder" { continue }
            if let url = urlOf(item) { return Source(title: title, url: url, format: format) }
        }
        return nil
    }

    /// 读取 ebook_mapping + tree 生成章节目录；失败返回空列表，不阻断下载。
    private static func readChapters(_ data: [String: Any], credentials: AuthSigner.Credentials) async -> [Chapter] {
        do {
            guard let items = data["ti_items"] as? [[String: Any]] else { return [] }
            var mappingURLString: String?
            for item in items where (item["ti_file_flag"] as? String) == "ebook_mapping" {
                if let storage = item["ti_storage"] as? String, !storage.isEmpty {
                    mappingURLString = storage.replacingOccurrences(of: "cs_path:${ref-path}", with: PlatformHeaders.privateCDN)
                } else if let storages = item["ti_storages"] as? [Any],
                          let first = storages.first as? String, !first.isEmpty {
                    mappingURLString = first
                }
                if mappingURLString?.isEmpty == false { break }
            }
            guard let mappingURLString, let mappingURL = URL(string: mappingURLString) else { return [] }

            let mapData = try await getJSON(mappingURL, credentials: credentials)
            guard let ebookId = mapData["ebook_id"] as? String, !ebookId.isEmpty else { return [] }

            var pageByNode: [String: Int] = [:]
            if let mappings = mapData["mappings"] as? [[String: Any]] {
                for m in mappings {
                    guard let nodeId = m["node_id"] as? String else { continue }
                    pageByNode[nodeId] = (m["page_number"] as? NSNumber)?.intValue ?? 1
                }
            }

            guard let treeURL = URL(string:
                "https://s-file-1.ykt.cbern.com.cn/zxx/ndrv2/national_lesson/trees/\(ebookId).json") else {
                return []
            }
            let treeData = try await getData(treeURL)
            let root = try JSONSerialization.jsonObject(with: treeData, options: [.fragmentsAllowed])
            let nodes: [[String: Any]]
            if let arr = root as? [[String: Any]] {
                nodes = arr
            } else if let obj = root as? [String: Any], let children = obj["child_nodes"] as? [[String: Any]] {
                nodes = children
            } else {
                nodes = []
            }
            return buildChapters(nodes, pageByNode: pageByNode)
        } catch {
            return [] // 书签是增强项，失败静默降级
        }
    }

    private static func buildChapters(_ nodes: [[String: Any]], pageByNode: [String: Int]) -> [Chapter] {
        nodes.map { node in
            let children = buildChapters(node["child_nodes"] as? [[String: Any]] ?? [], pageByNode: pageByNode)
            return Chapter(
                title: node["title"] as? String ?? "",
                pageIndex: pageByNode[node["id"] as? String ?? ""],
                children: children
            )
        }
    }

    private static func editionOf(_ data: [String: Any]) -> String? {
        guard let tags = data["tag_list"] as? [[String: Any]] else { return nil }
        for tag in tags where (tag["tag_dimension_id"] as? String) == "zxxbb" {
            return tag["tag_name"] as? String
        }
        return nil
    }

    private static func combineTitle(_ root: String, _ resource: String) -> String {
        let r = root.trimmingCharacters(in: .whitespaces)
        let s = resource.trimmingCharacters(in: .whitespaces)
        if r.isEmpty { return resource }
        if r.lowercased() == s.lowercased() { return resource }
        return "\(r) - \(resource)"
    }

    private static func getJSON(_ url: URL, credentials: AuthSigner.Credentials?) async throws -> [String: Any] {
        let data = try await getData(url, credentials: credentials)
        guard let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            throw ApiError(message: "响应格式异常：\(url.absoluteString)")
        }
        return obj
    }

    private static func getData(_ url: URL, credentials: AuthSigner.Credentials? = nil) async throws -> Data {
        var request = URLRequest(url: url)
        PlatformHeaders.apply(to: &request, credentials: credentials, url: url.absoluteString)
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ApiError(message: "响应异常：\(url.absoluteString)")
        }
        guard (200..<300).contains(http.statusCode) else {
            throw HttpFailure.make(code: http.statusCode, url: url.absoluteString)
        }
        return data
    }
}

/// 下载引擎：现算 X-ND-AUTH 签名，流式写入应用下载目录。
///
/// ── 合规红线（勿删）──────────────────────────────────────────
/// 1. 下载结果只能写入用户本机，不得上传、缓存或中转到我方服务器——本项目也没有任何服务端。
/// 2. 不得对下载到的 PDF 做任何去水印、去权利标识或解密处理。
/// 3. 逐本下载之间保留间隔，不做并发批量抓取（间隔见 AppState）。
/// see README「本项目的红线」。
/// ─────────────────────────────────────────────────────────
final class DownloadEngine: NSObject, URLSessionDownloadDelegate {
    private var session: URLSession!
    private var progressHandler: ((Int64, Int64) -> Void)?
    private var lastReport = Date.distantPast

    override init() {
        super.init()
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 120
        config.timeoutIntervalForResource = 3600
        session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }

    static var downloadDirectory: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = docs.appendingPathComponent("Download", isDirectory: true)
        if !FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir
    }

    func download(
        resource: ResourceInfo,
        credentials: AuthSigner.Credentials,
        onProgress: @escaping (Int64, Int64) -> Void
    ) async throws -> URL {
        guard let url = URL(string: resource.url) else {
            throw ApiError(message: "资源地址无效。")
        }
        var request = URLRequest(url: url)
        PlatformHeaders.apply(to: &request, credentials: credentials, url: resource.url)

        progressHandler = onProgress
        lastReport = .distantPast
        defer { progressHandler = nil }

        let (tmpURL, response) = try await session.download(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ApiError(message: "响应异常。")
        }
        guard (200..<300).contains(http.statusCode) else {
            throw HttpFailure.make(code: http.statusCode, url: resource.url)
        }

        let ext = resource.format.isEmpty ? "pdf" : resource.format
        let outURL = Self.downloadDirectory.appendingPathComponent(sanitize(resource.title) + "." + ext)
        let fm = FileManager.default
        if fm.fileExists(atPath: outURL.path) { try? fm.removeItem(at: outURL) }
        try fm.moveItem(at: tmpURL, to: outURL)
        return outURL
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        let now = Date()
        guard now.timeIntervalSince(lastReport) >= 0.1 else { return }
        lastReport = now
        progressHandler?(totalBytesWritten, max(totalBytesExpectedToWrite, 0))
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        // 使用 async 的 download(for:) 时由系统管理临时文件，这里无需处理。
    }

    /// 去掉文件名里的非法字符，与 Android 版 sanitize 保持一致。
    private func sanitize(_ name: String) -> String {
        let invalid = CharacterSet(charactersIn: "\\/:*?\"<>|")
        let cleaned = name.components(separatedBy: invalid).joined(separator: "_")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let fallback = cleaned.isEmpty ? "未命名课本" : cleaned
        return String(fallback.prefix(120))
    }
}
