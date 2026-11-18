import DPSharedBridge
import Foundation
import Observation
import SwiftUI

@MainActor @Observable
public final class DisplaySettingsModel {
    public private(set) var preferences: DisplayPreferences
    public private(set) var cars: [SettingsCar] = []
    public private(set) var isSaving = false
    public var saveFailed = false
    private let storage: (any DisplaySettingsStoring)?
    public init(storage: (any DisplaySettingsStoring)? = nil) {
        self.storage = storage
        preferences = storage?.current ?? DisplayPreferences()
    }
    public var catalogue: ProvenanceCatalog { storage?.catalogue(units: preferences.units) ?? ProvenanceCatalog() }
    public var regionalUnits: DisplayUnits { storage?.regionalUnits ?? .metric }
    public var colorScheme: ColorScheme? {
        switch preferences.appearance { case .system: nil; case .light: .light; case .dark: .dark }
    }
    public var summary: String { "\(preferences.units.rawValue.capitalized) · \(preferences.appearance.rawValue.capitalized)" }
    public var carName: String? { cars.first { $0.id == preferences.defaultVehicleId }?.name }
    public func observe() async {
        guard let storage else { return }
        for await value in storage.updates() { preferences = value }
    }
    public func loadCars() async { cars = await storage?.cars() ?? [] }
    public func change(_ update: (inout DisplayPreferences) -> Void) async {
        guard !isSaving, let storage else { return }
        var next = preferences
        update(&next)
        guard next != preferences else { return }
        isSaving = true
        if await storage.save(next) { preferences = storage.current } else { saveFailed = true }
        isSaving = false
    }
    public func trackAutomaticTripsChange() { storage?.settingChanged("auto_detect") }
    public func metric(_ key: String) -> MetricInfo {
        catalogue[key] ?? MetricInfo(key: key, provenance: .derived, meaning: "", unit: "km", decimals: 1, perSi: 0.001)
    }
    public func distance(_ metres: Double) -> String { "\(metric("distance").format(metres)) \(metric("distance").unit ?? "")" }
}
