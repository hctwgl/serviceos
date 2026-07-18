import CryptoKit
import Foundation
import Security

public enum OIDCConfigurationError: Error, Equatable {
    case randomGenerationFailed(OSStatus)
    case invalidReturnPath
    case invalidCallback
}

public struct PKCETransaction: Sendable, Equatable {
    public let state: String
    public let verifier: String
    public let challenge: String
    public let returnPath: String
}

public struct OIDCAuthorizationRequestFactory: Sendable {
    private let configuration: TechnicianIOSConfiguration

    public init(configuration: TechnicianIOSConfiguration) {
        self.configuration = configuration
    }

    public func begin(returnPath: String = "/technician/task-feed") throws -> (URL, PKCETransaction) {
        guard returnPath.hasPrefix("/"), !returnPath.hasPrefix("//") else {
            throw OIDCConfigurationError.invalidReturnPath
        }
        let verifier = try randomURLSafe(byteCount: 48)
        let state = try randomURLSafe(byteCount: 32)
        let challenge = Data(SHA256.hash(data: Data(verifier.utf8))).base64URLEncodedString()
        let redirectURI = "\(configuration.redirectScheme)://oauth/callback"
        var components = URLComponents(url: configuration.oidcIssuer.appending(path: "protocol/openid-connect/auth"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            .init(name: "client_id", value: configuration.oidcClientId),
            .init(name: "redirect_uri", value: redirectURI),
            .init(name: "response_type", value: "code"),
            .init(name: "scope", value: "openid profile"),
            .init(name: "state", value: state),
            .init(name: "code_challenge_method", value: "S256"),
            .init(name: "code_challenge", value: challenge),
        ]
        return (components.url!, .init(state: state, verifier: verifier, challenge: challenge, returnPath: returnPath))
    }

    public func validate(callbackURL: URL, transaction: PKCETransaction) throws -> String {
        guard callbackURL.scheme == configuration.redirectScheme,
              callbackURL.host == "oauth",
              callbackURL.path == "/callback",
              let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false),
              components.queryItems?.first(where: { $0.name == "state" })?.value == transaction.state,
              let code = components.queryItems?.first(where: { $0.name == "code" })?.value,
              !code.isEmpty else {
            throw OIDCConfigurationError.invalidCallback
        }
        return code
    }

    private func randomURLSafe(byteCount: Int) throws -> String {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        guard status == errSecSuccess else { throw OIDCConfigurationError.randomGenerationFailed(status) }
        return Data(bytes).base64URLEncodedString()
    }
}

private extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
