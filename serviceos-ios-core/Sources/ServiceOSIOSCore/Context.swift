public struct ServiceContext: Sendable, Equatable {
    public let contextId: String
    public let contextVersion: String
    public let scopeRef: String?

    public init(contextId: String, contextVersion: String, scopeRef: String? = nil) {
        precondition(!contextId.isEmpty && !contextVersion.isEmpty, "服务上下文缺少稳定标识或版本")
        self.contextId = contextId
        self.contextVersion = contextVersion
        self.scopeRef = scopeRef
    }
}

public actor ContextSelectionStore {
    private var selected: ServiceContext?
    private let onBoundaryChanged: @Sendable () async -> Void

    public init(onBoundaryChanged: @escaping @Sendable () async -> Void) {
        self.onBoundaryChanged = onBoundaryChanged
    }

    public func current() -> ServiceContext? { selected }

    public func select(_ next: ServiceContext) async {
        if let selected, selected.contextId != next.contextId || selected.contextVersion != next.contextVersion {
            await onBoundaryChanged()
        }
        selected = next
    }

    public func clear() async {
        if selected != nil { await onBoundaryChanged() }
        selected = nil
    }
}
