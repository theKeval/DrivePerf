// swift-tools-version: 6.2
import PackageDescription

// Apple-framework adapters that implement the shared ports (ADR-002 section 2): Core Location, Core Motion, file
// storage, permissions and local notifications. No business logic: these deliver samples and system state only
// (pipeline-and-scoring.md section 0.1). Features never import this package; the app target wires it in.
let package = Package(
    name: "DPPlatform",
    platforms: [.iOS("26.0")],
    products: [.library(name: "DPPlatform", targets: ["DPPlatform"])],
    dependencies: [.package(path: "../DPSharedBridge")],
    targets: [.target(name: "DPPlatform", dependencies: ["DPSharedBridge"])]
)
