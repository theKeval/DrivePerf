import Foundation

public enum DisplayUnits: String, CaseIterable, Sendable { case metric, imperial }
public enum AppAppearance: String, CaseIterable, Sendable { case system, light, dark }

public struct DisplayPreferences: Equatable, Sendable {
    public var units: DisplayUnits
    public var appearance: AppAppearance
    public var batterySaverRecording: Bool
    public var defaultVehicleId: String?
    public init(units: DisplayUnits = .metric, appearance: AppAppearance = .system,
                batterySaverRecording: Bool = false, defaultVehicleId: String? = nil) {
        self.units = units
        self.appearance = appearance
        self.batterySaverRecording = batterySaverRecording
        self.defaultVehicleId = defaultVehicleId
    }
}

public struct SettingsCar: Identifiable, Equatable, Sendable {
    public var id: String
    public var name: String
    public init(id: String, name: String) { self.id = id; self.name = name }
}

/// Native seam over the shared settings repository and DisplayUnit formatter.
@MainActor
public protocol DisplaySettingsStoring: AnyObject {
    var current: DisplayPreferences { get }
    var regionalUnits: DisplayUnits { get }
    func updates() -> AsyncStream<DisplayPreferences>
    func save(_ preferences: DisplayPreferences) async -> Bool
    func cars() async -> [SettingsCar]
    func catalogue(units: DisplayUnits) -> ProvenanceCatalog
    func settingChanged(_ key: String)
}
