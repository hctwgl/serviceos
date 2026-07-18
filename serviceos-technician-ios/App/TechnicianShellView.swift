import ServiceOSCoreClient
import SwiftUI
import TechnicianIOSFoundation

struct TechnicianShellView: View {
    let store: TechnicianAppStore
    let session: TechnicianSession

    private var visibleTabs: [TechnicianAppTab] {
        TechnicianAppTab.visibleTabs(for: session)
    }

    var body: some View {
        TabView(selection: Binding(
            get: { store.selectedTab },
            set: { store.selectedTab = $0 }
        )) {
            ForEach(visibleTabs) { tab in
                NavigationStack {
                    TechnicianNativePage(tab: tab, session: session, store: store)
                        .navigationTitle(tab.title(in: session))
                }
                .tabItem { Label(tab.title(in: session), systemImage: tab.systemImage) }
                .tag(tab)
                .accessibilityIdentifier("technician.tab.\(tab.rawValue)")
            }
        }
        .accessibilityIdentifier("technician.shell")
    }
}

private struct TechnicianNativePage: View {
    let tab: TechnicianAppTab
    let session: TechnicianSession
    let store: TechnicianAppStore

    var body: some View {
        List {
            Section("当前上下文") {
                LabeledContent("范围", value: session.activeContext.scopeRef)
                LabeledContent("类型", value: session.activeContext.scopeType.rawValue)
                LabeledContent("版本", value: session.activeContext.version)
                if session.contexts.count > 1 {
                    Picker("切换师傅上下文", selection: Binding(
                        get: { session.activeContext.contextId },
                        set: { contextID in Task { await store.switchContext(to: contextID) } }
                    )) {
                        ForEach(session.contexts, id: \.contextId) { context in
                            Text(context.scopeRef).tag(context.contextId)
                        }
                    }
                    .accessibilityIdentifier("technician.context-picker")
                }
            }

            switch tab {
            case .me:
                Section("授权信息") {
                    LabeledContent("Capability", value: "\(session.capabilities.capabilityCodes.count) 项")
                    LabeledContent("导航目录", value: session.navigation.navigationCatalogVersion)
                    LabeledContent("服务时间", value: session.navigation.asOf.formatted())
                }
                Section {
                    Button("退出登录", role: .destructive) { Task { await store.signOut() } }
                        .accessibilityIdentifier("technician.logout")
                }
            case .taskFeed, .schedule, .sync:
                Section("原生页面边界") {
                    Label("原生 SwiftUI 页壳已就绪", systemImage: "checkmark.seal")
                    Text("业务数据与写命令将在 Track E 按服务端契约 → H5 → iOS → 真机顺序接入；本页不会伪造完成或离线状态。")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .listStyle(.insetGrouped)
        .accessibilityIdentifier("technician.page.\(tab.rawValue)")
    }
}
