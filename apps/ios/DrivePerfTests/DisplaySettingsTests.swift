import DPSharedBridge
import SwiftUI
import XCTest
@testable import DPFeatures
@testable import DrivePerf

@MainActor
final class DisplaySettingsTests: XCTestCase {
    func testRegionalDefaultsAndSharedFormatting() {
        XCTAssertEqual(CoreDisplaySettings(region: "CA").current.units, .metric)
        XCTAssertEqual(CoreDisplaySettings(region: "US").current.units, .imperial)
        let catalog = CoreDisplaySettings().catalogue(units: .imperial)
        XCTAssertEqual(catalog["distance"]?.format(1609.344), "1.0")
        XCTAssertEqual(catalog["distance"]?.unit, "mi")
        XCTAssertEqual(catalog["maxSpeed"]?.format(26.8224), "60")
        XCTAssertEqual(catalog["maxSpeed"]?.unit, "mph")
        XCTAssertEqual(catalog["elevation"]?.format(30.48), "100")
        XCTAssertEqual(catalog["elevation"]?.unit, "ft")
        XCTAssertEqual(catalog["longG"]?.format(0.424), "0.42")
        XCTAssertEqual(catalog["longG"]?.unit, "g")
    }

    func testImmediatePreferencesAndFailureKeepsTheSavedChoice() async {
        let storage = SettingsFake()
        let model = DisplaySettingsModel(storage: storage)
        await model.change { $0.units = .imperial; $0.appearance = .light; $0.batterySaverRecording = true }
        XCTAssertEqual(model.distance(1609.344), "1.0 mi")
        XCTAssertEqual(model.colorScheme, .light)
        XCTAssertTrue(model.preferences.batterySaverRecording)
        XCTAssertEqual(DisplaySettingsModel(storage: storage).preferences, model.preferences)
        storage.succeeds = false
        await model.change { $0.units = .metric }
        XCTAssertTrue(model.saveFailed)
        XCTAssertEqual(model.preferences.units, .imperial)
        storage.availableCars = [SettingsCar(id: "car", name: "My car")]
        await model.loadCars()
        XCTAssertEqual(model.cars.map(\.id), ["car"])
        model.trackAutomaticTripsChange()
        XCTAssertEqual(storage.keys, ["auto_detect"])
    }

    func testSettingsScreensRenderBothThemesAndLargeText() async throws {
        let storage = SettingsFake()
        let display = DisplaySettingsModel(storage: storage)
        let recording = RecordingSettingsViewModel(
            preferences: MemoryPreferences(), permissions: FakePermissions(), refreshCapture: {}, display: display)
        for scheme in [ColorScheme.light, .dark] {
            for large in [false, true] {
                let screens: [(String, AnyView)] = [
                    ("hub", AnyView(SettingsHubView(display: display, recording: recording))),
                    ("units", AnyView(UnitsAndDisplayView(model: display))),
                    ("recording-empty", AnyView(RecordingSettingsView(model: recording))),
                ]
                for (name, screen) in screens {
                    let host = UIHostingController(rootView: NavigationStack { screen }
                        .environment(\.colorScheme, scheme)
                        .environment(\.dynamicTypeSize, large ? .accessibility3 : .large))
                    host.view.frame = CGRect(x: 0, y: 0, width: 375, height: 812)
                    let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
                    let previous = scene.windows.first { $0.isKeyWindow }
                    let window = UIWindow(windowScene: scene)
                    window.frame = host.view.frame
                    window.rootViewController = host
                    window.makeKeyAndVisible()
                    try await Task.sleep(for: .milliseconds(100))
                    host.view.layoutIfNeeded()
                    let image = UIGraphicsImageRenderer(bounds: host.view.bounds).image { context in
                        host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
                    }
                    window.isHidden = true
                    previous?.makeKeyAndVisible()
                    XCTAssertEqual(image.size.width, 375)
                    let pixels = try XCTUnwrap(image.cgImage?.dataProvider?.data) as Data
                    XCTAssertGreaterThan(Set(stride(from: 0, to: pixels.count, by: 128).map { pixels[$0] }).count, 8,
                                         "The screen must contain rendered content, not a blank hosting view")
                    let attachment = XCTAttachment(image: image)
                    attachment.name = "DRIVE-153-\(name)-\(scheme)-large-\(large)"
                    attachment.lifetime = .keepAlways
                    add(attachment)
                }
            }
        }
    }
}

@MainActor
private final class SettingsFake: DisplaySettingsStoring {
    var current = DisplayPreferences()
    var regionalUnits: DisplayUnits { .metric }
    var succeeds = true
    var keys: [String] = []
    var availableCars: [SettingsCar] = []
    func updates() -> AsyncStream<DisplayPreferences> { AsyncStream { $0.yield(current); $0.finish() } }
    func save(_ preferences: DisplayPreferences) async -> Bool {
        if succeeds { current = preferences }
        return succeeds
    }
    func cars() async -> [SettingsCar] { availableCars }
    func catalogue(units: DisplayUnits) -> ProvenanceCatalog { CoreDisplaySettings().catalogue(units: units) }
    func settingChanged(_ key: String) { keys.append(key) }
}
