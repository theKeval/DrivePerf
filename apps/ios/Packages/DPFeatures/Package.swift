// swift-tools-version: 6.2
import PackageDescription

// One folder per PRD epic lives under Sources/DPFeatures (ADR-002 section 1); features see the design system
// and the Swift-facing bridge only, never DPPlatform, Firebase or RevenueCat.
let package = Package(
    name: "DPFeatures",
    platforms: [.iOS("26.0")],
    products: [.library(name: "DPFeatures", targets: ["DPFeatures"])],
    dependencies: [
        .package(path: "../DPDesignSystem"),
        .package(path: "../DPSharedBridge"),
    ],
    targets: [
        .target(
            name: "DPFeatures",
            dependencies: ["DPDesignSystem", "DPSharedBridge"]
        ),
    ]
)
