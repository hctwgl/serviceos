import Foundation
import ServiceOSIOSCore

public enum TechnicianAppEnvironment: String, CaseIterable, Sendable {
    case local
    case development
    case test
    case staging
    case production
}

public enum TechnicianConfigurationError: Error, Equatable {
    case missing(String)
    case unsupportedEnvironment(String)
    case insecureURL(String)
    case invalidClientVersion
    case invalidRedirectScheme
}

public struct TechnicianIOSConfiguration: Sendable, Equatable {
    public let environment: TechnicianAppEnvironment
    public let apiBaseURL: URL
    public let oidcIssuer: URL
    public let oidcClientId: String
    public let redirectScheme: String
    public let clientMetadata: IOSClientMetadata

    public static func resolve(_ values: [String: String]) throws -> TechnicianIOSConfiguration {
        func required(_ key: String) throws -> String {
            guard let value = values[key]?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else {
                throw TechnicianConfigurationError.missing(key)
            }
            return value
        }

        let rawEnvironment = try required("SERVICEOS_ENV")
        guard let environment = TechnicianAppEnvironment(rawValue: rawEnvironment) else {
            throw TechnicianConfigurationError.unsupportedEnvironment(rawEnvironment)
        }
        let apiBaseURL = try secureURL(required("SERVICEOS_API_BASE_URL"), environment: environment)
        let oidcIssuer = try secureURL(required("SERVICEOS_OIDC_ISSUER"), environment: environment)
        let clientId = try required("SERVICEOS_OIDC_CLIENT_ID")
        let redirectScheme = try required("SERVICEOS_OIDC_REDIRECT_SCHEME")
        guard redirectScheme.range(of: #"^[a-z][a-z0-9+.-]{2,63}$"#, options: .regularExpression) != nil else {
            throw TechnicianConfigurationError.invalidRedirectScheme
        }
        let version = try required("SERVICEOS_CLIENT_VERSION")
        guard version.range(of: #"^[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]{1,32})?$"#, options: .regularExpression) != nil else {
            throw TechnicianConfigurationError.invalidClientVersion
        }
        return TechnicianIOSConfiguration(
            environment: environment,
            apiBaseURL: apiBaseURL,
            oidcIssuer: oidcIssuer,
            oidcClientId: clientId,
            redirectScheme: redirectScheme,
            clientMetadata: IOSClientMetadata(kind: .technicianIOS, version: version)
        )
    }

    private static func secureURL(_ rawValue: String, environment: TechnicianAppEnvironment) throws -> URL {
        guard let url = URL(string: rawValue), let scheme = url.scheme, url.host != nil else {
            throw TechnicianConfigurationError.insecureURL(rawValue)
        }
        let localHTTP = environment == .local && scheme == "http" && ["localhost", "127.0.0.1"].contains(url.host)
        guard scheme == "https" || localHTTP else { throw TechnicianConfigurationError.insecureURL(rawValue) }
        return url
    }
}
