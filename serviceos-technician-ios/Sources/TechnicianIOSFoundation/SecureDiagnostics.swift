import Foundation

public enum SecureDiagnostics {
    private static let sensitiveKeys = [
        "authorization", "token", "contact", "address", "vin", "photo", "file", "form", "payload",
    ]

    /// 只允许低基数诊断事实进入日志；Token、联系人、地址、VIN、照片路径和表单值统一脱敏。
    public static func redacted(_ fields: [String: String]) -> [String: String] {
        fields.reduce(into: [:]) { result, pair in
            let normalized = pair.key.lowercased()
            result[pair.key] = sensitiveKeys.contains(where: normalized.contains) ? "<redacted>" : pair.value
        }
    }
}
