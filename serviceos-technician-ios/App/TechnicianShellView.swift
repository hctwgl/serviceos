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
            case .taskFeed:
                TechnicianTaskFeedSection(store: store, session: session)
            case .schedule, .sync:
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
        .task {
            if tab == .taskFeed { await store.loadTaskFeed(session: session) }
        }
    }
}

private struct TechnicianTaskFeedSection: View {
    let store: TechnicianAppStore
    let session: TechnicianSession

    var body: some View {
        Section("当前责任任务") {
            if store.onlineLoading, store.taskFeed == nil {
                ProgressView("正在加载任务…")
            } else if let items = store.taskFeed?.items.filter({ $0.itemType == .assignment }), !items.isEmpty {
                ForEach(items, id: \.taskId) { item in
                    NavigationLink {
                        TechnicianTaskDetailView(store: store, session: session, taskID: item.taskId)
                    } label: {
                        VStack(alignment: .leading, spacing: 5) {
                            Text(item.taskType ?? "现场任务").font(.headline)
                            Text("\(item.stageCode ?? "—") · \(item.taskStatus ?? "—")")
                                .font(.subheadline).foregroundStyle(.secondary)
                            Text(item.taskId.uuidString.lowercased())
                                .font(.caption2.monospaced()).foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityIdentifier("technician.feed.task.\(item.taskId.uuidString.lowercased())")
                }
            } else {
                ContentUnavailableView("暂无当前任务", systemImage: "checkmark.circle")
            }
            Button("刷新任务") { Task { await store.loadTaskFeed(session: session) } }
                .disabled(store.onlineLoading)
                .accessibilityIdentifier("technician.feed.refresh")
        }
        if let message = store.onlineMessage {
            Section { Text(message).foregroundStyle(.secondary) }
        }
    }
}

private struct TechnicianTaskDetailView: View {
    let store: TechnicianAppStore
    let session: TechnicianSession
    let taskID: UUID
    @State private var exceptionCode = "SITE_UNSAFE"
    @State private var note = ""
    @State private var confirmingInterrupt = false

    private var detail: TechnicianPortalTaskDetail? {
        store.taskDetail?.taskId == taskID ? store.taskDetail : nil
    }

    private var confirmedAppointment: TechnicianPortalScheduleItem? {
        detail?.appointments.first { $0.status == "CONFIRMED" }
    }

    private var activeVisit: TechnicianPortalVisitItem? {
        detail?.visits.first { $0.status == .inProgress }
    }

    var body: some View {
        List {
            if let detail {
                Section("任务") {
                    LabeledContent("类型", value: detail.taskType)
                    LabeledContent("阶段", value: detail.stageCode)
                    LabeledContent("状态", value: detail.taskStatus)
                    LabeledContent("资源版本", value: "\(detail.resourceVersion)")
                }

                Section("预约与到场") {
                    if let appointment = confirmedAppointment {
                        LabeledContent("预约", value: appointment.type)
                        if let start = appointment.windowStart {
                            LabeledContent("窗口开始", value: start.formatted())
                        }
                    } else {
                        Text("没有可签到的已确认预约").foregroundStyle(.secondary)
                    }
                    if let visit = activeVisit {
                        LabeledContent("本次上门", value: "进行中 · v\(visit.aggregateVersion)")
                        LabeledContent("位置策略", value: visit.policyDecision.rawValue)
                    }
                }

                Section("在线现场操作") {
                    if let appointment = confirmedAppointment, activeVisit == nil {
                        Button {
                            Task {
                                await store.checkIn(
                                    session: session,
                                    appointmentID: appointment.appointmentId,
                                    taskID: taskID
                                )
                            }
                        } label: {
                            Label("主动定位并签到", systemImage: "location.fill")
                        }
                        .disabled(store.onlineLoading || detail.executionGuarded)
                        .accessibilityIdentifier("technician.visit.check-in")
                    }
                    if let visit = activeVisit {
                        Picker("无法施工原因", selection: $exceptionCode) {
                            Text("现场不安全").tag("SITE_UNSAFE")
                            Text("物料缺失").tag("MATERIAL_MISSING")
                        }
                        TextField("说明（可选）", text: $note, axis: .vertical)
                            .lineLimit(1...4)
                        Button("确认无法施工", role: .destructive) { confirmingInterrupt = true }
                            .disabled(store.onlineLoading)
                            .accessibilityIdentifier("technician.visit.interrupt")
                        Text("签退必须引用已完成现场操作；动态表单/资料尚未接入前，不生成占位 operationRefs。")
                            .font(.footnote).foregroundStyle(.secondary)
                            .accessibilityIdentifier("technician.visit.checkout-boundary")
                    }
                    if let message = store.onlineMessage {
                        Text(message).font(.callout).foregroundStyle(.secondary)
                            .accessibilityIdentifier("technician.visit.message")
                    }
                }

                Section("安全边界") {
                    Text("位置只在主动签到时采集一次；不持续定位、不后台定位。服务器仍实时复核当前责任和网点上下文。")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            } else if store.onlineLoading {
                ProgressView("正在加载任务详情…")
            } else {
                ContentUnavailableView("任务详情不可用", systemImage: "exclamationmark.triangle")
            }
        }
        .navigationTitle("任务详情")
        .task { await store.loadTaskDetail(session: session, taskID: taskID) }
        .refreshable { await store.loadTaskDetail(session: session, taskID: taskID) }
        .confirmationDialog("确认记录无法施工？", isPresented: $confirmingInterrupt, titleVisibility: .visible) {
            if let visit = activeVisit {
                Button("确认并中断本次上门", role: .destructive) {
                    Task {
                        await store.interrupt(
                            session: session,
                            visitID: visit.visitId,
                            aggregateVersion: visit.aggregateVersion,
                            taskID: taskID,
                            exceptionCode: exceptionCode,
                            note: note
                        )
                    }
                }
            }
            Button("取消", role: .cancel) { }
        }
        .accessibilityIdentifier("technician.task.detail")
    }
}
