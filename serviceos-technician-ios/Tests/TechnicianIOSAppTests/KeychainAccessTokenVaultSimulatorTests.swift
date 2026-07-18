import Foundation
import XCTest
@testable import TechnicianIOS

final class KeychainAccessTokenVaultSimulatorTests: XCTestCase {
    func testExpiredAccessTokenKeepsRefreshTokenUntilRefreshOrLogout() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let vault = KeychainAccessTokenVault(
            service: "com.serviceos.technician.simulator-tests.\(UUID().uuidString)",
            account: "oidc-token-set",
            now: { now },
            expirySkew: 30
        )

        try await vault.store(.init(
            accessToken: "expired-access-token",
            refreshToken: "retained-refresh-token",
            expiresAt: now.addingTimeInterval(-1)
        ))

        let accessToken = await vault.currentAccessToken()
        let refreshToken = await vault.currentRefreshToken()
        XCTAssertNil(accessToken)
        XCTAssertEqual(refreshToken, "retained-refresh-token")

        await vault.clear()
        let clearedRefreshToken = await vault.currentRefreshToken()
        XCTAssertNil(clearedRefreshToken)
    }
}
