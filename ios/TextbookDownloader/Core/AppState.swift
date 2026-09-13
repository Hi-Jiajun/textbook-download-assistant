import Foundation

/// 应用状态机，对应 Android 版的 DownloadViewModel。
@MainActor
final class AppState: ObservableObject {

    // 流程
    @Published var step: FlowStep = .browse
    @Published var needAgreement = false
    @Published var error: String?
    @Published var loading = false

    // 目录与筛选
    @Published var textbooks: [Textbook] = []
    @Published var catalogLoading = false
    @Published var catalogError: String?
    @Published var query = ""
    @Published var stageFilter = ""
    @Published var subjectFilter = ""
    @Published var versionFilter = ""
    @Published var selectedIds: Set<String> = []

    // 登录
    @Published var loggedIn = false
    @Published var loginHint = "请在下方网页中先登录国家中小学智慧教育平台账号，登录成功后会在这里提示。"

    // 解析与下载
    @Published var resources: [ResourceInfo] = []
    @Published var progressDone: Int64 = 0
    @Published var progressTotal: Int64 = 0
    @Published var currentIndex = 0
    @Published var downloadedCount = 0
    @Published var bookmarksCount = 0
    @Published var library: [SavedItem] = []

    /// 合规约束：单次批量下载上限与逐本间隔（见 README「本项目的红线」）。
    static let maxBatchSize = 10
    static let downloadIntervalNanos: UInt64 = 2_000_000_000

    private let engine = DownloadEngine()
    private let tokenStore = TokenStore()
    private let libraryStore = LibraryStore()
    private var prefs = AppPrefs()

    private var credentials: AuthSigner.Credentials?
    private var pendingDownload = false
    private var flowTask: Task<Void, Never>?
    private var retryFromIndex = 0
    private var retrySaved = 0
    private var retryBookmarks = 0

    init() {
        library = libraryStore.load()
        needAgreement = !prefs.hasAcceptedNotice
        restoreCredentials()
        Task { await loadCatalog() }
    }

    // MARK: - 使用声明

    func acceptNotice() {
        needAgreement = false
        prefs.hasAcceptedNotice = true
    }

    // MARK: - 凭据

    private func restoreCredentials() {
        guard let saved = tokenStore.load() else { return }
        if let cred = try? AuthSigner.parseTokenInput(saved), !cred.accessToken.isEmpty {
            credentials = cred
            loggedIn = true
            loginHint = "当前已登录。"
        }
    }

    func logout() {
        credentials = nil
        pendingDownload = false
        tokenStore.clear()
        loggedIn = false
        loginHint = "已退出登录。可重新登录或手动粘贴凭据。"
        error = nil
    }

    func openCredentials() {
        pendingDownload = false
        error = nil
        loginHint = credentials != nil
            ? "当前已是登录状态，可重新登录或手动更新凭据。"
            : "请登录国家中小学智慧教育平台账号，或手动粘贴凭据。"
        step = .login
    }

    /// WebView 抓到登录凭据后调用。
    func onTokenCaptured(_ json: String) {
        do {
            let cred = try AuthSigner.parseTokenInput(json)
            guard !cred.accessToken.isEmpty else { return }
            let wasLoggedIn = credentials != nil
            credentials = cred
            tokenStore.save(json)
            loggedIn = true
            error = nil
            if pendingDownload {
                let selected = textbooks.filter { selectedIds.contains($0.id) }
                Task { await beginResolveDownload(selected) }
            } else if !wasLoggedIn {
                loginHint = "登录成功，凭据已保存。"
                step = .browse
            } else {
                loginHint = "凭据已保存，当前已登录。"
            }
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? "未能识别登录凭据。"
        }
    }

    // MARK: - 目录

    func loadCatalog(forceRefresh: Bool = false) async {
        guard !catalogLoading else { return }
        catalogLoading = true
        catalogError = nil
        do {
            let books = try await CatalogApi.fetchTextbooks(forceRefresh: forceRefresh)
            textbooks = books
            catalogLoading = false
        } catch {
            catalogLoading = false
            catalogError = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        }
    }

    func refreshCatalog() {
        Task { await loadCatalog(forceRefresh: true) }
    }

    func dismissError() { error = nil }

    // MARK: - 筛选

    var filteredBooks: [Textbook] {
        textbooks.filter { book in
            (stageFilter.isEmpty || book.stage == stageFilter) &&
            (subjectFilter.isEmpty || book.subject == subjectFilter) &&
            (versionFilter.isEmpty || book.version == versionFilter) &&
            (query.isEmpty ||
             book.title.localizedCaseInsensitiveContains(query) ||
             book.subject.localizedCaseInsensitiveContains(query) ||
             book.grade.localizedCaseInsensitiveContains(query) ||
             book.version.localizedCaseInsensitiveContains(query))
        }
    }

    var stageOptions: [String] {
        uniqueSorted(textbooks.map(\.stage))
    }

    var subjectOptions: [String] {
        let base = stageFilter.isEmpty ? textbooks : textbooks.filter { $0.stage == stageFilter }
        return uniqueSorted(base.map(\.subject))
    }

    var versionOptions: [String] {
        let base = textbooks.filter {
            (stageFilter.isEmpty || $0.stage == stageFilter) &&
            (subjectFilter.isEmpty || $0.subject == subjectFilter)
        }
        return uniqueSorted(base.map(\.version))
    }

    private func uniqueSorted(_ values: [String]) -> [String] {
        Array(Set(values.filter { !$0.isEmpty })).sorted()
    }

    func setStage(_ value: String) {
        stageFilter = value
        subjectFilter = ""
        versionFilter = ""
    }

    func setSubject(_ value: String) {
        subjectFilter = value
        versionFilter = ""
    }

    func toggleSelect(_ id: String) {
        if selectedIds.contains(id) {
            selectedIds.remove(id)
            error = nil
            return
        }
        // 选满即止：避免"点了去下载却毫无反应"。
        if selectedIds.count >= Self.maxBatchSize {
            error = "单次最多下载 \(Self.maxBatchSize) 本（合规限制）。请先下载完这批，再勾选下一批。"
            return
        }
        selectedIds.insert(id)
        error = nil
    }

    // MARK: - 下载流程

    func startDownload() {
        let selected = textbooks.filter { selectedIds.contains($0.id) }
        guard !selected.isEmpty else {
            error = "请先勾选至少一本教材。"
            return
        }
        guard selected.count <= Self.maxBatchSize else {
            error = "单次最多下载 \(Self.maxBatchSize) 本（合规限制）。"
            return
        }
        guard credentials != nil else {
            pendingDownload = true
            loginHint = "请先登录国家中小学智慧教育平台账号，登录成功后会自动解析并下载你选中的教材。"
            error = nil
            step = .login
            return
        }
        Task { await beginResolveDownload(selected) }
    }

    private func beginResolveDownload(_ selected: [Textbook]) async {
        pendingDownload = false
        guard let cred = credentials, !selected.isEmpty else {
            error = "尚未获取登录凭据，请回到登录步骤。"
            step = .login
            return
        }
        cancelFlow()
        loading = true
        error = nil
        resources = []
        step = .resolve
        flowTask = Task { [weak self] in
            guard let self else { return }
            do {
                var results: [ResourceInfo] = []
                for book in selected {
                    try Task.checkCancellation()
                    results.append(try await SmartEduApi.resolveById(book.id, credentials: cred))
                }
                guard !Task.isCancelled else { return }
                self.resources = results
                self.loading = false
            } catch is CancellationError {
                return
            } catch {
                guard !Task.isCancelled else { return }
                self.handleFlowError(error, fallback: "解析失败，请检查链接与网络。")
            }
        }
    }

    func download() {
        Task { await downloadFrom(0, 0, 0) }
    }

    func retryDownload() {
        Task { await downloadFrom(retryFromIndex, retrySaved, retryBookmarks) }
    }

    private func downloadFrom(_ startIndex: Int, _ savedBefore: Int, _ bookmarksBefore: Int) async {
        guard !resources.isEmpty, resources.indices.contains(startIndex) else { return }
        guard let cred = credentials else {
            error = "登录凭据缺失，请先登录。"
            return
        }
        cancelFlow()
        loading = true
        error = nil
        step = .download
        progressDone = 0
        progressTotal = 0
        downloadedCount = savedBefore
        bookmarksCount = bookmarksBefore

        flowTask = Task { [weak self] in
            guard let self else { return }
            var saved = savedBefore
            var bookmarks = bookmarksBefore
            for index in startIndex..<self.resources.count {
                await MainActor.run {
                    self.currentIndex = index
                    self.progressDone = 0
                    self.progressTotal = 0
                }
                if index > startIndex {
                    try? await Task.sleep(nanoseconds: Self.downloadIntervalNanos)
                }
                if Task.isCancelled { return }
                let resource = self.resources[index]
                do {
                    let fileURL = try await self.engine.download(resource: resource, credentials: cred) { done, total in
                        Task { @MainActor in
                            self.progressDone = done
                            self.progressTotal = total
                        }
                    }
                    let added = PdfBookmarker.addBookmarks(fileURL: fileURL, chapters: resource.chapters)
                    if added { bookmarks += 1 }
                    let item = SavedItem(title: resource.title, path: fileURL.path, format: resource.format,
                                         edition: resource.edition, addedAt: Int64(Date().timeIntervalSince1970 * 1000))
                    _ = self.libraryStore.append(item)
                    saved += 1
                    await MainActor.run {
                        self.downloadedCount = saved
                        self.bookmarksCount = bookmarks
                    }
                } catch {
                    if Task.isCancelled { return }
                    self.retryFromIndex = index
                    self.retrySaved = saved
                    self.retryBookmarks = bookmarks
                    let prefix = self.resources.count > 1 ? "第 \(index + 1)/\(self.resources.count) 本下载失败：" : ""
                    await MainActor.run {
                        self.handleFlowError(error, fallback: "下载失败，请检查网络后重试。")
                        self.error = prefix + (self.error ?? "下载失败")
                    }
                    return
                }
            }
            self.retryFromIndex = 0
            self.retrySaved = 0
            self.retryBookmarks = 0
            await MainActor.run {
                self.library = self.libraryStore.load()
                self.loading = false
                self.step = .done
                self.downloadedCount = saved
                self.bookmarksCount = bookmarks
                self.selectedIds = []
            }
        }
    }

    /// 下载失败后从「下载中」页面退回首页：保留已勾选的教材，方便再次发起。
    func leaveFailedDownload() {
        cancelFlow()
        loading = false
        error = nil
        progressDone = 0
        progressTotal = 0
        step = .browse
    }

    private func handleFlowError(_ error: Error, fallback: String) {
        let expired = error is AuthExpiredError
        if expired {
            credentials = nil
            pendingDownload = false
            tokenStore.clear()
            loggedIn = false
            loginHint = "登录状态已失效，请重新登录。"
        }
        loading = false
        self.error = (error as? LocalizedError)?.errorDescription ?? fallback
    }

    private func cancelFlow() {
        flowTask?.cancel()
        flowTask = nil
    }

    // MARK: - 教材库与导航

    func openLibrary() {
        cancelFlow()
        library = libraryStore.load()
        error = nil
        step = .library
    }

    func openAbout() {
        cancelFlow()
        error = nil
        step = .about
    }

    func goBrowse() {
        cancelFlow()
        error = nil
        step = .browse
    }

    func deleteFromLibrary(path: String) -> Bool {
        let ok = libraryStore.remove(path: path)
        library = libraryStore.load()
        return ok
    }

    func reset() {
        cancelFlow()
        pendingDownload = false
        retryFromIndex = 0
        retrySaved = 0
        retryBookmarks = 0
        selectedIds = []
        resources = []
        loading = false
        error = nil
        progressDone = 0
        progressTotal = 0
        currentIndex = 0
        downloadedCount = 0
        bookmarksCount = 0
        loginHint = credentials != nil ? "当前已登录。" : "请登录国家中小学智慧教育平台账号，或手动粘贴凭据。"
        step = .browse
    }
}
