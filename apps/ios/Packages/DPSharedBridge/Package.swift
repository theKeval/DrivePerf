// swift-tools-version: 6.2
import PackageDescription

// Swift-facing contract with the Kotlin core (ADR-002 section 8): Swift mirrors of the shared capture types and
// ports, the TripRecorder contract and the app ports, which DPPlatform implements and DPFeatures codes against.
// The Kotlin framework "DrivePerfShared" is imported only by the app target, which adapts these to the Kotlin
// protocols (apps/ios/DrivePerf/CoreBridge/); that way the framework is always built (Run Script phase) before
// any Swift that imports it.
let package = Package(
    name: "DPSharedBridge",
    platforms: [.iOS("26.0")],
    products: [.library(name: "DPSharedBridge", targets: ["DPSharedBridge"])],
    targets: [.target(name: "DPSharedBridge")]
)
