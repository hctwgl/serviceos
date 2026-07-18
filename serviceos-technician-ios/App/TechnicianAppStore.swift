import Foundation
import Observation
import ServiceOSCoreClient
import ServiceOSIOSCore
import TechnicianIOSFoundation

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
            )
        )
    }
}

@MainActor
@Observable
final class TechnicianAppStore {
    private(set) var phase: TechnicianAppPhase = .launching
    private(set) var configuration: TechnicianIOSConfiguration?
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
        phase = .signedOut
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
        phase = .ready(session)
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
