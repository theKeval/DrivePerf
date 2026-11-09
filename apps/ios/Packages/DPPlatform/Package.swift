// swift-tools-version: 6.2
import PackageDescription

// Apple-framework and Firebase adapters that implement the shared ports (ADR-002 section 2): Core Location,
// Core Motion, BGTaskScheduler, ActivityKit. Content arrives with DRIVE-67.
let package = Package(
    name: "DPPlatform",
    platforms: [.iOS("26.0")],
    products: [.library(name: "DPPlatform", targets: ["DPPlatform"])],
    dependencies: [.package(path: "../DPSharedBridge")],
    targets: [.target(name: "DPPlatform", dependencies: ["DPSharedBridge"])]
)
