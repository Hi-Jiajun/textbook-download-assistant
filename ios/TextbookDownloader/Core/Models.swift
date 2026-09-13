import Foundation

/// 教材目录里的一本书（字段与 Android 版 Textbook 一致）。
struct Textbook: Identifiable, Hashable {
    let id: String
    let title: String
    let stage: String
    let subject: String
    let version: String
    let grade: String
    let volume: String
    let thumb: String?

    var meta: String {
        [stage, subject, version, grade, volume]
            .filter { !$0.isEmpty }
            .reduce(into: [String]()) { acc, v in if !acc.contains(v) { acc.append(v) } }
            .joined(separator: " · ")
    }
}

/// 章节目录（递归）。pageIndex 来自平台 mapping，1 起。
struct Chapter {
    let title: String
    let pageIndex: Int?
    let children: [Chapter]
}

/// 解析结果：一个可下载的资源。
struct ResourceInfo {
    let title: String
    let url: String
    let format: String
    let edition: String?
    let chapters: [Chapter]
}

/// 教材库索引里的一条记录。
struct SavedItem: Codable, Identifiable, Hashable {
    let title: String
    let path: String
    let format: String
    let edition: String?
    let addedAt: Int64

    /// 同一本书重复下载会落到同一路径，用路径 + 时间做唯一键。
    var id: String { "\(path):\(addedAt)" }
}

/// 下载页用到的状态。
enum FlowStep {
    case browse
    case login
    case resolve
    case download
    case done
    case library
    case about
}
