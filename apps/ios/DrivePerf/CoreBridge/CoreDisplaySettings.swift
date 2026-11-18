import DPSharedBridge
import DrivePerfShared
import Foundation

@MainActor
final class CoreDisplaySettings: DisplaySettingsStoring {
    var onChange: (() -> Void)?
    private var repository: (any SettingsRepository)?
    private var analytics: (any DrivePerfShared.Analytics)?
    private let region: String
    private var listeners: [UUID: AsyncStream<DisplayPreferences>.Continuation] = [:]
    private var task: Task<Void, Never>?
    private(set) var current: DisplayPreferences

    init(region: String = Locale.current.region?.identifier ?? "") {
        self.region = region
        current = DisplayPreferences(units: Self.units(UnitSystem.companion.defaultFor(region: region)))
    }
    var regionalUnits: DisplayUnits { Self.units(UnitSystem.companion.defaultFor(region: region)) }

    func attach(_ services: CoreServices) {
        repository = services.settings
        analytics = services.analytics
        current = swift(services.settings.state.value)
        let flow = UncheckedSendable(services.settings.state)
        task?.cancel()
        task = Task { [weak self] in
            for await value in flow.value {
                guard let self else { return }
                current = swift(value)
                onChange?()
                listeners.values.forEach { $0.yield(current) }
            }
        }
    }

    func updates() -> AsyncStream<DisplayPreferences> {
        let id = UUID()
        return AsyncStream { continuation in
            listeners[id] = continuation
            continuation.yield(current)
            continuation.onTermination = { [weak self] _ in
                Task { @MainActor in self?.listeners.removeValue(forKey: id) }
            }
        }
    }
    func save(_ preferences: DisplayPreferences) async -> Bool {
        guard let repository else { return false }
        let previous = repository.state.value
        let units: UnitSystem? = preferences.units == current.units
            ? previous.units : (preferences.units == .metric ? .metric : .imperial)
        let next = Settings(
            units: units, appearance: appearance(preferences.appearance),
            batterySaverRecording: preferences.batterySaverRecording,
            defaultVehicleId: preferences.defaultVehicleId, dynamicColour: previous.dynamicColour)
        do {
            let saved = try await Self.save(UncheckedSendable(repository), settings: UncheckedSendable(next))
            if saved {
                current = swift(repository.state.value)
                onChange?()
                listeners.values.forEach { $0.yield(current) }
            }
            return saved
        }
        catch { return false }
    }
    func cars() async -> [SettingsCar] {
        guard let repository else { return [] }
        do { return try await Self.cars(UncheckedSendable(repository)) }
        catch { return [] }
    }
    func catalogue(units: DisplayUnits) -> ProvenanceCatalog {
        KotlinMapping.provenanceCatalog(units: units == .metric ? .metric : .imperial)
    }
    func settingChanged(_ key: String) {
        guard let setting = SettingKey.allCases.first(where: { $0.key == key }) else { return }
        analytics?.track(event: SettingChanged(key: setting))
    }
    private nonisolated static func save(
        _ repository: UncheckedSendable<any SettingsRepository>, settings: UncheckedSendable<Settings>
    ) async throws -> Bool {
        try await repository.value.update(settings: settings.value).boolValue
    }
    private nonisolated static func cars(_ repository: UncheckedSendable<any SettingsRepository>) async throws -> [SettingsCar] {
        try await repository.value.vehicles().map { SettingsCar(id: $0.id, name: $0.name) }
    }
    private func swift(_ value: Settings) -> DisplayPreferences {
        DisplayPreferences(
            units: Self.units(value.effectiveUnits(region: region)),
            appearance: AppAppearance(rawValue: value.appearance.name.lowercased()) ?? .system,
            batterySaverRecording: value.batterySaverRecording, defaultVehicleId: value.defaultVehicleId)
    }
    private static func units(_ value: UnitSystem) -> DisplayUnits { value == .imperial ? .imperial : .metric }
    private func appearance(_ value: AppAppearance) -> Appearance {
        switch value { case .system: .system; case .light: .light; case .dark: .dark }
    }
}
