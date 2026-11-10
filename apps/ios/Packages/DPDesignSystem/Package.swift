// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "DPDesignSystem",
    platforms: [.iOS("26.0")],
    products: [.library(name: "DPDesignSystem", targets: ["DPDesignSystem"])],
    targets: [.target(name: "DPDesignSystem")]
)
