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

public enum TechnicianFormValue: Sendable, Equatable, Codable {
    case string(String)
    case integer(Int)
    case decimal(Double)
    case boolean(Bool)

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let value = try? container.decode(Bool.self) { self = .boolean(value) }
        else if let value = try? container.decode(Int.self) { self = .integer(value) }
        else if let value = try? container.decode(Double.self) { self = .decimal(value) }
        else { self = .string(try container.decode(String.self)) }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case let .string(value): try container.encode(value)
        case let .integer(value): try container.encode(value)
        case let .decimal(value): try container.encode(value)
        case let .boolean(value): try container.encode(value)
        }
    }
}

public struct TechnicianTaskForm: Sendable, Decodable, Equatable {
    public let taskId: UUID
    public let formVersionId: UUID
    public let formKey: String
    public let semanticVersion: String
    public let schemaVersion: String
    public let definition: TechnicianFormDefinition
    public let contentDigest: String
}

public struct TechnicianFormDefinition: Sendable, Decodable, Equatable {
    public let title: String?
    public let sections: [TechnicianFormSection]
    public let hasValidationRules: Bool

    private enum CodingKeys: String, CodingKey { case title, sections, validationRules }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        title = try container.decodeIfPresent(String.self, forKey: .title)
        sections = try container.decode([TechnicianFormSection].self, forKey: .sections)
        hasValidationRules = !(try container.decodeIfPresent([IgnoredJSON].self, forKey: .validationRules) ?? []).isEmpty
    }
}

public struct TechnicianFormSection: Sendable, Decodable, Equatable {
    public let sectionKey: String
    public let title: String
    public let fields: [TechnicianFormField]
    public let hasVisibility: Bool

    private enum CodingKeys: String, CodingKey { case sectionKey, title, fields, visibility }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        sectionKey = try container.decode(String.self, forKey: .sectionKey)
        title = try container.decode(String.self, forKey: .title)
        fields = try container.decode([TechnicianFormField].self, forKey: .fields)
        hasVisibility = container.contains(.visibility)
            ? !(try container.decodeNil(forKey: .visibility))
            : false
    }
}

public struct TechnicianFormField: Sendable, Decodable, Equatable {
    public let fieldKey: String
    public let label: String
    public let dataType: String
    public let required: Bool
    public let hasConditionalBehavior: Bool
    public let hasOptionsOrValidators: Bool

    private enum CodingKeys: String, CodingKey {
        case fieldKey, label, dataType, required, requiredWhen, visibleWhen, editableWhen
        case defaultExpression, optionsRef, validators
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        fieldKey = try container.decode(String.self, forKey: .fieldKey)
        label = try container.decode(String.self, forKey: .label)
        dataType = try container.decode(String.self, forKey: .dataType)
        required = try container.decodeIfPresent(Bool.self, forKey: .required) ?? false
        hasConditionalBehavior = [.requiredWhen, .visibleWhen, .editableWhen, .defaultExpression]
            .contains { container.contains($0) && (try? container.decodeNil(forKey: $0)) == false }
        let validators = try container.decodeIfPresent([IgnoredJSON].self, forKey: .validators) ?? []
        hasOptionsOrValidators = container.contains(.optionsRef) || !validators.isEmpty
    }
}

public struct TechnicianFormValidationIssue: Sendable, Decodable, Equatable {
    public let fieldKey: String
    public let code: String
    public let message: String
}

public struct TechnicianFormSubmissionResult: Sendable, Decodable, Equatable {
    public let submissionId: UUID
    public let submissionVersion: Int
    public let validationStatus: String
    public let errors: [TechnicianFormValidationIssue]
    public let warnings: [TechnicianFormValidationIssue]
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

    public func taskForms(contextID: String, taskID: UUID) async throws -> [TechnicianTaskForm] {
        try await get(
            "/technician/me/tasks/\(taskID.uuidString.lowercased())/forms",
            contextID: contextID
        )
    }

    /// 在线提交不发送 prefillVersion；草稿/预填冲突策略未接受前不能由客户端猜测。
    public func submitForm(
        contextID: String,
        taskID: UUID,
        formVersionID: UUID,
        values: [String: TechnicianFormValue]
    ) async throws -> TechnicianFormSubmissionResult {
        try await post(
            "/technician/me/tasks/\(taskID.uuidString.lowercased())/form-submissions",
            contextID: contextID,
            idempotencyKey: UUID().uuidString.lowercased(),
            ifMatch: nil,
            body: SubmitFormBody(formVersionId: formVersionID, values: values)
        )
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

    private struct SubmitFormBody: Encodable {
        let formVersionId: UUID
        let values: [String: TechnicianFormValue]
    }
}

private enum IgnoredJSON: Decodable, Sendable, Equatable {
    case value

    init(from decoder: Decoder) throws {
        if var array = try? decoder.unkeyedContainer() {
            while !array.isAtEnd { _ = try array.decode(IgnoredJSON.self) }
        } else if let object = try? decoder.container(keyedBy: DynamicCodingKey.self) {
            for key in object.allKeys { _ = try object.decode(IgnoredJSON.self, forKey: key) }
        } else {
            _ = try? decoder.singleValueContainer().decode(Bool.self)
            _ = try? decoder.singleValueContainer().decode(Double.self)
            _ = try? decoder.singleValueContainer().decode(String.self)
            _ = try? decoder.singleValueContainer().decodeNil()
        }
        self = .value
    }
}

private struct DynamicCodingKey: CodingKey {
    let stringValue: String
    let intValue: Int? = nil
    init?(stringValue: String) { self.stringValue = stringValue }
    init?(intValue: Int) { return nil }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
