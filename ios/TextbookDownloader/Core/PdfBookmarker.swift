import Foundation
import PDFKit

/// 为下载好的 PDF 写入章节书签。
///
/// 规则（与 Android 版 PdfBookmarker 一致）：
/// - pageIndex 来自平台 mapping，1 起，写入时减 1 得到 0 起的页码。
/// - 页码越界或缺失的书签跳过（不阻断其余书签）。
/// - 任一出错返回 false，不影响已下载的 PDF 文件本身。
enum PdfBookmarker {

    static func addBookmarks(fileURL: URL, chapters: [Chapter]) -> Bool {
        guard !chapters.isEmpty else { return false }
        // 先写到临时文件再原子替换，避免读写同一个文件把课本写坏。
        let scratch = fileURL.deletingLastPathComponent()
            .appendingPathComponent(fileURL.lastPathComponent + ".bookmark.tmp")
        defer { try? FileManager.default.removeItem(at: scratch) }

        guard let doc = PDFDocument(url: fileURL) else { return false }
        let root = PDFOutline()

        func add(parent: PDFOutline, items: [Chapter]) {
            for chapter in items {
                let node = PDFOutline()
                node.label = chapter.title
                if let pageIndex = chapter.pageIndex {
                    let zeroBased = pageIndex - 1
                    if zeroBased >= 0, zeroBased < doc.pageCount, let page = doc.page(at: zeroBased) {
                        let top = CGPoint(x: 0, y: page.bounds(for: .mediaBox).height)
                        node.destination = PDFDestination(page: page, at: top)
                    }
                }
                parent.insertChild(node, at: parent.numberOfChildren)
                if !chapter.children.isEmpty {
                    add(parent: node, items: chapter.children)
                }
            }
        }

        add(parent: root, items: chapters)
        doc.outlineRoot = root

        guard doc.write(to: scratch) else { return false }
        do {
            _ = try FileManager.default.replaceItemAt(fileURL, withItemAt: scratch)
            return true
        } catch {
            // 替换失败时退化为覆盖写（此时 PDFDocument 已经关闭源文件句柄）。
            do {
                try FileManager.default.removeItem(at: fileURL)
                try FileManager.default.moveItem(at: scratch, to: fileURL)
                return true
            } catch {
                return false
            }
        }
    }
}
