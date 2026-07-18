import Foundation
import Security
import ServiceOSIOSCore

public enum KeychainVaultError: Error, Equatable {
    case writeFailed(OSStatus)
}

public struct OIDCTokenSet: Sendable, Equatable {
    public let accessToken: String
    public let refreshToken: String?
    public let expiresAt: Date

    public init(accessToken: String, refreshToken: String?, expiresAt: Date) {
        precondition(!accessToken.isEmpty, "Access Token 不得为空")
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.expiresAt = expiresAt
    }
}

public protocol OIDCTokenPersisting: AccessTokenProviding {
    func store(_ tokenSet: OIDCTokenSet) async throws
    func currentRefreshToken() async -> String?
    func clear() async
}

/// 生产 Token 只进入本机 Keychain，并使用 ThisDeviceOnly 等级阻止随备份迁移到其他设备。
/// 读取异常一律失败关闭为未登录；调用方不得把 OSStatus、Token 或账号信息写入业务日志。
public actor KeychainAccessTokenVault: OIDCTokenPersisting {
    private struct StoredToken: Codable {
        let accessToken: String
        let refreshToken: String?
        let expiresAt: Date
    }

    private let service: String
    private let account: String
    private let now: @Sendable () -> Date
    private let expirySkew: TimeInterval

    public init(
        service: String,
        account: String,
        now: @escaping @Sendable () -> Date = Date.init,
        expirySkew: TimeInterval = 30
    ) {
        precondition(!service.isEmpty && !account.isEmpty, "Keychain service/account 不得为空")
        self.service = service
        self.account = account
        self.now = now
        self.expirySkew = expirySkew
    }

    public func store(_ snapshot: AccessTokenSnapshot) throws {
        try store(.init(accessToken: snapshot.accessToken, refreshToken: nil, expiresAt: snapshot.expiresAt))
    }

    public func store(_ tokenSet: OIDCTokenSet) throws {
        let data = try JSONEncoder().encode(StoredToken(
            accessToken: tokenSet.accessToken,
            refreshToken: tokenSet.refreshToken,
            expiresAt: tokenSet.expiresAt
        ))
        SecItemDelete(baseQuery() as CFDictionary)
        var query = baseQuery()
        query[kSecValueData as String] = data
#if os(iOS)
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
#else
        // macOS 仅作为仓库内 Swift smoke 宿主，不接受 iOS 的 ThisDeviceOnly 参数；生产 iOS 编译分支
        // 始终使用不可随备份迁移的等级，并由 Simulator/真机测试再次证明。
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
#endif
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else { throw KeychainVaultError.writeFailed(status) }
    }

    public func clear() {
        SecItemDelete(baseQuery() as CFDictionary)
    }

    public func currentAccessToken() -> AccessTokenSnapshot? {
        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess,
              let data = result as? Data,
              let stored = try? JSONDecoder().decode(StoredToken.self, from: data) else { return nil }
        // Access Token 到期不等于整个 OIDC 会话失效。这里必须保留同一 Keychain 项中的 Refresh Token，
        // 让 App 冷启动时可以受控刷新；只有 refresh 明确失败或用户注销时才清理完整 TokenSet。
        guard stored.expiresAt.timeIntervalSince(now()) > expirySkew else { return nil }
        return AccessTokenSnapshot(accessToken: stored.accessToken, expiresAt: stored.expiresAt)
    }

    public func currentRefreshToken() -> String? {
        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data,
              let stored = try? JSONDecoder().decode(StoredToken.self, from: data) else { return nil }
        return stored.refreshToken
    }

    private func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}
