import SwiftUI
import TechnicianIOSFoundation

struct TechnicianRootView: View {
    let store: TechnicianAppStore

    var body: some View {
        Group {
            switch store.phase {
            case .launching:
                ProgressView("正在恢复安全会话…")
                    .accessibilityIdentifier("technician.launching")
            case .signedOut:
                TechnicianSignedOutView(store: store)
            case .authenticating:
                ProgressView("正在打开企业登录…")
                    .accessibilityIdentifier("technician.authenticating")
            case .ready(let session):
                TechnicianShellView(store: store, session: session)
            case .failed(let message):
                TechnicianFailureView(store: store, message: message)
            }
        }
        .tint(Color(serviceOSHex: GeneratedContractBoundary.primaryActionColor))
    }
}

private struct TechnicianSignedOutView: View {
    let store: TechnicianAppStore

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "wrench.and.screwdriver.fill")
                .font(.system(size: 52))
                .accessibilityHidden(true)
            VStack(spacing: 8) {
                Text("ServiceOS 师傅端")
                    .font(.largeTitle.bold())
                Text("使用企业账号登录后，只加载服务端授予的师傅上下文和导航。")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            Button("企业账号登录") {
                Task { await store.signIn() }
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .accessibilityIdentifier("technician.login")
            if let environment = store.configuration?.environment.rawValue {
                Text("环境：\(environment)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .accessibilityIdentifier("technician.environment")
            }
        }
        .padding(32)
    }
}

private struct TechnicianFailureView: View {
    let store: TechnicianAppStore
    let message: String

    var body: some View {
        ContentUnavailableView {
            Label("暂时无法继续", systemImage: "exclamationmark.triangle")
        } description: {
            Text(message)
        } actions: {
            Button("重试") { Task { await store.retry() } }
                .accessibilityIdentifier("technician.retry")
            Button("清除会话并退出") { Task { await store.signOut() } }
                .accessibilityIdentifier("technician.logout-after-error")
        }
    }
}

private extension Color {
    init(serviceOSHex rawValue: String) {
        let value = rawValue.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        let rgb = UInt64(value, radix: 16) ?? 0x243B53
        self.init(
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255
        )
    }
}
