// swift-tools-version: 6.2
import PackageDescription

// Design tokens (docs/design/system/tokens.json, iOS columns) and shared components. The widget extension may
// depend on this package too (ADR-002 section 2), so it imports SwiftUI only: no Kotlin, no Core Location.
let package = Package(
    name: "DPDesignSystem",
    platforms: [.iOS("26.0")],
    products: [.library(name: "DPDesignSystem", targets: ["DPDesignSystem"])],
    targets: [.target(name: "DPDesignSystem", resources: [.process("Resources")])]
)
