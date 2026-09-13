import SwiftUI

/// 第一步：浏览并勾选要下载的教材。
struct BrowseView: View {
    @ObservedObject var state: AppState

    var body: some View {
        VStack(spacing: 0) {
            header
            searchAndFilters
            content
            bottomBar
        }
        .background(Color(.systemBackground))
    }

    private var header: some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 12)
                .fill(Theme.primaryContainer)
                .frame(width: 44, height: 44)
                .overlay(Text("书").font(.headline).foregroundColor(Theme.primary))
            VStack(alignment: .leading, spacing: 2) {
                Text("教材下载助手").font(.title2).bold()
                Text("选择需要的教材，一键离线下载")
                    .font(.footnote).foregroundColor(Theme.onSurfaceVariant)
            }
            Spacer(minLength: 0)
            Button(state.loggedIn ? "已登录" : "登录") { state.openCredentials() }
                .font(.subheadline).bold()
            Button("教材库") { state.openLibrary() }
                .font(.subheadline).bold()
            Button {
                state.openAbout()
            } label: {
                Image(systemName: "info.circle").font(.title3)
            }
            .accessibilityLabel("关于与免责")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private var searchAndFilters: some View {
        VStack(spacing: 10) {
            if let message = state.error {
                HStack(alignment: .top, spacing: 8) {
                    Text(message)
                        .font(.footnote)
                        .foregroundColor(Theme.onErrorContainer)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Button {
                        state.dismissError()
                    } label: {
                        Image(systemName: "xmark").font(.footnote)
                            .foregroundColor(Theme.onErrorContainer)
                    }
                }
                .padding(10)
                .background(Theme.errorContainer)
                .cornerRadius(12)
            }

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundColor(Theme.onSurfaceVariant)
                TextField("搜索书名 / 学科 / 年级", text: $state.query)
                    .textInputAutocapitalization(.never)
                    .disableAutocorrection(true)
                if state.catalogLoading {
                    ProgressView().scaleEffect(0.8)
                } else {
                    Button {
                        state.refreshCatalog()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .accessibilityLabel("刷新教材目录")
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.primary, lineWidth: 1))

            HStack(spacing: 10) {
                FilterMenu(title: "学段", value: state.stageFilter, options: state.stageOptions) {
                    state.setStage($0)
                }
                FilterMenu(title: "学科", value: state.subjectFilter, options: state.subjectOptions) {
                    state.setSubject($0)
                }
                FilterMenu(title: "版本", value: state.versionFilter, options: state.versionOptions) {
                    state.versionFilter = $0
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 10)
    }

    @ViewBuilder
    private var content: some View {
        if state.catalogLoading && state.textbooks.isEmpty {
            Spacer()
            ProgressView()
            Spacer()
        } else if state.textbooks.isEmpty {
            Spacer()
            VStack(spacing: 8) {
                Text(state.catalogError ?? "没有找到教材")
                    .multilineTextAlignment(.center)
                    .foregroundColor(.red)
                Button("重试") { state.refreshCatalog() }
            }
            .padding(.horizontal, 24)
            Spacer()
        } else if state.filteredBooks.isEmpty {
            Spacer()
            Text("没有符合筛选条件的教材").foregroundColor(Theme.onSurfaceVariant)
            Spacer()
        } else {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(state.filteredBooks) { book in
                        BookRow(
                            book: book,
                            selected: state.selectedIds.contains(book.id),
                            onToggle: { state.toggleSelect(book.id) }
                        )
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
        }
    }

    private var bottomBar: some View {
        let count = state.selectedIds.count
        return VStack(spacing: 0) {
            Divider()
            Button {
                state.startDownload()
            } label: {
                Text(count > 0 ? "去下载（\(count)）" : "请先勾选教材")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(count > 0 ? Theme.primary : Color(.systemGray4))
                    .foregroundColor(count > 0 ? .white : Theme.onSurfaceVariant)
                    .cornerRadius(16)
            }
            .disabled(count == 0)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .background(Color(.systemBackground))
    }
}

private struct BookRow: View {
    let book: Textbook
    let selected: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(alignment: .center, spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(colorForSubject(book.subject))
                        .frame(width: 58, height: 80)
                    Text(book.subject.isEmpty ? "书" : String(book.subject.prefix(1)))
                        .foregroundColor(.white).bold()
                    if let thumb = book.thumb, let url = URL(string: thumb) {
                        AsyncImage(url: url) { image in
                            image.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Color.clear
                        }
                        .frame(width: 58, height: 80)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(book.title)
                        .font(.subheadline).bold()
                        .foregroundColor(.primary)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    if !book.meta.isEmpty {
                        Text(book.meta)
                            .font(.caption)
                            .foregroundColor(Theme.onSurfaceVariant)
                            .lineLimit(2)
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: selected ? "checkmark.square.fill" : "square")
                    .font(.title3)
                    .foregroundColor(selected ? Theme.primary : Theme.onSurfaceVariant)
            }
            .padding(12)
            .background(Color(.systemBackground))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.outline.opacity(0.15)))
            .cornerRadius(16)
        }
        .buttonStyle(.plain)
    }

    private func colorForSubject(_ subject: String) -> Color {
        let palette: [Color] = [
            Color(red: 0.404, green: 0.314, blue: 0.643),
            Color(red: 0.000, green: 0.412, blue: 0.427),
            Color(red: 0.490, green: 0.322, blue: 0.376),
            Color(red: 0.545, green: 0.314, blue: 0.000),
            Color(red: 0.220, green: 0.416, blue: 0.125),
        ]
        let index = abs(subject.hashValue) % palette.count
        return palette[index]
    }
}

private struct FilterMenu: View {
    let title: String
    let value: String
    let options: [String]
    let onSelect: (String) -> Void

    var body: some View {
        Menu {
            Button("全部") { onSelect("") }
            ForEach(options, id: \.self) { option in
                Button(option) { onSelect(option) }
            }
        } label: {
            HStack(spacing: 4) {
                Text(value.isEmpty ? title : value)
                    .font(.subheadline)
                    .lineLimit(1)
                Image(systemName: "chevron.down").font(.caption2)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(value.isEmpty ? Theme.surfaceVariant : Theme.primaryContainer)
            .foregroundColor(.primary)
            .cornerRadius(12)
        }
    }
}
