import PhotosUI
import ServiceOSCoreClient
import SwiftUI
import TechnicianIOSFoundation
import UIKit
import UniformTypeIdentifiers

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

                TechnicianOnlineFormSection(
                    store: store,
                    session: session,
                    taskID: taskID,
                    taskStatus: detail.taskStatus,
                    executionGuarded: detail.executionGuarded
                )

                TechnicianOnlineEvidenceSection(
                    store: store,
                    session: session,
                    taskID: taskID,
                    taskStatus: detail.taskStatus,
                    executionGuarded: detail.executionGuarded
                )

                Section("冻结资料并完成任务") {
                    Text("只选择 ACTIVE Item 的最新 VALIDATED Revision；引用、摘要与输入版本由服务器重新读取并冻结。")
                        .font(.footnote).foregroundStyle(.secondary)
                    Button {
                        Task { await store.completeTask(session: session, taskID: taskID) }
                    } label: {
                        Label(store.taskSubmitting ? "服务器复核中…" : "冻结资料并完成任务",
                              systemImage: "checkmark.seal")
                    }
                    .disabled(store.taskSubmitting || store.evidenceUploading
                        || detail.executionGuarded || detail.taskStatus != "RUNNING")
                    .accessibilityIdentifier("technician.task.complete")
                    if let message = store.taskSubmissionMessage {
                        Text(message).font(.callout).foregroundStyle(.secondary)
                            .accessibilityIdentifier("technician.task.complete.message")
                    }
                }
                .accessibilityIdentifier("technician.task.submission")

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
                        Text("签退必须引用已完成现场操作；表单与 Evidence 上传已接入，但作业引用尚未形成前不生成占位 operationRefs。")
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

private struct TechnicianOnlineEvidenceSection: View {
    let store: TechnicianAppStore
    let session: TechnicianSession
    let taskID: UUID
    let taskStatus: String
    let executionGuarded: Bool

    var body: some View {
        Section("在线现场资料") {
            Text("文件只在本次前台操作中读取；不进入离线草稿或后台上传。完成 PUT 后仍须由服务器扫描与校验。")
                .font(.footnote).foregroundStyle(.secondary)
            if store.evidenceSlots.isEmpty {
                Text(store.evidenceMessage ?? "当前任务没有可上传的资料槽位")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(store.evidenceSlots) { slot in
                    TechnicianEvidenceSlotRow(
                        store: store,
                        session: session,
                        taskID: taskID,
                        slot: slot,
                        items: store.evidenceItems.filter { $0.evidenceSlotId == slot.slotId },
                        uploadDisabled: store.evidenceUploading || executionGuarded
                            || taskStatus != "RUNNING" || !slot.active
                    )
                }
            }
            if let message = store.evidenceMessage {
                Text(message).font(.callout).foregroundStyle(.secondary)
                    .accessibilityIdentifier("technician.evidence.message")
            }
        }
        .accessibilityIdentifier("technician.evidence.section")
    }
}

private struct TechnicianEvidenceSlotRow: View {
    let store: TechnicianAppStore
    let session: TechnicianSession
    let taskID: UUID
    let slot: TechnicianOnlineEvidenceSlot
    let items: [TechnicianOnlineEvidenceItem]
    let uploadDisabled: Bool
    @State private var galleryItem: PhotosPickerItem?
    @State private var importingFile = false
    @State private var showingCamera = false
    @State private var localMessage: String?

    private var acceptsPhoto: Bool { slot.mediaType == "PHOTO" }
    private var acceptsVideo: Bool { slot.mediaType == "VIDEO" }
    private var pickerFilter: PHPickerFilter { acceptsVideo ? .videos : .images }
    private var contentTypes: [UTType] {
        if acceptsPhoto { return [.image] }
        if acceptsVideo { return [.movie] }
        return [.pdf, .plainText, .data]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(slot.requirementName).font(.headline)
                if slot.required { Text("必填").font(.caption).foregroundStyle(.red) }
                Spacer()
                Text("\(items.count)/\(slot.maxCount.map(String.init) ?? "∞")")
                    .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
            }
            Text("\(slot.mediaType) · \(slot.status)")
                .font(.caption).foregroundStyle(.secondary)
            ForEach(items.sorted { $0.itemOrdinal < $1.itemOrdinal }) { item in
                let latest = item.revisions.max { $0.revisionNumber < $1.revisionNumber }
                Text("第 \(item.itemOrdinal) 项 · \(latest?.status ?? item.status)")
                    .font(.caption).foregroundStyle(.secondary)
            }
            HStack {
                if acceptsPhoto {
                    Button { showingCamera = true } label: {
                        Label("拍照", systemImage: "camera")
                    }
                    .disabled(uploadDisabled || !UIImagePickerController.isSourceTypeAvailable(.camera))
                }
                if acceptsPhoto || acceptsVideo {
                    PhotosPicker(selection: $galleryItem, matching: pickerFilter) {
                        Label("相册", systemImage: "photo.on.rectangle")
                    }
                    .disabled(uploadDisabled)
                } else {
                    Button { importingFile = true } label: {
                        Label("选择文件", systemImage: "doc")
                    }
                    .disabled(uploadDisabled)
                }
            }
            if let localMessage { Text(localMessage).font(.caption).foregroundStyle(.red) }
        }
        .task(id: galleryItem) {
            guard let galleryItem else { return }
            defer { self.galleryItem = nil }
            do {
                guard let data = try await galleryItem.loadTransferable(type: Data.self), !data.isEmpty else {
                    throw TechnicianEvidenceSelectionError.emptySelection
                }
                let mimeType = galleryItem.supportedContentTypes.first?.preferredMIMEType
                    ?? (acceptsVideo ? "video/quicktime" : "image/jpeg")
                await upload(data: data, fileName: acceptsVideo ? "现场视频.mov" : "现场照片.jpg",
                             mimeType: mimeType, source: .gallery)
            } catch {
                localMessage = "无法读取所选资料，请重新选择"
            }
        }
        .sheet(isPresented: $showingCamera) {
            TechnicianCameraPicker { image in
                showingCamera = false
                guard let data = image.jpegData(compressionQuality: 0.9) else {
                    localMessage = "无法读取拍摄照片"
                    return
                }
                Task { await upload(data: data, fileName: "现场照片.jpg", mimeType: "image/jpeg", source: .camera) }
            } onCancel: {
                showingCamera = false
            }
            .ignoresSafeArea()
        }
        .fileImporter(isPresented: $importingFile, allowedContentTypes: contentTypes) { result in
            do {
                let url = try result.get()
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                let data = try Data(contentsOf: url)
                let mimeType = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType
                    ?? "application/octet-stream"
                Task { await upload(data: data, fileName: url.lastPathComponent, mimeType: mimeType, source: .file) }
            } catch {
                localMessage = "无法读取所选文件，请重新选择"
            }
        }
        .accessibilityIdentifier("technician.evidence.slot.\(slot.slotId.uuidString.lowercased())")
    }

    @MainActor
    private func upload(
        data: Data,
        fileName: String,
        mimeType: String,
        source: TechnicianEvidenceUploadAsset.Source
    ) async {
        localMessage = nil
        await store.uploadEvidence(
            session: session,
            taskID: taskID,
            slot: slot,
            asset: .init(data: data, fileName: fileName, mimeType: mimeType, source: source, capturedAt: Date())
        )
    }
}

private enum TechnicianEvidenceSelectionError: Error { case emptySelection }

private struct TechnicianOnlineFormSection: View {
    let store: TechnicianAppStore
    let session: TechnicianSession
    let taskID: UUID
    let taskStatus: String
    let executionGuarded: Bool
    @State private var textValues: [String: String] = [:]
    @State private var booleanValues: [String: Bool] = [:]
    @State private var localMessage: String?

    private let supportedTypes = Set(["STRING", "TEXT", "INTEGER", "DECIMAL", "BOOLEAN", "DATE", "DATETIME"])

    private var form: TechnicianTaskForm? { store.taskForms.first }
    private var fields: [TechnicianFormField] {
        form?.definition.sections.flatMap(\.fields) ?? []
    }
    private var unsupportedReasons: [String] {
        guard let form else { return [] }
        var reasons = Set<String>()
        if form.definition.hasValidationRules { reasons.insert("跨字段规则尚无 Web/iOS 共用执行器") }
        for section in form.definition.sections {
            if section.hasVisibility { reasons.insert("分区条件显隐尚无 Web/iOS 共用执行器") }
            for field in section.fields {
                if !supportedTypes.contains(field.dataType) { reasons.insert("字段类型 \(field.dataType) 尚未接入") }
                if field.hasConditionalBehavior { reasons.insert("字段条件或默认值尚无 Web/iOS 共用执行器") }
                if field.hasOptionsOrValidators { reasons.insert("选项或扩展校验器尚未接入") }
            }
        }
        return reasons.sorted()
    }

    var body: some View {
        Section("在线填写冻结表单") {
            Text("输入只在当前页面内存中；草稿与 prefill 冲突策略未接受，不会伪装成已保存草稿。")
                .font(.footnote).foregroundStyle(.secondary)
            if let form {
                LabeledContent("表单", value: form.definition.title ?? form.formKey)
                LabeledContent("版本", value: form.semanticVersion)
                if !unsupportedReasons.isEmpty {
                    Label(unsupportedReasons.joined(separator: "；"), systemImage: "exclamationmark.shield")
                        .foregroundStyle(.red)
                        .accessibilityIdentifier("technician.form.unsupported")
                } else {
                    ForEach(form.definition.sections, id: \.sectionKey) { section in
                        Text(section.title).font(.headline)
                        ForEach(section.fields, id: \.fieldKey) { field in
                            if field.dataType == "BOOLEAN" {
                                Toggle(isOn: booleanBinding(field.fieldKey)) {
                                    Text(field.label + (field.required ? " *" : ""))
                                }
                                .accessibilityIdentifier("technician.form.field.\(field.fieldKey)")
                            } else {
                                TextField(
                                    field.label + (field.required ? " *" : ""),
                                    text: textBinding(field.fieldKey),
                                    axis: field.dataType == "TEXT" ? .vertical : .horizontal
                                )
                                .lineLimit(field.dataType == "TEXT" ? 2...6 : 1...1)
                                .keyboardType(field.dataType == "INTEGER" || field.dataType == "DECIMAL"
                                    ? .numbersAndPunctuation : .default)
                                .accessibilityIdentifier("technician.form.field.\(field.fieldKey)")
                            }
                        }
                    }
                    Button("提交不可变表单") {
                        guard let values = submissionValues() else { return }
                        Task {
                            await store.submitForm(
                                session: session,
                                taskID: taskID,
                                formVersionID: form.formVersionId,
                                values: values
                            )
                        }
                    }
                    .disabled(store.onlineLoading || executionGuarded || taskStatus != "RUNNING")
                    .accessibilityIdentifier("technician.form.submit")
                }
            } else {
                Text(store.formMessage ?? "当前任务未锁定可填写表单")
                    .foregroundStyle(.secondary)
            }
            if let message = localMessage ?? store.formMessage {
                Text(message).font(.callout).foregroundStyle(.secondary)
                    .accessibilityIdentifier("technician.form.message")
            }
            ForEach(Array(store.formIssues.enumerated()), id: \.offset) { _, issue in
                Text("\(issue.fieldKey)：\(issue.message)").foregroundStyle(.red)
            }
        }
        .task(id: form?.formVersionId) {
            textValues = [:]
            booleanValues = Dictionary(uniqueKeysWithValues:
                fields.filter { $0.dataType == "BOOLEAN" }.map { ($0.fieldKey, false) })
            localMessage = nil
        }
    }

    private func textBinding(_ key: String) -> Binding<String> {
        Binding(get: { textValues[key, default: ""] }, set: { textValues[key] = $0 })
    }

    private func booleanBinding(_ key: String) -> Binding<Bool> {
        Binding(get: { booleanValues[key, default: false] }, set: { booleanValues[key] = $0 })
    }

    private func submissionValues() -> [String: TechnicianFormValue]? {
        var result: [String: TechnicianFormValue] = [:]
        var missing: [String] = []
        for field in fields {
            if field.dataType == "BOOLEAN" {
                result[field.fieldKey] = .boolean(booleanValues[field.fieldKey, default: false])
                continue
            }
            let text = textValues[field.fieldKey, default: ""].trimmingCharacters(in: .whitespacesAndNewlines)
            if text.isEmpty {
                if field.required { missing.append(field.label) }
                continue
            }
            switch field.dataType {
            case "INTEGER":
                guard let value = Int(text) else { localMessage = "\(field.label) 必须是整数"; return nil }
                result[field.fieldKey] = .integer(value)
            case "DECIMAL":
                guard let value = Double(text) else { localMessage = "\(field.label) 必须是数字"; return nil }
                result[field.fieldKey] = .decimal(value)
            default:
                result[field.fieldKey] = .string(text)
            }
        }
        if !missing.isEmpty {
            localMessage = "请填写必填项：\(missing.joined(separator: "、"))"
            return nil
        }
        localMessage = nil
        return result
    }
}
