import Foundation
import ServiceOSIOSCore

actor Counter {
    private var value = 0
    func increment() { value += 1 }
    func current() -> Int { value }
}

@main
struct IOSCoreSmoke {
    static func main() async throws {
        let now = Date(timeIntervalSince1970: 1_000)
        let vault = MemoryAccessTokenVault(now: { now }, expirySkew: 0)
        await vault.store(.init(accessToken: "secret", expiresAt: now.addingTimeInterval(60)))

        let builder = ServiceRequestBuilder(
            baseURL: URL(string: "https://serviceos.invalid/api/v1/")!,
            tokenProvider: vault,
            clientMetadata: .init(kind: .technicianIOS, version: "1.2.3+42"),
            correlationId: { UUID(uuidString: "11111111-1111-1111-1111-111111111111")! }
        )
        let request = await builder.build(path: "/me", contextHeaders: ["X-Service-Context": "ctx-1"])
        precondition(request.value(forHTTPHeaderField: "Authorization") == "Bearer secret")
        precondition(request.value(forHTTPHeaderField: "X-Service-Context") == "ctx-1")
        precondition(request.value(forHTTPHeaderField: "X-ServiceOS-Client-Kind") == "TECHNICIAN_IOS")
        precondition(request.value(forHTTPHeaderField: "X-ServiceOS-Client-Version") == "1.2.3+42")

        let counter = Counter()
        let contexts = ContextSelectionStore { await counter.increment() }
        await contexts.select(.init(contextId: "ctx-1", contextVersion: "v1"))
        await contexts.select(.init(contextId: "ctx-1", contextVersion: "v2"))
        let boundaryChanges = await counter.current()
        precondition(boundaryChanges == 1)

        let error = ServiceAPIError(
            status: 403,
            problem: nil,
            diagnostics: .init(headers: ["X-Correlation-Id": "corr-1"])
        )
        precondition(error.safeUserMessage == "无权访问或不存在")
        precondition(error.diagnostics.correlationId == "corr-1")
    }
}
