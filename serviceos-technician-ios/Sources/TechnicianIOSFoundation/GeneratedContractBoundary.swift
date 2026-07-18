import ServiceOSCoreClient
import ServiceOSDesignTokens

/// App 必须真实链接同源生成客户端和 Token 模块；此边界阻止只在文档里声明依赖。
public enum GeneratedContractBoundary {
    public static var apiConfigurationType: Any.Type { ServiceOSCoreClientAPIConfiguration.self }
    public static var primaryActionColor: String { ServiceOSDesignTokens.Color.actionPrimary }
    public static var standardSpacing: Double { ServiceOSDesignTokens.Spacing.lg }
}
