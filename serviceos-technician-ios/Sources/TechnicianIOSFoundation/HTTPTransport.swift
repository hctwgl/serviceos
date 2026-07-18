import Foundation
import ServiceOSIOSCore

public struct ServiceHTTPResponse: Sendable {
    public let data: Data
    public let status: Int
    public let diagnostics: DiagnosticContext

    public init(data: Data, status: Int, diagnostics: DiagnosticContext) {
        self.data = data
        self.status = status
        self.diagnostics = diagnostics
    }
}

public protocol ServiceHTTPTransporting: Sendable {
    func execute(_ request: URLRequest) async throws -> ServiceHTTPResponse
}

public struct URLSessionServiceTransport: ServiceHTTPTransporting {
    private let session: URLSession

    public init(session: URLSession = .shared) {
        self.session = session
    }

    public func execute(_ request: URLRequest) async throws -> ServiceHTTPResponse {
        let (data, rawResponse) = try await session.data(for: request)
        guard let response = rawResponse as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        let headers = response.allHeaderFields.reduce(into: [String: String]()) { result, pair in
            result[String(describing: pair.key)] = String(describing: pair.value)
        }
        let diagnostics = DiagnosticContext(headers: headers)
        guard (200..<300).contains(response.statusCode) else {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let problem = try? decoder.decode(ProblemDetails.self, from: data)
            throw ServiceAPIError(status: response.statusCode, problem: problem, diagnostics: diagnostics)
        }
        return ServiceHTTPResponse(data: data, status: response.statusCode, diagnostics: diagnostics)
    }
}
