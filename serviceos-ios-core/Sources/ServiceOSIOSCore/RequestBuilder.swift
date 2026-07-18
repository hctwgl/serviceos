import Foundation

public struct ServiceRequestBuilder: Sendable {
    private let baseURL: URL
    private let tokenProvider: any AccessTokenProviding
    private let clientMetadata: IOSClientMetadata
    private let correlationId: @Sendable () -> UUID

    public init(
        baseURL: URL,
        tokenProvider: any AccessTokenProviding,
        clientMetadata: IOSClientMetadata,
        correlationId: @escaping @Sendable () -> UUID = UUID.init
    ) {
        precondition(baseURL.scheme == "https" || baseURL.host == "localhost", "非本地 API 必须使用 HTTPS")
        self.baseURL = baseURL
        self.tokenProvider = tokenProvider
        self.clientMetadata = clientMetadata
        self.correlationId = correlationId
    }

    public func build(
        path: String,
        method: String = "GET",
        contextHeaders: [String: String] = [:],
        body: Data? = nil
    ) async -> URLRequest {
        precondition(path.hasPrefix("/"), "API path 必须以 / 开头")
        var request = URLRequest(url: baseURL.appending(path: String(path.dropFirst())))
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(correlationId().uuidString.lowercased(), forHTTPHeaderField: "X-Correlation-Id")
        request.setValue(clientMetadata.kind.rawValue, forHTTPHeaderField: "X-ServiceOS-Client-Kind")
        request.setValue(clientMetadata.version, forHTTPHeaderField: "X-ServiceOS-Client-Version")
        if body != nil { request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        if let token = await tokenProvider.currentAccessToken() {
            request.setValue("Bearer \(token.accessToken)", forHTTPHeaderField: "Authorization")
        }
        for (name, value) in contextHeaders { request.setValue(value, forHTTPHeaderField: name) }
        return request
    }
}
