import SwiftUI
import UIKit

/// 解析结果：确认后开始下载。
struct ResolveView: View {
    @ObservedObject var state: AppState

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("解析结果").font(.headline)

            if state.loading {
                ProgressView("正在解析资源…")
            }
            if let error = state.error {
                Text(error).font(.footnote).foregroundColor(.red)
            }
            if !state.resources.isEmpty && !state.loading {
                Text("共解析到 \(state.resources.count) 个资源：").font(.subheadline).bold()
            }

            ScrollView {
                VStack(spacing: 10) {
                    ForEach(Array(state.resources.enumerated()), id: \.offset) { _, resource in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(resource.title).bold()
                            Text("格式：\(resource.format.uppercased())   版别：\(resource.edition ?? "未知")")
                                .font(.subheadline)
                            Text("章节目录：\(resource.chapters.count) 项"
                                 + (resource.chapters.isEmpty ? "（无可写入书签）" : "（将写入书签）"))
                                .font(.subheadline)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                        .background(Theme.surfaceVariant)
                        .cornerRadius(14)
                    }
                }
            }

            HStack {
                Button("上一步") { state.goBrowse() }
                Spacer()
                Button("开始下载") { state.download() }
                    .disabled(state.resources.isEmpty || state.loading)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .background(Theme.primary)
                    .foregroundColor(.white)
                    .cornerRadius(14)
            }
        }
        .padding(20)
    }
}

/// 下载中 / 下载失败。失败时必须有出口：重试 或 返回首页。
struct DownloadView: View {
    @ObservedObject var state: AppState

    var body: some View {
        VStack(spacing: 20) {
            Spacer()

            if state.error == nil {
                ProgressView()
                    .scaleEffect(1.4)
                Text("正在下载并写入书签…").font(.headline)
                if state.resources.count > 1 {
                    Text("第 \(state.currentIndex + 1) / \(state.resources.count) 本")
                        .font(.footnote).foregroundColor(Theme.onSurfaceVariant)
                }
            } else {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.largeTitle).foregroundColor(.orange)
                Text("下载失败").font(.headline).foregroundColor(.red)
                Text(state.error ?? "")
                    .font(.footnote)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }

            if state.progressTotal > 0 {
                ProgressView(value: Double(state.progressDone), total: Double(state.progressTotal))
                    .padding(.horizontal, 32)
                Text("\(formatBytes(state.progressDone)) / \(formatBytes(state.progressTotal))")
                    .font(.footnote).foregroundColor(Theme.onSurfaceVariant)
            } else if state.progressDone > 0 {
                Text("已下载 \(formatBytes(state.progressDone))")
                    .font(.footnote).foregroundColor(Theme.onSurfaceVariant)
            }

            if state.error != nil {
                HStack(spacing: 12) {
                    Button("返回首页") { state.leaveFailedDownload() }
                        .frame(maxWidth: .infinity).frame(height: 50)
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.outline.opacity(0.3)))
                    Button("重试") { state.retryDownload() }
                        .frame(maxWidth: .infinity).frame(height: 50)
                        .background(Theme.primary).foregroundColor(.white).cornerRadius(14)
                }
                .padding(.horizontal, 24)
                Text("重试会从失败的那一本继续，已经下载好的不会重复下载。")
                    .font(.caption).foregroundColor(Theme.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }

            Spacer()
        }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let mb = Double(bytes) / 1024 / 1024
        if mb >= 1 { return String(format: "%.1f MB", mb) }
        let kb = Double(bytes) / 1024
        if kb >= 1 { return String(format: "%.0f KB", kb) }
        return "\(bytes) B"
    }
}

/// 下载完成。
struct DoneView: View {
    @ObservedObject var state: AppState

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("下载完成").font(.title2).bold()

            VStack(alignment: .leading, spacing: 6) {
                Text("已保存 \(state.downloadedCount) 本教材到应用下载目录").bold()
                Text("✅ 其中 \(state.bookmarksCount) 本已自动写入章节书签").font(.footnote)
                Text("可随时在「教材库」里离线打开这些 PDF。").font(.footnote)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Theme.primaryContainer)
            .cornerRadius(16)

            Spacer()

            HStack(spacing: 12) {
                Button("返回") { state.reset() }
                    .frame(maxWidth: .infinity).frame(height: 50)
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.outline.opacity(0.3)))
                Button("教材库") { state.openLibrary() }
                    .frame(maxWidth: .infinity).frame(height: 50)
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.outline.opacity(0.3)))
            }
        }
        .padding(24)
    }
}

/// 教材库：打开 / 分享 / 删除。
/// iOS 没有「打开所在位置」这种跨应用目录跳转，改为提示去「文件」App 查看（本应用开启了文件共享）。
struct LibraryView: View {
    @ObservedObject var state: AppState
    @State private var pendingDelete: SavedItem?
    @State private var shareItem: URL?
    @State private var showLocationHint = false
    @State private var toast: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("我的教材库").font(.title2).bold()
                Spacer()
                Button("返回") { state.goBrowse() }
            }

            if state.library.isEmpty {
                Spacer()
                Text("还没有下载的教材。回到首页勾选教材，跟着步骤下载后会自动收录到这里。")
                    .foregroundColor(Theme.onSurfaceVariant)
                Spacer()
            } else {
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(state.library) { item in
                            VStack(alignment: .leading, spacing: 10) {
                                HStack(alignment: .top) {
                                    RoundedRectangle(cornerRadius: 10)
                                        .fill(Theme.primary)
                                        .frame(width: 42, height: 42)
                                        .overlay(Text(String(item.format.prefix(1)).uppercased())
                                            .foregroundColor(.white).bold())
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(item.title).font(.subheadline).lineLimit(2)
                                        Text("\(item.format.uppercased())  ·  \(item.edition ?? "未知版别")")
                                            .font(.caption).foregroundColor(Theme.onSurfaceVariant)
                                    }
                                    Spacer()
                                    Button("删除") { pendingDelete = item }
                                        .font(.footnote).foregroundColor(.red)
                                }
                                HStack(spacing: 8) {
                                    Button("打开 / 分享") { shareItem = URL(fileURLWithPath: item.path) }
                                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.outline.opacity(0.3)))
                                    Button("文件位置") { showLocationHint = true }
                                        .frame(maxWidth: .infinity).padding(.vertical, 9)
                                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.outline.opacity(0.3)))
                                }
                            }
                            .padding(12)
                            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.outline.opacity(0.15)))
                        }
                    }
                }
            }
        }
        .padding(16)
        .sheet(item: Binding(
            get: { shareItem.map(ShareItem.init) },
            set: { shareItem = $0?.url }
        )) { item in
            ActivityView(activityItems: [item.url])
        }
        .alert("删除教材", isPresented: Binding(
            get: { pendingDelete != nil },
            set: { if !$0 { pendingDelete = nil } }
        )) {
            Button("取消", role: .cancel) { pendingDelete = nil }
            Button("删除", role: .destructive) {
                if let item = pendingDelete {
                    let ok = state.deleteFromLibrary(path: item.path)
                    toast = ok ? "已删除" : "删除失败：文件可能被其他应用占用"
                }
                pendingDelete = nil
            }
        } message: {
            Text("将同时删除本地 PDF 文件，且无法恢复。确定删除《\(pendingDelete?.title ?? "")》吗？")
        }
        .alert("文件位置", isPresented: $showLocationHint) {
            Button("好", role: .cancel) {}
        } message: {
            Text("PDF 保存在本应用的「文件」目录里。打开系统「文件」App → 我的 iPhone → 教材下载助手 → Download 即可看到，也可以在其中移动到别处。")
        }
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast)
                    .font(.footnote)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(Color.black.opacity(0.8))
                    .foregroundColor(.white)
                    .cornerRadius(10)
                    .padding(.bottom, 20)
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) { self.toast = nil }
                    }
            }
        }
    }
}

private struct ShareItem: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

/// 系统分享/打开面板（iOS 15 没有 ShareLink，用 UIActivityViewController 包装）。
struct ActivityView: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
