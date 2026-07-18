import Foundation
import ServiceOSCoreClient
import ServiceOSIOSCore
import TechnicianIOSFoundation

actor FakeTransport: ServiceHTTPTransporting {
    private(set) var paths: [String] = []

    func execute(_ request: URLRequest) async throws -> ServiceHTTPResponse {
        let path = request.url!.path + (request.url!.query.map { "?\($0)" } ?? "")
        paths.append(path)
        let body: String
        if request.url!.path.hasSuffix("/me/contexts") {
            body = #"{"contexts":[{"contextId":"ADMIN|TENANT|t1","portal":"ADMIN","personaType":"INTERNAL_EMPLOYEE","scopeType":"TENANT","scopeRef":"t1","scopeSummary":{"organizationIds":[],"networkIds":[],"projectIds":[]},"version":"v7"},{"contextId":"TECHNICIAN|NETWORK|n1","portal":"TECHNICIAN","personaType":"TECHNICIAN","scopeType":"NETWORK","scopeRef":"n1","scopeSummary":{"organizationIds":[],"networkIds":["n1"],"projectIds":[]},"version":"v7"}],"contextVersion":"v7","asOf":"2026-07-18T08:00:00Z"}"#
        } else if request.url!.path.hasSuffix("/me/capabilities") {
            body = #"{"contextId":"TECHNICIAN|NETWORK|n1","portal":"TECHNICIAN","capabilityCodes":["task.readAssigned"],"contextVersion":"v7","asOf":"2026-07-18T08:00:00Z"}"#
        } else {
            body = #"{"contextId":"TECHNICIAN|NETWORK|n1","portal":"TECHNICIAN","contextVersion":"v7","navigationCatalogVersion":"page-registry-v16","items":[],"asOf":"2026-07-18T08:00:00Z"}"#
        }
        return ServiceHTTPResponse(data: Data(body.utf8), status: 200, diagnostics: .init(headers: [:]))
    }

    func capturedPaths() -> [String] { paths }
}

actor FakeOIDCTransport: OIDCTokenEndpointTransporting {
    private(set) var bodies: [String] = []

    func execute(_ request: URLRequest) async throws -> (Data, Int) {
        bodies.append(String(data: request.httpBody ?? Data(), encoding: .utf8) ?? "")
        let body = #"{"access_token":"exchanged-access","refresh_token":"refresh-1","expires_in":300}"#
        return (Data(body.utf8), 200)
    }

    func capturedBodies() -> [String] { bodies }
}

actor TestOIDCTokenVault: OIDCTokenPersisting {
    private var tokenSet: OIDCTokenSet?
    private let now: Date

    init(now: Date) { self.now = now }

    func store(_ snapshot: AccessTokenSnapshot) {
        tokenSet = .init(accessToken: snapshot.accessToken, refreshToken: nil, expiresAt: snapshot.expiresAt)
    }

    func store(_ next: OIDCTokenSet) { tokenSet = next }

    func currentAccessToken() -> AccessTokenSnapshot? {
        guard let tokenSet, tokenSet.expiresAt > now else { return nil }
        return .init(accessToken: tokenSet.accessToken, expiresAt: tokenSet.expiresAt)
    }

    func currentRefreshToken() -> String? { tokenSet?.refreshToken }

    func clear() { tokenSet = nil }
}

@main
struct FoundationSmoke {
    static func main() async throws {
        let values = [
            "SERVICEOS_ENV": "staging",
            "SERVICEOS_API_BASE_URL": "https://api.example/api/v1/",
            "SERVICEOS_OIDC_ISSUER": "https://identity.example/realms/serviceos/",
            "SERVICEOS_OIDC_CLIENT_ID": "serviceos-technician-ios",
            "SERVICEOS_OIDC_REDIRECT_SCHEME": "serviceos-technician",
            "SERVICEOS_CLIENT_VERSION": "1.2.3+45",
        ]
        let configuration = try TechnicianIOSConfiguration.resolve(values)
        precondition(configuration.clientMetadata.kind == .technicianIOS)
        do {
            _ = try TechnicianIOSConfiguration.resolve(values.merging(["SERVICEOS_API_BASE_URL": "http://api.example"]) { _, new in new })
            preconditionFailure("staging HTTP 必须失败关闭")
        } catch TechnicianConfigurationError.insecureURL { }

        let oidc = OIDCAuthorizationRequestFactory(configuration: configuration)
        let (authorizationURL, transaction) = try oidc.begin(returnPath: "/technician/tasks/task-1")
        precondition(authorizationURL.query!.contains("code_challenge_method=S256"))
        let callback = URL(string: "serviceos-technician://oauth/callback?code=code-1&state=\(transaction.state)")!
        let authorizationCode = try oidc.validate(callbackURL: callback, transaction: transaction)
        precondition(authorizationCode == "code-1")

        let now = Date(timeIntervalSince1970: 1_000)
        let tokenVault = TestOIDCTokenVault(now: now)
        await tokenVault.store(.init(accessToken: "memory-secret", expiresAt: now.addingTimeInterval(60)))
        let storedToken = await tokenVault.currentAccessToken()
        precondition(storedToken?.accessToken == "memory-secret")
        let oidcTransport = FakeOIDCTransport()
        let lifecycle = OIDCTokenLifecycle(
            requestFactory: .init(configuration: configuration),
            transport: oidcTransport,
            vault: tokenVault,
            now: { now }
        )
        try await lifecycle.exchange(code: authorizationCode, transaction: transaction)
        let refreshToken = await tokenVault.currentRefreshToken()
        precondition(refreshToken == "refresh-1")
        try await lifecycle.refresh()
        let tokenBodies = await oidcTransport.capturedBodies()
        precondition(tokenBodies.count == 2)
        precondition(tokenBodies[0].contains("code_verifier="))
        precondition(!tokenBodies[0].contains("client_secret"))
        precondition(tokenBodies[1].contains("grant_type=refresh_token"))
        await lifecycle.logout()
        let clearedToken = await tokenVault.currentAccessToken()
        precondition(clearedToken == nil)

        let builder = ServiceRequestBuilder(
            baseURL: configuration.apiBaseURL,
            tokenProvider: tokenVault,
            clientMetadata: configuration.clientMetadata
        )
        let transport = FakeTransport()
        let loader = TechnicianSessionLoader(requestBuilder: builder, transport: transport)
        let session = try await loader.load()
        precondition(session.activeContext.contextId == "TECHNICIAN|NETWORK|n1")
        precondition(session.capabilities.capabilityCodes == ["task.readAssigned"])
        let paths = await transport.capturedPaths()
        precondition(paths.count == 3)
        precondition(paths[1].contains("expectedContextVersion=v7"))
        do {
            _ = try await loader.load(preferredContextId: "TECHNICIAN|NETWORK|forged")
            preconditionFailure("伪造 Context 必须失败关闭")
        } catch TechnicianSessionError.contextNotOwned { }

        let redacted = SecureDiagnostics.redacted([
            "traceId": "trace-1", "authorization": "Bearer secret", "photoPath": "/private/photo.jpg",
        ])
        precondition(redacted["traceId"] == "trace-1")
        precondition(redacted["authorization"] == "<redacted>")
        precondition(redacted["photoPath"] == "<redacted>")
        precondition(GeneratedContractBoundary.apiConfigurationType == ServiceOSCoreClientAPIConfiguration.self)
        precondition(GeneratedContractBoundary.primaryActionColor == "#243B53")
    }
}
