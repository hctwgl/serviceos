import Foundation
import Observation
import ServiceOSCoreClient
import ServiceOSIOSCore
import UIKit
// Hosted XCTest 复用 App 已链接的 Foundation，避免静态 Swift Package 在宿主与测试 bundle 中重复装载。
@_exported import TechnicianIOSFoundation

enum TechnicianAppPhase {
    case launching
    case signedOut
    case authenticating
    case ready(TechnicianSession)
    case failed(message: String)
}

enum TechnicianAppNavigationError: Error {
    case noSupportedPage
}

struct TechnicianAppDependencies {
    let configuration: TechnicianIOSConfiguration
    let authorizationFactory: OIDCAuthorizationRequestFactory
    let authorizer: any OIDCAuthorizing
    let tokenLifecycle: OIDCTokenLifecycle
    let tokenVault: any OIDCTokenPersisting
    let sessionLoader: TechnicianSessionLoader
    let onlineService: TechnicianOnlineService
    let locationProvider: OneShotLocationProvider

    @MainActor
    static func live(bundle: Bundle = .main) throws -> TechnicianAppDependencies {
        let values = bundle.infoDictionary?.reduce(into: [String: String]()) { result, pair in
            if let value = pair.value as? String { result[pair.key] = value }
        } ?? [:]
        let configuration = try TechnicianIOSConfiguration.resolve(values)
        let vault = KeychainAccessTokenVault(
            service: bundle.bundleIdentifier ?? "com.serviceos.technician",
            account: "oidc-token-set"
        )
        let requestBuilder = ServiceRequestBuilder(
            baseURL: configuration.apiBaseURL,
            tokenProvider: vault,
            clientMetadata: configuration.clientMetadata
        )
        return TechnicianAppDependencies(
            configuration: configuration,
            authorizationFactory: OIDCAuthorizationRequestFactory(configuration: configuration),
            authorizer: SystemOIDCAuthorizer(),
            tokenLifecycle: OIDCTokenLifecycle(
                requestFactory: OIDCTokenEndpointRequestFactory(configuration: configuration),
                transport: URLSessionOIDCTokenTransport(),
                vault: vault
            ),
            tokenVault: vault,
            sessionLoader: TechnicianSessionLoader(
                requestBuilder: requestBuilder,
                transport: URLSessionServiceTransport()
            ),
            onlineService: TechnicianOnlineService(
                requestBuilder: requestBuilder,
                transport: URLSessionServiceTransport()
            ),
            locationProvider: OneShotLocationProvider()
        )
    }
}

@MainActor
@Observable
final class TechnicianAppStore {
    private(set) var phase: TechnicianAppPhase = .launching
    private(set) var configuration: TechnicianIOSConfiguration?
    private(set) var taskFeed: TechnicianPortalFeedPage?
    private(set) var taskDetail: TechnicianPortalTaskDetail?
    private(set) var taskForms: [TechnicianTaskForm] = []
    private(set) var formMessage: String?
    private(set) var formIssues: [TechnicianIOSFoundation.TechnicianFormValidationIssue] = []
    private(set) var evidenceSlots: [TechnicianOnlineEvidenceSlot] = []
    private(set) var evidenceItems: [TechnicianOnlineEvidenceItem] = []
    private(set) var evidenceMessage: String?
    private(set) var evidenceUploading = false
    private(set) var onlineMessage: String?
    private(set) var onlineLoading = false
    var selectedTab: TechnicianAppTab = .taskFeed
    private var dependencies: TechnicianAppDependencies?
    private var hasBootstrapped = false

    func bootstrapIfNeeded() async {
        guard !hasBootstrapped else { return }
        hasBootstrapped = true
        do {
            let dependencies = try TechnicianAppDependencies.live()
            self.dependencies = dependencies
            configuration = dependencies.configuration
            if ProcessInfo.processInfo.environment["SERVICEOS_UI_TEST_RESET_SESSION"] == "1" {
                await dependencies.tokenVault.clear()
            }
            await restoreSession(using: dependencies)
        } catch {
            phase = .failed(message: Self.safeMessage(for: error))
        }
    }

    func signIn() async {
        guard let dependencies else {
            phase = .failed(message: "应用配置不可用，请联系管理员")
            return
        }
        phase = .authenticating
        do {
            let (authorizationURL, transaction) = try dependencies.authorizationFactory.begin()
            let callbackURL = try await dependencies.authorizer.authorize(
                url: authorizationURL,
                callbackScheme: dependencies.configuration.redirectScheme
            )
            let code = try dependencies.authorizationFactory.validate(
                callbackURL: callbackURL,
                transaction: transaction
            )
            try await dependencies.tokenLifecycle.exchange(code: code, transaction: transaction)
            try await loadSession(using: dependencies)
        } catch is CancellationError {
            phase = .signedOut
        } catch {
            phase = .failed(message: Self.safeMessage(for: error))
        }
    }

    func retry() async {
        guard let dependencies else {
            hasBootstrapped = false
            phase = .launching
            await bootstrapIfNeeded()
            return
        }
        await restoreSession(using: dependencies)
    }

    func switchContext(to contextID: String) async {
        guard let dependencies else { return }
        phase = .launching
        do {
            try await loadSession(using: dependencies, preferredContextID: contextID)
        } catch {
            phase = .failed(message: Self.safeMessage(for: error))
        }
    }

    func signOut() async {
        guard let dependencies else {
            phase = .signedOut
            return
        }
        await dependencies.tokenLifecycle.logout()
        selectedTab = .taskFeed
        clearOnlineState()
        phase = .signedOut
    }

    func loadTaskFeed(session: TechnicianSession) async {
        guard let dependencies else { return }
        onlineLoading = true
        defer { onlineLoading = false }
        do {
            taskFeed = try await dependencies.onlineService.taskFeed(
                contextID: session.activeContext.contextId
            )
            onlineMessage = nil
        } catch {
            taskFeed = nil
            onlineMessage = Self.safeMessage(for: error)
        }
    }

    func loadTaskDetail(session: TechnicianSession, taskID: UUID) async {
        guard let dependencies else { return }
        onlineLoading = true
        defer { onlineLoading = false }
        do {
            taskDetail = try await dependencies.onlineService.taskDetail(
                contextID: session.activeContext.contextId,
                taskID: taskID
            )
            do {
                taskForms = try await dependencies.onlineService.taskForms(
                    contextID: session.activeContext.contextId,
                    taskID: taskID
                )
                formMessage = nil
                formIssues = []
            } catch {
                // 表单次级资源失败不能抹掉已获权的任务详情；仍以安全文案明确不可填写。
                taskForms = []
                formMessage = Self.safeMessage(for: error)
            }
            await loadEvidence(using: dependencies, session: session, taskID: taskID)
            onlineMessage = nil
        } catch {
            taskDetail = nil
            taskForms = []
            evidenceSlots = []
            evidenceItems = []
            onlineMessage = Self.safeMessage(for: error)
        }
    }

    /// Evidence 是任务详情的次级资源；加载失败只关闭上传区域，不得抹掉任务与表单事实。
    private func loadEvidence(
        using dependencies: TechnicianAppDependencies,
        session: TechnicianSession,
        taskID: UUID
    ) async {
        do {
            let onlineService = dependencies.onlineService
            let contextID = session.activeContext.contextId
            async let slots = onlineService.evidenceSlots(
                contextID: contextID,
                taskID: taskID
            )
            async let items = onlineService.evidenceItems(
                contextID: contextID,
                taskID: taskID
            )
            (evidenceSlots, evidenceItems) = try await (slots, items)
            evidenceMessage = nil
        } catch {
            evidenceSlots = []
            evidenceItems = []
            evidenceMessage = Self.safeMessage(for: error)
        }
    }

    func uploadEvidence(
        session: TechnicianSession,
        taskID: UUID,
        slot: TechnicianOnlineEvidenceSlot,
        asset: TechnicianEvidenceUploadAsset
    ) async {
        guard let dependencies else { return }
        evidenceUploading = true
        evidenceMessage = "正在校验并上传现场资料…"
        defer { evidenceUploading = false }
        do {
            let currentItems = evidenceItems
                .filter { $0.evidenceSlotId == slot.slotId }
                .sorted { $0.itemOrdinal < $1.itemOrdinal }
            // 槽位达到 maxCount 后追加最后一项的新 revision；未达到时创建新 EvidenceItem。
            let targetItem = slot.maxCount.map { currentItems.count >= $0 ? currentItems.last : nil } ?? nil
            let item = try await dependencies.onlineService.uploadEvidence(
                contextID: session.activeContext.contextId,
                taskID: taskID,
                slotID: slot.slotId,
                evidenceItemID: targetItem?.evidenceItemId,
                asset: asset
            )
            evidenceMessage = "资料已提交，当前状态：\(item.status)；扫描与校验完成前不视为可用证据"
            await loadEvidence(using: dependencies, session: session, taskID: taskID)
        } catch {
            evidenceMessage = Self.safeMessage(for: error)
        }
    }

    func checkIn(session: TechnicianSession, appointmentID: UUID, taskID: UUID) async {
        guard let dependencies else { return }
        onlineLoading = true
        onlineMessage = "正在获取一次定位…"
        defer { onlineLoading = false }
        do {
            let location = try await dependencies.locationProvider.capture()
            let commandID = UUID().uuidString.lowercased()
            let deviceID = UIDevice.current.identifierForVendor?.uuidString.lowercased()
                ?? "ios-installation-\(Bundle.main.bundleIdentifier ?? "technician")"
            let receipt = try await dependencies.onlineService.checkIn(
                contextID: session.activeContext.contextId,
                appointmentID: appointmentID,
                deviceID: deviceID,
                deviceCommandID: commandID,
                location: location
            )
            onlineMessage = receipt.policyDecision == .warning
                ? "已到场，但位置策略提示 \(receipt.geofenceResult.rawValue)"
                : "到场已由服务器确认"
            taskDetail = try await dependencies.onlineService.taskDetail(
                contextID: session.activeContext.contextId,
                taskID: taskID
            )
        } catch is TechnicianLocationError {
            onlineMessage = "无法获取位置，请在系统设置中允许使用 App 时定位后重试"
        } catch {
            onlineMessage = Self.safeMessage(for: error)
        }
    }

    func interrupt(
        session: TechnicianSession,
        visitID: UUID,
        aggregateVersion: Int64,
        taskID: UUID,
        exceptionCode: String,
        note: String
    ) async {
        guard let dependencies else { return }
        onlineLoading = true
        defer { onlineLoading = false }
        do {
            _ = try await dependencies.onlineService.interrupt(
                contextID: session.activeContext.contextId,
                visitID: visitID,
                aggregateVersion: aggregateVersion,
                exceptionCode: exceptionCode,
                note: note
            )
            onlineMessage = "无法施工已由服务器确认；未伪造任何资料上传"
            taskDetail = try await dependencies.onlineService.taskDetail(
                contextID: session.activeContext.contextId,
                taskID: taskID
            )
        } catch {
            onlineMessage = Self.safeMessage(for: error)
        }
    }

    func submitForm(
        session: TechnicianSession,
        taskID: UUID,
        formVersionID: UUID,
        values: [String: TechnicianFormValue]
    ) async {
        guard let dependencies else { return }
        onlineLoading = true
        formMessage = "正在提交不可变表单事实…"
        formIssues = []
        defer { onlineLoading = false }
        do {
            let result = try await dependencies.onlineService.submitForm(
                contextID: session.activeContext.contextId,
                taskID: taskID,
                formVersionID: formVersionID,
                values: values
            )
            formIssues = result.errors
            formMessage = result.validationStatus == "VALIDATED"
                ? "表单提交成功（版本 \(result.submissionVersion)）"
                : "服务器已保留 INVALID 提交，请按错误修正后产生新版本"
            taskDetail = try await dependencies.onlineService.taskDetail(
                contextID: session.activeContext.contextId,
                taskID: taskID
            )
        } catch {
            formMessage = Self.safeMessage(for: error)
        }
    }

    private func restoreSession(using dependencies: TechnicianAppDependencies) async {
        phase = .launching
        if await dependencies.tokenVault.currentAccessToken() != nil {
            do {
                try await loadSession(using: dependencies)
                return
            } catch {
                await dependencies.tokenLifecycle.logout()
                phase = .failed(message: Self.safeMessage(for: error))
                return
            }
        }
        if await dependencies.tokenVault.currentRefreshToken() != nil {
            do {
                try await dependencies.tokenLifecycle.refresh()
                try await loadSession(using: dependencies)
                return
            } catch {
                // Refresh 被拒绝后必须清理旧凭据，避免每次启动重复请求或误展示已登录状态。
                await dependencies.tokenLifecycle.logout()
            }
        }
        phase = .signedOut
    }

    private func loadSession(
        using dependencies: TechnicianAppDependencies,
        preferredContextID: String? = nil
    ) async throws {
        let session = try await dependencies.sessionLoader.load(preferredContextId: preferredContextID)
        let visibleTabs = TechnicianAppTab.visibleTabs(for: session)
        guard let firstVisibleTab = visibleTabs.first else {
            throw TechnicianAppNavigationError.noSupportedPage
        }
        selectedTab = visibleTabs.contains(selectedTab) ? selectedTab : firstVisibleTab
        clearOnlineState()
        phase = .ready(session)
    }

    private func clearOnlineState() {
        taskFeed = nil
        taskDetail = nil
        taskForms = []
        formMessage = nil
        formIssues = []
        evidenceSlots = []
        evidenceItems = []
        evidenceMessage = nil
        evidenceUploading = false
        onlineMessage = nil
        onlineLoading = false
    }

    private static func safeMessage(for error: Error) -> String {
        if let apiError = error as? ServiceAPIError { return apiError.safeUserMessage }
        if error is TechnicianSessionError { return "师傅上下文不可用，请重新登录或联系管理员" }
        if error is TechnicianAppNavigationError { return "当前账号没有可用的师傅端页面，请联系管理员" }
        if error is TechnicianConfigurationError { return "应用环境配置无效，请联系管理员" }
        if error is OIDCConfigurationError || error is OIDCTokenLifecycleError {
            return "登录未完成，请重试"
        }
        return "操作未完成，请稍后重试"
    }
}
