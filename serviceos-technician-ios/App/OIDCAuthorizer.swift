import AuthenticationServices
import Foundation
import UIKit

@MainActor
protocol OIDCAuthorizing: AnyObject {
    func authorize(url: URL, callbackScheme: String) async throws -> URL
}

/// 使用系统浏览器会话完成 OIDC，App 从不接触用户密码；回调只由当前 ASWebAuthenticationSession 接收。
@MainActor
final class SystemOIDCAuthorizer: NSObject, OIDCAuthorizing, ASWebAuthenticationPresentationContextProviding {
    private var currentSession: ASWebAuthenticationSession?

    func authorize(url: URL, callbackScheme: String) async throws -> URL {
        currentSession?.cancel()
        return try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(url: url, callbackURLScheme: callbackScheme) { [weak self] callbackURL, error in
                self?.currentSession = nil
                if let callbackURL {
                    continuation.resume(returning: callbackURL)
                } else if let sessionError = error as? ASWebAuthenticationSessionError,
                          sessionError.code == .canceledLogin {
                    continuation.resume(throwing: CancellationError())
                } else {
                    continuation.resume(throwing: error ?? URLError(.userAuthenticationRequired))
                }
            }
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false
            currentSession = session
            guard session.start() else {
                currentSession = nil
                continuation.resume(throwing: URLError(.cannotLoadFromNetwork))
                return
            }
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        let windowScene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        return windowScene?.windows.first(where: \.isKeyWindow) ?? ASPresentationAnchor()
    }
}
