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

actor FakeOnlineTransport: ServiceHTTPTransporting {
    struct Captured: Sendable {
        let method: String
        let path: String
        let context: String?
        let idempotencyKey: String?
        let ifMatch: String?
        let authorization: String?
        let clientKind: String?
        let body: String
    }

    private(set) var requests: [Captured] = []

    func execute(_ request: URLRequest) async throws -> ServiceHTTPResponse {
        requests.append(.init(
            method: request.httpMethod ?? "GET",
            path: request.url!.path,
            context: request.value(forHTTPHeaderField: "X-Technician-Context"),
            idempotencyKey: request.value(forHTTPHeaderField: "Idempotency-Key"),
            ifMatch: request.value(forHTTPHeaderField: "If-Match"),
            authorization: request.value(forHTTPHeaderField: "Authorization"),
            clientKind: request.value(forHTTPHeaderField: "X-ServiceOS-Client-Kind"),
            body: String(data: request.httpBody ?? Data(), encoding: .utf8) ?? ""
        ))
        let body: String
        if request.url!.path.hasSuffix("/task-feed") {
            body = #"{"networkId":"20000000-0000-4000-8000-000000000262","items":[],"nextCursor":null,"asOf":"2026-07-18T10:00:00Z"}"#
        } else if request.url!.path.hasSuffix("/forms") {
            body = #"[{"taskId":"50000000-0000-4000-8000-000000000263","formVersionId":"60000000-0000-4000-8000-000000000263","formKey":"INSTALL_REPORT","semanticVersion":"1.0.0","schemaVersion":"FORM_V1","definition":{"title":"安装结果","sections":[{"sectionKey":"result","title":"现场结果","fields":[{"fieldKey":"survey.conclusion","label":"勘测结论","dataType":"STRING","binding":"task.input.survey.conclusion","required":true},{"fieldKey":"installation.count","label":"安装数量","dataType":"INTEGER","binding":"task.input.installation.count"},{"fieldKey":"site.safe","label":"现场安全","dataType":"BOOLEAN","binding":"task.input.site.safe"}]}]},"contentDigest":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]"#
        } else if request.url!.path.hasSuffix("/form-submissions") {
            body = #"{"submissionId":"70000000-0000-4000-8000-000000000263","taskId":"50000000-0000-4000-8000-000000000263","projectId":"80000000-0000-4000-8000-000000000263","formVersionId":"60000000-0000-4000-8000-000000000263","formKey":"INSTALL_REPORT","submissionVersion":1,"values":{"survey.conclusion":"PASS","installation.count":2,"site.safe":true},"contentDigest":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","validationStatus":"VALIDATED","errors":[],"warnings":[],"submittedAt":"2026-07-18T10:00:01Z"}"#
        } else if request.url!.path.hasSuffix("/evidence-slots") {
            body = #"[{"slotId":"90000000-0000-4000-8000-000000000264","requirementCode":"SITE_PHOTO","occurrenceKey":"default","requirementName":"现场照片","mediaType":"PHOTO","required":true,"minCount":1,"maxCount":2,"status":"OPEN","active":true,"transition":"CREATE","requiredDisposition":"SUBMIT"}]"#
        } else if request.url!.path.hasSuffix("/evidence-items") {
            body = #"[]"#
        } else if request.url!.path.hasSuffix("/upload-sessions") {
            body = #"{"uploadSessionId":"91000000-0000-4000-8000-000000000264","evidenceSlotId":"90000000-0000-4000-8000-000000000264","evidenceItemId":null,"status":"AUTHORIZED","uploadMethod":"PUT","uploadUrl":"https://uploads.example/once","requiredHeaders":{"Content-Type":"image/jpeg"},"uploadAuthorizationExpiresAt":"2026-07-18T10:05:00Z","sessionExpiresAt":"2026-07-18T10:10:00Z"}"#
        } else if request.url!.path.hasSuffix("/once") {
            body = #"{}"#
        } else if request.url!.path.hasSuffix(":finalize") {
            body = #"{"evidenceItemId":"92000000-0000-4000-8000-000000000264","taskId":"50000000-0000-4000-8000-000000000263","evidenceSlotId":"90000000-0000-4000-8000-000000000264","itemOrdinal":1,"status":"STORED","createdAt":"2026-07-18T10:00:02Z","revisions":[{"evidenceRevisionId":"93000000-0000-4000-8000-000000000264","revisionNumber":1,"contentDigest":"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855","mimeType":"image/jpeg","sizeBytes":10,"status":"STORED","createdAt":"2026-07-18T10:00:02Z"}]}"#
        } else {
            body = #"{"visitId":"40000000-0000-4000-8000-000000000262","status":"IN_PROGRESS","aggregateVersion":1,"geofenceResult":"WITHIN_GEOFENCE","policyDecision":"ACCEPTED","occurredAt":"2026-07-18T10:00:01Z"}"#
        }
        return ServiceHTTPResponse(data: Data(body.utf8), status: 200, diagnostics: .init(headers: [:]))
    }

    func captured() -> [Captured] { requests }
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

        let onlineTransport = FakeOnlineTransport()
        await tokenVault.store(.init(accessToken: "online-access", expiresAt: now.addingTimeInterval(60)))
        let online = TechnicianOnlineService(requestBuilder: builder, transport: onlineTransport)
        let contextID = "TECHNICIAN|NETWORK|20000000-0000-4000-8000-000000000262"
        let feed = try await online.taskFeed(contextID: contextID)
        precondition(feed.items.isEmpty)
        _ = try await online.checkIn(
            contextID: contextID,
            appointmentID: UUID(uuidString: "30000000-0000-4000-8000-000000000262")!,
            deviceID: "ios-device-262",
            deviceCommandID: "ios-command-262",
            location: .init(latitude: 31.2304, longitude: 121.4737, accuracyMeters: 8,
                            capturedAt: Date(timeIntervalSince1970: 1_800_000_000))
        )
        _ = try await online.interrupt(
            contextID: contextID,
            visitID: UUID(uuidString: "40000000-0000-4000-8000-000000000262")!,
            aggregateVersion: 1,
            exceptionCode: "SITE_UNSAFE",
            note: "现场存在安全风险"
        )
        let formTaskID = UUID(uuidString: "50000000-0000-4000-8000-000000000263")!
        let formVersionID = UUID(uuidString: "60000000-0000-4000-8000-000000000263")!
        let forms = try await online.taskForms(contextID: contextID, taskID: formTaskID)
        precondition(forms.first?.definition.sections.first?.fields.count == 3)
        let submission = try await online.submitForm(
            contextID: contextID,
            taskID: formTaskID,
            formVersionID: formVersionID,
            values: [
                "survey.conclusion": .string("PASS"),
                "installation.count": .integer(2),
                "site.safe": .boolean(true),
            ]
        )
        precondition(submission.validationStatus == "VALIDATED")
        let onlineRequests = await onlineTransport.captured()
        precondition(onlineRequests.count == 5)
        precondition(onlineRequests.allSatisfy { $0.context == contextID })
        precondition(onlineRequests[1].idempotencyKey == "ios-command-262")
        precondition(!onlineRequests[1].body.contains("offline"))
        precondition(!onlineRequests[1].body.contains("receivedAt"))
        precondition(onlineRequests[2].ifMatch == "\"1\"")
        precondition(onlineRequests[2].body.contains(#""evidenceRefs":[]"#))
        precondition(onlineRequests[3].method == "GET")
        precondition(onlineRequests[3].path.hasSuffix("/forms"))
        precondition(onlineRequests[4].idempotencyKey != nil)
        precondition(onlineRequests[4].body.contains(#""installation.count":2"#))
        precondition(onlineRequests[4].body.contains(#""site.safe":true"#))
        precondition(!onlineRequests[4].body.contains("prefillVersion"))
        precondition(!onlineRequests[4].body.contains("submittedBy"))
        let evidenceSlots = try await online.evidenceSlots(contextID: contextID, taskID: formTaskID)
        precondition(evidenceSlots.first?.requirementCode == "SITE_PHOTO")
        let evidenceItems = try await online.evidenceItems(contextID: contextID, taskID: formTaskID)
        precondition(evidenceItems.isEmpty)
        let uploaded = try await online.uploadEvidence(
            contextID: contextID,
            taskID: formTaskID,
            slotID: evidenceSlots[0].slotId,
            evidenceItemID: nil,
            asset: .init(
                data: Data("0123456789".utf8),
                fileName: "现场照片.jpg",
                mimeType: "image/jpeg",
                source: .camera,
                capturedAt: Date(timeIntervalSince1970: 1_800_000_001)
            )
        )
        precondition(uploaded.status == "STORED")
        let evidenceRequests = await onlineTransport.captured()
        precondition(evidenceRequests.count == 10)
        precondition(evidenceRequests[7].body.contains(#""captureSource":"CAMERA""#))
        precondition(evidenceRequests[7].body.contains(#""expectedSize":10"#))
        precondition(!evidenceRequests[7].body.contains("offline"))
        precondition(!evidenceRequests[7].body.contains("uploader"))
        precondition(!evidenceRequests[7].body.contains("locationVerified"))
        precondition(evidenceRequests[8].method == "PUT")
        precondition(evidenceRequests[8].context == nil)
        precondition(evidenceRequests[8].authorization == nil)
        precondition(evidenceRequests[8].clientKind == nil)
        precondition(evidenceRequests[8].idempotencyKey == nil)
        precondition(evidenceRequests[9].body.contains("actualSha256"))
        precondition(evidenceRequests[9].body.contains("finalizeCommandId"))
    }
}
