/// What the scaffold screen shows. A plain Swift type, so features never see Kotlin types directly.
public struct HelloContent: Equatable, Sendable {
    public let title: String
    public let subtitle: String

    public init(title: String, subtitle: String) {
        self.title = title
        self.subtitle = subtitle
    }
}

/// Source of the scaffold content. The app target implements it by calling the shared Kotlin core.
public protocol HelloProviding: Sendable {
    func hello() -> HelloContent
}
