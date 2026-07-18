import Foundation

public struct ProblemDetails: Codable, Sendable, Equatable {
    public let type: String?
    public let title: String?
    public let status: Int?
    public let detail: String?
    public let instance: String?
    public let errorCode: String?
    public let code: String?
    public let traceId: String?
    public let correlationId: String?
}

public struct DiagnosticContext: Sendable, Equatable {
    public let correlationId: String?
    public let traceId: String?
    public let traceparent: String?

    public init(headers: [String: String]) {
        correlationId = headers.first { $0.key.caseInsensitiveCompare("X-Correlation-Id") == .orderedSame }?.value
        traceId = headers.first { $0.key.caseInsensitiveCompare("X-Trace-Id") == .orderedSame }?.value
        traceparent = headers.first { $0.key.caseInsensitiveCompare("traceparent") == .orderedSame }?.value
    }
}

public struct ServiceAPIError: Error, Sendable {
    public let status: Int
    public let problem: ProblemDetails?
    public let diagnostics: DiagnosticContext

    public init(status: Int, problem: ProblemDetails?, diagnostics: DiagnosticContext) {
        self.status = status
        self.problem = problem
        self.diagnostics = diagnostics
    }

    public var safeUserMessage: String {
        switch status {
        case 401: "需要重新登录"
        case 403, 404: "无权访问或不存在"
        case 409, 412: "数据已变化，请刷新后重试"
        case 429: "请求过于频繁，请稍后重试"
        case 500...599: "服务暂时不可用，请稍后重试"
        default: "请求失败，请稍后重试"
        }
    }
}
