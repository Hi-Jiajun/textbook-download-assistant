import SwiftUI

@main
struct TextbookDownloaderApp: App {
    var body: some Scene {
        WindowGroup {
            LaunchScreen()
        }
    }
}

/// 骨架页：这一步只用于验证「GitHub Actions 的 macOS runner 能否编出未签名 IPA」。
/// 链路确认后再替换为完整功能（浏览目录、登录、解析、下载、书签、教材库）。
struct LaunchScreen: View {
    var body: some View {
        VStack(spacing: 12) {
            Text("教材下载助手")
                .font(.title2).bold()
            Text("iOS 版骨架构建成功")
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}
