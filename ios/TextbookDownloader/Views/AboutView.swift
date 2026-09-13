import SwiftUI
import UIKit

/// 「关于与免责」页：合规声明、承诺、自愿赞助入口、开源许可。
struct AboutView: View {
    @ObservedObject var state: AppState
    @State private var enlarged: String?

    private let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.1.0"

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button {
                    state.goBrowse()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("返回")
                    }
                }
                Spacer()
                Text("关于与免责").font(.headline)
                Spacer()
                Color.clear.frame(width: 60, height: 1)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("教材下载助手 v\(version)")
                        .font(.headline)
                    Text("免费开源的电子课本下载工具，与 Android 版共用同一套实现逻辑。")
                        .font(.footnote).foregroundColor(Theme.onSurfaceVariant)

                    SectionCard(title: "免责声明") {
                        ForEach(Array(LegalText.disclaimerSections.enumerated()), id: \.offset) { index, section in
                            if index > 0 { Spacer().frame(height: 10) }
                            Text(section.title).font(.subheadline).bold()
                            Text(section.body).font(.footnote).foregroundColor(Theme.onSurfaceVariant)
                        }
                    }

                    SectionCard(title: "本应用的承诺") {
                        ForEach([
                            "不存储、不托管、不分发任何教材内容，也没有任何服务端。",
                            "不移除教材中的任何水印或权利标识。",
                            "不提供、也不提示任何绕过平台登录或鉴权的方法。",
                            "永久免费，没有付费点、没有广告、不做任何数据上报。",
                        ], id: \.self) { line in
                            Text("· \(line)")
                                .font(.footnote).foregroundColor(Theme.onSurfaceVariant)
                        }
                    }

                    SectionCard(title: "支持开发") {
                        Text(LegalText.sponsorNotice)
                            .font(.footnote).foregroundColor(Theme.onSurfaceVariant)
                        Spacer().frame(height: 12)
                        HStack(spacing: 16) {
                            SponsorCode(name: "微信", imageName: "wechat") { enlarged = "微信" }
                            SponsorCode(name: "支付宝", imageName: "alipay") { enlarged = "支付宝" }
                        }
                    }

                    SectionCard(title: "开源许可") {
                        Text("本工程采用 MIT 许可，完整文本见仓库根目录 LICENSE。")
                            .font(.footnote).foregroundColor(Theme.onSurfaceVariant)
                        Spacer().frame(height: 6)
                        Text("上游项目 tchMaterial-parser（作者：肥宅水水呀）同样使用 MIT 许可，版权与许可声明见 THIRD_PARTY_NOTICES.md。")
                            .font(.footnote).foregroundColor(Theme.onSurfaceVariant)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
        }
        .sheet(item: Binding(
            get: { enlarged.map(IdentifiedText.init) },
            set: { enlarged = $0?.value }
        )) { item in
            VStack(spacing: 12) {
                if let image = UIImage(named: item.value == "微信" ? "wechat" : "alipay") {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .padding(16)
                }
                Text("\(item.value) · 点按空白处关闭")
                    .font(.footnote).foregroundColor(Theme.onSurfaceVariant)
            }
        }
    }
}

private struct IdentifiedText: Identifiable {
    let value: String
    var id: String { value }
}

private struct SectionCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.subheadline).bold()
            Divider()
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Theme.surfaceVariant)
        .cornerRadius(18)
    }
}

private struct SponsorCode: View {
    let name: String
    let imageName: String
    let onTap: () -> Void

    var body: some View {
        VStack(spacing: 4) {
            if let image = UIImage(named: imageName) {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .onTapGesture(perform: onTap)
                    .accessibilityLabel("\(name) 收款码")
            }
            Text(name).font(.footnote).bold()
            Text("点按放大").font(.caption2).foregroundColor(Theme.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
    }
}

/// 首次启动的使用声明：必须主动点「我已知悉」，不能跳过。
struct UsageNoticeView: View {
    let onAccept: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 12) {
                Text("使用前请阅读").font(.title3).bold()
                ScrollView {
                    Text(LegalText.usageNotice)
                        .font(.footnote)
                        .lineSpacing(3)
                }
                .frame(maxHeight: 420)
                HStack {
                    Spacer()
                    Button("我已知悉") { onAccept() }
                        .font(.headline)
                }
            }
            .padding(20)
            .background(Color(.systemBackground))
            .cornerRadius(20)
            .padding(24)
        }
    }
}
