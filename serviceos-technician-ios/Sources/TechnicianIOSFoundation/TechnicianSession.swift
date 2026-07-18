import Foundation
import ServiceOSCoreClient
import ServiceOSIOSCore

public enum TechnicianSessionError: Error, Equatable {
    case noContext
    case contextNotOwned
    case portalMismatch
    case contextVersionMismatch
}

public struct TechnicianSession: Sendable {
    public let contexts: [MeContext]
    public let activeContext: MeContext
    public let capabilities: MeCapabilities
    public let navigation: MeNavigation
}

public struct TechnicianSessionLoader: Sendable {
    private let requestBuilder: ServiceRequestBuilder
    private let transport: any ServiceHTTPTransporting

    public init(requestBuilder: ServiceRequestBuilder, transport: any ServiceHTTPTransporting) {
        self.requestBuilder = requestBuilder
        self.transport = transport
    }

    /// Context 只能来自本轮 `/me/contexts`；Capability/导航仅用于呈现，业务请求仍按当前责任重鉴权。
    public func load(preferredContextId: String? = nil) async throws -> TechnicianSession {
        let contexts: MeContexts = try await get("/me/contexts")
        let technicianContexts = contexts.contexts.filter { $0.portal == .technician }
        guard !technicianContexts.isEmpty else { throw TechnicianSessionError.noContext }
        let activeContext: MeContext
        if let preferredContextId {
            guard let owned = technicianContexts.first(where: { $0.contextId == preferredContextId }) else {
                throw TechnicianSessionError.contextNotOwned
            }
            activeContext = owned
        } else {
            activeContext = technicianContexts[0]
        }
        let query = Self.contextQuery(contextId: activeContext.contextId, contextVersion: contexts.contextVersion)
        async let capabilities: MeCapabilities = get("/me/capabilities?\(query)")
        async let navigation: MeNavigation = get("/me/navigation?\(query)")
        let resolvedCapabilities = try await capabilities
        let resolvedNavigation = try await navigation
        guard resolvedCapabilities.portal == .technician, resolvedNavigation.portal == .technician else {
            throw TechnicianSessionError.portalMismatch
        }
        guard resolvedCapabilities.contextVersion == contexts.contextVersion,
              resolvedNavigation.contextVersion == contexts.contextVersion else {
            throw TechnicianSessionError.contextVersionMismatch
        }
        return TechnicianSession(
            contexts: technicianContexts,
            activeContext: activeContext,
            capabilities: resolvedCapabilities,
            navigation: resolvedNavigation
        )
    }

    private func get<T: Decodable>(_ path: String) async throws -> T {
        let request = await requestBuilder.build(path: path)
        let response = try await transport.execute(request)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(T.self, from: response.data)
    }

    private static func contextQuery(contextId: String, contextVersion: String) -> String {
        var components = URLComponents()
        components.queryItems = [
            .init(name: "contextId", value: contextId),
            .init(name: "expectedContextVersion", value: contextVersion),
        ]
        return components.percentEncodedQuery ?? ""
    }
}
