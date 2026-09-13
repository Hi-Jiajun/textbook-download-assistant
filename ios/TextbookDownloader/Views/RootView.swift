import SwiftUI

struct RootView: View {
    @StateObject private var state = AppState()

    var body: some View {
        ZStack {
            switch state.step {
            case .browse:  BrowseView(state: state)
            case .login:   LoginView(state: state)
            case .resolve: ResolveView(state: state)
            case .download: DownloadView(state: state)
            case .done:    DoneView(state: state)
            case .library: LibraryView(state: state)
            case .about:   AboutView(state: state)
            }

            // 首次启动必须主动确认使用声明后才能使用。
            if state.needAgreement {
                UsageNoticeView(onAccept: state.acceptNotice)
            }
        }
        .tint(Theme.primary)
    }
}

enum Theme {
    static let primary = Color(red: 0.404, green: 0.314, blue: 0.643)      // #6750A4
    static let primaryContainer = Color(red: 0.910, green: 0.867, blue: 0.961)
    static let surfaceVariant = Color(red: 0.906, green: 0.867, blue: 0.953)
    static let onSurfaceVariant = Color(red: 0.286, green: 0.271, blue: 0.310)
    static let errorContainer = Color(red: 0.976, green: 0.871, blue: 0.863)
    static let onErrorContainer = Color(red: 0.255, green: 0.055, blue: 0.043)
    static let outline = Color(red: 0.478, green: 0.459, blue: 0.498)
}
