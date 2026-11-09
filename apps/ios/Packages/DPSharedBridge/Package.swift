// swift-tools-version: 6.2
import PackageDescription

// Swift-facing contract with the Kotlin core (ADR-002 section 8): plain Swift types and protocols that
// features code against. The Kotlin framework "DrivePerfShared" is imported only by the app target, which
// supplies the implementations (apps/ios/DrivePerf/SharedCoreHelloProvider.swift); that way the framework
// is always built (Run Script phase) before any Swift that imports it. DRIVE-67 grows this package into the
// full set of *Bridge protocols for natively implemented ports.
let package = Package(
    name: "DPSharedBridge",
    platforms: [.iOS("26.0")],
    products: [.library(name: "DPSharedBridge", targets: ["DPSharedBridge"])],
    targets: [.target(name: "DPSharedBridge")]
)
