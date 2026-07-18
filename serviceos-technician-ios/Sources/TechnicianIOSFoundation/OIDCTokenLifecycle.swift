import Foundation

public enum OIDCTokenLifecycleError: Error, Equatable {
    case invalidResponse
    case requestRejected(Int)
    case noRefreshToken
}

public struct OIDCTokenEndpointRequestFactory: Sendable {
    private let configuration: TechnicianIOSConfiguration

    public init(configuration: TechnicianIOSConfiguration) {
        self.configuration = configuration
    }

    public func authorizationCodeRequest(code: String, transaction: PKCETransaction) -> URLRequest {
        tokenRequest([
            "grant_type": "authorization_code",
            "client_id": configuration.oidcClientId,
            "redirect_uri": "\(configuration.redirectScheme)://oauth/callback",
            "code": code,
            "code_verifier": transaction.verifier,
        ])
    }

    public func refreshRequest(refreshToken: String) -> URLRequest {
        tokenRequest([
            "grant_type": "refresh_token",
            "client_id": configuration.oidcClientId,
            "refresh_token": refreshToken,
        ])
    }

    private func tokenRequest(_ parameters: [String: String]) -> URLRequest {
        let endpoint = configuration.oidcIssuer.appending(path: "protocol/openid-connect/token")
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        var components = URLComponents()
        components.queryItems = parameters.sorted(by: { $0.key < $1.key }).map(URLQueryItem.init)
        request.httpBody = components.percentEncodedQuery?.data(using: .utf8)
        return request
    }
}

public protocol OIDCTokenEndpointTransporting: Sendable {
    func execute(_ request: URLRequest) async throws -> (Data, Int)
}

public struct URLSessionOIDCTokenTransport: OIDCTokenEndpointTransporting {
    private let session: URLSession

    public init(session: URLSession = .shared) { self.session = session }

    public func execute(_ request: URLRequest) async throws -> (Data, Int) {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw OIDCTokenLifecycleError.invalidResponse }
        return (data, http.statusCode)
    }
}

public actor OIDCTokenLifecycle {
    private struct TokenResponse: Decodable {
        let accessToken: String
        let refreshToken: String?
        let expiresIn: TimeInterval

        enum CodingKeys: String, CodingKey {
            case accessToken = "access_token"
            case refreshToken = "refresh_token"
            case expiresIn = "expires_in"
        }
    }

    private let requestFactory: OIDCTokenEndpointRequestFactory
    private let transport: any OIDCTokenEndpointTransporting
    private let vault: any OIDCTokenPersisting
    private let now: @Sendable () -> Date

    public init(
        requestFactory: OIDCTokenEndpointRequestFactory,
        transport: any OIDCTokenEndpointTransporting,
        vault: any OIDCTokenPersisting,
        now: @escaping @Sendable () -> Date = Date.init
    ) {
        self.requestFactory = requestFactory
        self.transport = transport
        self.vault = vault
        self.now = now
    }

    public func exchange(code: String, transaction: PKCETransaction) async throws {
        try await persist(requestFactory.authorizationCodeRequest(code: code, transaction: transaction))
    }

    public func refresh() async throws {
        guard let refreshToken = await vault.currentRefreshToken(), !refreshToken.isEmpty else {
            throw OIDCTokenLifecycleError.noRefreshToken
        }
        try await persist(requestFactory.refreshRequest(refreshToken: refreshToken))
    }

    public func logout() async {
        await vault.clear()
    }

    private func persist(_ request: URLRequest) async throws {
        let (data, status) = try await transport.execute(request)
        guard (200..<300).contains(status) else { throw OIDCTokenLifecycleError.requestRejected(status) }
        guard let response = try? JSONDecoder().decode(TokenResponse.self, from: data),
              !response.accessToken.isEmpty, response.expiresIn > 0 else {
            throw OIDCTokenLifecycleError.invalidResponse
        }
        try await vault.store(.init(
            accessToken: response.accessToken,
            refreshToken: response.refreshToken,
            expiresAt: now().addingTimeInterval(response.expiresIn)
        ))
    }
}
