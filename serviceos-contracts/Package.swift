// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "ServiceOSGeneratedContracts",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "ServiceOSCoreClient", targets: ["ServiceOSCoreClient"]),
        .library(name: "ServiceOSDesignTokens", targets: ["ServiceOSDesignTokens"]),
    ],
    targets: [
        // 两个目录都由仓库生成器产生；Xcode 门禁在解析 Package 前先执行生成，禁止手改生成物。
        .target(
            name: "ServiceOSCoreClient",
            path: "target/generated-clients/swift6/Sources/ServiceOSCoreClient"
        ),
        .target(
            name: "ServiceOSDesignTokens",
            path: "target/generated-design-tokens/swift",
            sources: ["ServiceOSDesignTokens.swift"]
        ),
    ]
)
