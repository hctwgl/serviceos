// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "TechnicianIOSFoundation",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "TechnicianIOSFoundation", targets: ["TechnicianIOSFoundation"]),
    ],
    dependencies: [
        .package(path: "../serviceos-ios-core"),
        .package(path: "../serviceos-contracts"),
    ],
    targets: [
        .target(
            name: "TechnicianIOSFoundation",
            dependencies: [
                .product(name: "ServiceOSIOSCore", package: "serviceos-ios-core"),
                .product(name: "ServiceOSCoreClient", package: "serviceos-contracts"),
                .product(name: "ServiceOSDesignTokens", package: "serviceos-contracts"),
            ],
            path: "Sources/TechnicianIOSFoundation"
        ),
    ]
)
