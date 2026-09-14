import SwiftUI
import WebKit

private let loginURL = "https://auth.smartedu.cn/uias/login"
private let loginURLPrefix = "https://auth.smartedu.cn"

/// 从 auth 域 localStorage 里取登录凭据（与 Android 版 EXTRACT_JS 完全一致）。
private let extractJS = """
(function () {
  try {
    const authKey = Object.keys(localStorage).find(
      key => /^ND_UC_AUTH-[^&]+&[^&]+&token$/.test(key)
    );
    if (!authKey) return "__NO_TOKEN__";
    const tokenData = JSON.parse(localStorage.getItem(authKey));
    let cred = null;
    if (tokenData && typeof tokenData.value === "string") {
      cred = JSON.parse(tokenData.value);
    } else if (tokenData && tokenData.cred) {
      cred = tokenData.cred;
    } else if (tokenData) {
      cred = tokenData;
    }
    if (!cred) return "__NO_TOKEN__";
    const access_token = cred.access_token;
    const mac_key = cred.mac_key;
    const diff = cred.diff;
    const hasAll = access_token && mac_key && diff !== undefined && diff !== null && diff !== "";
    if (hasAll) return { access_token: access_token, mac_key: mac_key, diff: diff };
    return "__NO_TOKEN__";
  } catch (e) {
    return "__NO_TOKEN__";
  }
})();
"""

/// 内嵌官网登录页：登录成功后离开 auth 域时拦截跳转，直接从 localStorage 取走凭据。
final class LoginWebController: NSObject, ObservableObject, WKNavigationDelegate, WKUIDelegate {
    let webView: WKWebView
    var onToken: ((String) -> Void)?
    /// 当前页面地址，显示在界面上，便于用户反馈「卡在哪一页」。
    @Published var currentURL: String = loginURL
    /// 凭据探测状态，显示在界面上（只显示"是否读到"，不显示凭据内容）。
    @Published var tokenProbe: String = "等待页面加载…"
    private var captured = false
    private var pollTask: Task<Void, Never>?

    override init() {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        // 与 Android 版 settings.javaScriptCanOpenWindowsAutomatically 对齐：
        // 验证码/登录页可能用 window.open 打开，不允许的话会被拦掉。
        config.preferences.javaScriptCanOpenWindowsAutomatically = true
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        webView = WKWebView(frame: .zero, configuration: config)
        super.init()
        webView.navigationDelegate = self
        webView.uiDelegate = self
    }

    func loadFresh() {
        captured = false
        webView.load(URLRequest(url: URL(string: loginURL)!))
        startPolling()
    }

    func reload() {
        captured = false
        webView.reload()
        startPolling()
    }

    func clearSession() {
        captured = false
        webView.evaluateJavaScript("localStorage.clear();", completionHandler: nil)
        let store = WKWebsiteDataStore.default()
        store.fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { records in
            store.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), for: records) {}
        }
    }

    func stopPolling() {
        pollTask?.cancel()
        pollTask = nil
    }

    private func startPolling() {
        pollTask?.cancel()
        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self, !self.captured else { return }
                if let url = self.webView.url?.absoluteString, url != self.currentURL {
                    self.currentURL = url
                }
                let raw = await self.extractRaw()
                self.tokenProbe = self.describe(raw)
                if let json = Self.jsonString(from: raw) {
                    self.handle(json)
                    return
                }
                try? await Task.sleep(nanoseconds: 400_000_000)
            }
        }
    }

    /// 执行取凭据脚本，返回原始结果（字符串或字典，取决于平台桥接方式）。
    private func extractRaw() async -> Any? {
        await withCheckedContinuation { continuation in
            webView.evaluateJavaScript(extractJS) { result, _ in
                continuation.resume(returning: result)
            }
        }
    }

    /// 把探测结果显示成中文状态（绝不显示凭据内容）。
    private func describe(_ raw: Any?) -> String {
        if let text = raw as? String {
            if text == "__NO_TOKEN__" { return "未在页面中找到登录凭据" }
            if text.hasPrefix("{") { return "已读取到登录凭据" }
            return "页面返回了未知内容"
        }
        if raw is [String: Any] { return "已读取到登录凭据" }
        return "等待页面加载…"
    }

    /// 关键差异：Android 的 evaluateJavascript 把 JS 对象返回成 JSON 字符串，
    /// 而 iOS 的 WKWebView 会把它桥接成 NSDictionary。之前只处理 String，
    /// 导致取到凭据的那一刻返回 nil——登录成功却抓不到凭据，跳转又被拦下，
    /// 于是卡在变暗的登录页。
    private static func jsonString(from raw: Any?) -> String? {
        if let text = raw as? String {
            return text.hasPrefix("{") ? text : nil
        }
        guard let object = raw as? [String: Any], JSONSerialization.isValidJSONObject(object),
              let data = try? JSONSerialization.data(withJSONObject: object),
              let text = String(data: data, encoding: .utf8) else { return nil }
        return text
    }

    private func handle(_ raw: String?) {
        guard !captured, let text = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              text.hasPrefix("{"), !text.hasPrefix("\"") else { return }
        captured = true
        onToken?(text)
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        let url = navigationAction.request.url?.absoluteString ?? ""

        // 关键：只对「主框架」导航做拦截。
        // Android 的 shouldOverrideUrlLoading 默认只为顶层导航回调，而 iOS 的
        // decidePolicyFor 连 iframe（滑块验证码就在 iframe 里）也会回调——如果一律
        // 取消，验证码就永远加载不出来。targetFrame == nil 视为新窗口请求，按主框架处理。
        let isMainFrame = navigationAction.targetFrame?.isMainFrame ?? true
        if !isMainFrame {
            decisionHandler(.allow)
            return
        }

        if !url.hasPrefix(loginURLPrefix) {
            // 登录成功后回跳官网：先抓 token，再阻止跳转。
            Task { [weak self] in
                guard let self else { return }
                let raw = await self.extractRaw()
                self.tokenProbe = self.describe(raw)
                self.handle(Self.jsonString(from: raw))
            }
            decisionHandler(.cancel)
            return
        }
        decisionHandler(.allow)
    }

    /// window.open / target=_blank：Android 的 WebView 默认在同窗口打开，iOS 默认会
    /// 静默丢弃（验证码弹窗就是这么消失的）。这里改成同窗口加载，行为与 Android 对齐；
    /// 若目标不是 auth 域，随后的 decidePolicyFor 会照常拦下并取走凭据。
    func webView(
        _ webView: WKWebView,
        createWebViewWith configuration: WKWebViewConfiguration,
        for navigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ) -> WKWebView? {
        webView.load(navigationAction.request)
        return nil
    }
}

struct LoginWebView: UIViewRepresentable {
    let controller: LoginWebController

    func makeUIView(context: Context) -> WKWebView { controller.webView }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

/// 登录与凭据管理页。
struct LoginView: View {
    @ObservedObject var state: AppState
    @StateObject private var controller = LoginWebController()
    @State private var showManual = false
    @State private var manualJSON = ""

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button("返回") { state.goBrowse() }
                Spacer()
                Button("重新加载") { controller.reload() }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)

            VStack(alignment: .leading, spacing: 6) {
                Text("登录 国家中小学智慧教育平台").font(.headline)
                Text(controller.currentURL).font(.caption).foregroundColor(Theme.onSurfaceVariant).lineLimit(2)
                Text("凭据状态：\(controller.tokenProbe)")
                    .font(.caption2).foregroundColor(Theme.onSurfaceVariant)
                Text(state.error ?? state.loginHint)
                    .font(.footnote)
                    .foregroundColor(state.error == nil ? Theme.onSurfaceVariant : Theme.onErrorContainer)
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(state.error == nil ? Theme.surfaceVariant : Theme.errorContainer)
                    .cornerRadius(10)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            LoginWebView(controller: controller)

            VStack(spacing: 8) {
                if state.loggedIn {
                    HStack(spacing: 8) {
                        Button("重新登录") {
                            controller.clearSession()
                            state.logout()
                            controller.loadFresh()
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.outline.opacity(0.3)))

                        Button("退出登录") {
                            controller.clearSession()
                            state.logout()
                            state.goBrowse()
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.outline.opacity(0.3)))
                    }
                }
                Button(showManual ? "收起手动粘贴凭据" : "网页打不开？手动粘贴凭据") {
                    showManual.toggle()
                }
                .font(.footnote)
                if showManual {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("在浏览器打开 \(loginURL) 并登录，按 F12 → 控制台，用取凭据脚本复制出整段 JSON 粘到下面；格式为 { access_token, mac_key, diff }。")
                            .font(.caption)
                        TextEditor(text: $manualJSON)
                            .frame(height: 90)
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.outline.opacity(0.3)))
                        Button("用这份凭据登录") {
                            guard !manualJSON.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                            state.onTokenCaptured(manualJSON)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Theme.primaryContainer)
                        .cornerRadius(12)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .onAppear {
            controller.onToken = { state.onTokenCaptured($0) }
            if controller.webView.url == nil { controller.loadFresh() }
        }
        .onDisappear { controller.stopPolling() }
    }
}
