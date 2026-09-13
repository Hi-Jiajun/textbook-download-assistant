import Foundation
import Security

/// 登录凭据的本地持久化：存进 Keychain（与 Android 版用 Keystore 加密 DataStore 对应）。
struct TokenStore {
    private let service = "com.jiaocai.textbook"
    private let account = "credentials"

    func save(_ token: String) {
        let data = Data(token.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        var attrs = query
        attrs[kSecValueData as String] = data
        attrs[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(attrs as CFDictionary, nil)
    }

    func load() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

/// 「我的教材库」本地索引，存于 Documents/library.json。
struct LibraryStore {
    private var fileURL: URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return dir.appendingPathComponent("library.json")
    }

    func load() -> [SavedItem] {
        guard let data = try? Data(contentsOf: fileURL) else { return [] }
        return (try? JSONDecoder().decode([SavedItem].self, from: data)) ?? []
    }

    /// 同一本书重复下载会落到相同路径，覆盖旧记录，避免列表出现重复行。
    @discardableResult
    func append(_ item: SavedItem) -> [SavedItem] {
        var list = load().filter { $0.path != item.path }
        list.append(item)
        save(list)
        return list
    }

    /// 删除本地文件并从索引移除；文件存在但删除失败时返回 false 且保留索引。
    func remove(path: String) -> Bool {
        let target = URL(fileURLWithPath: path)
        if FileManager.default.fileExists(atPath: path) {
            do {
                try FileManager.default.removeItem(at: target)
            } catch {
                return false
            }
        }
        save(load().filter { $0.path != path })
        return true
    }

    private func save(_ list: [SavedItem]) {
        guard let data = try? JSONEncoder().encode(list) else { return }
        let tmp = fileURL.deletingLastPathComponent().appendingPathComponent("library.json.tmp")
        do {
            try data.write(to: tmp, options: .atomic)
            _ = try? FileManager.default.replaceItemAt(fileURL, withItemAt: tmp)
            if !FileManager.default.fileExists(atPath: fileURL.path) {
                try data.write(to: fileURL, options: .atomic)
            }
        } catch {
            try? data.write(to: fileURL, options: .atomic)
        }
    }
}

/// 应用级本地偏好：目前只存「首次启动的使用声明是否已确认」。
struct AppPrefs {
    private let key = "usage_notice_accepted"

    var hasAcceptedNotice: Bool {
        get { UserDefaults.standard.bool(forKey: key) }
        nonmutating set { UserDefaults.standard.set(newValue, forKey: key) }
    }
}
