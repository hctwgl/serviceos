import Foundation

public struct AccessTokenSnapshot: Sendable, Equatable {
    public let accessToken: String
    public let expiresAt: Date

    public init(accessToken: String, expiresAt: Date) {
        precondition(!accessToken.isEmpty, "Access Token 不得为空")
        self.accessToken = accessToken
        self.expiresAt = expiresAt
    }
}

public protocol AccessTokenProviding: Sendable {
    func currentAccessToken() async -> AccessTokenSnapshot?
}

/// 仅用于测试和未登录启动阶段；生产 iOS 必须由后续 Keychain 实现替换。
public actor MemoryAccessTokenVault: AccessTokenProviding {
    private var snapshot: AccessTokenSnapshot?
    private let now: @Sendable () -> Date
    private let expirySkew: TimeInterval

    public init(now: @escaping @Sendable () -> Date = Date.init, expirySkew: TimeInterval = 30) {
        self.now = now
        self.expirySkew = expirySkew
    }

    public func store(_ next: AccessTokenSnapshot) {
        snapshot = next
    }

    public func clear() {
        snapshot = nil
    }

    public func currentAccessToken() -> AccessTokenSnapshot? {
        guard let snapshot, snapshot.expiresAt.timeIntervalSince(now()) > expirySkew else {
            self.snapshot = nil
            return nil
        }
        return snapshot
    }
}
