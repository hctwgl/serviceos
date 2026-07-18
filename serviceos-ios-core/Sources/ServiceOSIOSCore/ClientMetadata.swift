import Foundation

public struct IOSClientMetadata: Sendable, Equatable {
    public enum Kind: String, Sendable {
        case technicianIOS = "TECHNICIAN_IOS"
    }

    public let kind: Kind
    public let version: String

    public init(kind: Kind, version: String) {
        precondition(version.range(
            of: #"^[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]{1,32})?$"#,
            options: .regularExpression
        ) != nil, "iOS clientVersion 必须是受支持的语义版本")
        self.kind = kind
        self.version = version
    }
}
