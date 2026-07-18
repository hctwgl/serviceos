// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "ServiceOSIOSCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "ServiceOSIOSCore", targets: ["ServiceOSIOSCore"]),
    ],
    targets: [
        .target(
            name: "ServiceOSIOSCore",
            path: "Sources/ServiceOSIOSCore"
        ),
    ]
)
