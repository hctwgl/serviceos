import Foundation
import ServiceOSCoreClient
import ServiceOSIOSCore

public struct TechnicianCapturedLocation: Sendable, Equatable {
    public let latitude: Double
    public let longitude: Double
    public let accuracyMeters: Double
    public let capturedAt: Date

    public init(latitude: Double, longitude: Double, accuracyMeters: Double, capturedAt: Date) {
        self.latitude = latitude
        self.longitude = longitude
        self.accuracyMeters = accuracyMeters
        self.capturedAt = capturedAt
    }
}

public struct TechnicianOnlineService: Sendable {
    private let requestBuilder: ServiceRequestBuilder
    private let transport: any ServiceHTTPTransporting

    public init(requestBuilder: ServiceRequestBuilder, transport: any ServiceHTTPTransporting) {
        self.requestBuilder = requestBuilder
        self.transport = transport
    }

    public func taskFeed(contextID: String) async throws -> TechnicianPortalFeedPage {
        try await get("/technician/me/task-feed", contextID: contextID)
    }

    public func taskDetail(contextID: String, taskID: UUID) async throws -> TechnicianPortalTaskDetail {
        try await get("/technician/me/tasks/\(taskID.uuidString.lowercased())", contextID: contextID)
    }

    /// 签到只接受本次主动采集的位置；receivedAt、师傅和网点都由服务端权威生成。
    public func checkIn(
        contextID: String,
        appointmentID: UUID,
        deviceID: String,
        deviceCommandID: String,
        location: TechnicianCapturedLocation
    ) async throws -> VisitCommandReceipt {
        let body = CheckInBody(
            capturedAt: location.capturedAt,
            deviceCommandId: deviceCommandID,
            deviceId: deviceID,
            location: .init(
                latitude: location.latitude,
                longitude: location.longitude,
                accuracyMeters: max(location.accuracyMeters, 0.1)
            )
        )
        return try await post(
            "/technician/me/appointments/\(appointmentID.uuidString.lowercased())/visits:check-in",
            contextID: contextID,
            idempotencyKey: deviceCommandID,
            ifMatch: nil,
            body: body
        )
    }

    /// 无法施工不伪造 Evidence；尚未上传资料时明确发送空引用集合。
    public func interrupt(
        contextID: String,
        visitID: UUID,
        aggregateVersion: Int64,
        exceptionCode: String,
        note: String?
    ) async throws -> VisitCommandReceipt {
        try await post(
            "/technician/me/visits/\(visitID.uuidString.lowercased()):interrupt",
            contextID: contextID,
            idempotencyKey: UUID().uuidString.lowercased(),
            ifMatch: "\"\(aggregateVersion)\"",
            body: InterruptBody(
                capturedAt: Date(),
                exceptionCode: exceptionCode,
                note: note?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
                evidenceRefs: []
            )
        )
    }

    private func get<T: Decodable>(_ path: String, contextID: String) async throws -> T {
        let request = await requestBuilder.build(
            path: path,
            contextHeaders: ["X-Technician-Context": contextID]
        )
        return try decode(await transport.execute(request))
    }

    private func post<T: Decodable, Body: Encodable>(
        _ path: String,
        contextID: String,
        idempotencyKey: String,
        ifMatch: String?,
        body: Body
    ) async throws -> T {
        var headers = [
            "X-Technician-Context": contextID,
            "Idempotency-Key": idempotencyKey,
        ]
        if let ifMatch { headers["If-Match"] = ifMatch }
        let request = await requestBuilder.build(
            path: path,
            method: "POST",
            contextHeaders: headers,
            body: try Self.encoder.encode(body)
        )
        return try decode(await transport.execute(request))
    }

    private func decode<T: Decodable>(_ response: ServiceHTTPResponse) throws -> T {
        try Self.decoder.decode(T.self, from: response.data)
    }

    private static var encoder: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }

    private static var decoder: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }

    private struct LocationBody: Encodable {
        let latitude: Double
        let longitude: Double
        let accuracyMeters: Double
    }

    private struct CheckInBody: Encodable {
        let capturedAt: Date
        let deviceCommandId: String
        let deviceId: String
        let location: LocationBody
    }

    private struct InterruptBody: Encodable {
        let capturedAt: Date
        let exceptionCode: String
        let note: String?
        let evidenceRefs: [String]
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
