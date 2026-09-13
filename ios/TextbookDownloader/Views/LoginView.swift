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
final class LoginWebController: NSObject, ObservableObject, WKNavigationDelegate {
    let webView: WKWebView
    var onToken: ((String) -> Void)?
    private var captured = false
    private var pollTask: Task<Void, Never>?

    override init() {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        webView = WKWebView(frame: .zero, configuration: config)
        super.init()
        webView.navigationDelegate = self
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
                if let value = await self.extract() {
                    self.handle(value)
                    return
                }
                try? await Task.sleep(nanoseconds: 400_000_000)
            }
        }
    }

    private func extract() async -> String? {
        await withCheckedContinuation { continuation in
            webView.evaluateJavaScript(extractJS) { result, _ in
                continuation.resume(returning: result as? String)
            }
        }
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
        if !url.hasPrefix(loginURLPrefix) {
            // 登录成功后回跳官网：先抓 token，再阻止跳转。
            Task { [weak self] in
                guard let self else { return }
                self.handle(await self.extract())
            }
            decisionHandler(.cancel)
            return
        }
        decisionHandler(.allow)
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
                Text(loginURL).font(.caption).foregroundColor(Theme.onSurfaceVariant)
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
