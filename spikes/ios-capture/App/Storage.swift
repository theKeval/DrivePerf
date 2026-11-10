import CaptureCore
import Foundation

/// Timestamps-only wake log at protection level none, so it is writable before first unlock
/// after a reboot (A3 §1.5). It must never hold a location.
enum WakeLog {
    private static let url: URL = {
        let directory = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask)[0]
        return directory.appendingPathComponent("wake.jsonl")
    }()

    private static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }()

    static func append(kind: String, detail: String) {
        let entry = WakeEntry(at: Date(), kind: kind, detail: detail)
        guard var line = try? encoder.encode(entry) else { return }
        line.append(0x0A)
        let manager = FileManager.default
        if !manager.fileExists(atPath: url.path) {
            manager.createFile(atPath: url.path, contents: nil, attributes: [.protectionKey: FileProtectionType.none])
        }
        do {
            let handle = try FileHandle(forWritingTo: url)
            defer { try? handle.close() }
            try handle.seekToEnd()
            try handle.write(contentsOf: line)
        } catch {
            NSLog("WakeLog write failed: \(error)")
        }
    }

    static func entries() -> [WakeEntry] {
        guard let data = try? Data(contentsOf: url) else { return [] }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return data.split(separator: 0x0A).compactMap { try? decoder.decode(WakeEntry.self, from: Data($0)) }
    }
}

enum TripStore {
    private static let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    static let tripsRoot = documents.appendingPathComponent("trips", isDirectory: true)
    static let exportsRoot = documents.appendingPathComponent("exports", isDirectory: true)
    static let summaryURL = documents.appendingPathComponent("summary.json")
    private static let parkedURL = documents.appendingPathComponent("parked.json")
    private static let installsURL = documents.appendingPathComponent("installs.json")
    private static let unlockSentinelURL = documents.appendingPathComponent("unlock.sentinel")

    private static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .millisecondsSince1970
        return encoder
    }()

    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .millisecondsSince1970
        return decoder
    }()

    /// The sentinel is unreadable only before the first unlock after a reboot, unlike
    /// UIApplication.isProtectedDataAvailable, which is false every time the phone locks.
    static func hasBeenUnlockedSinceBoot() -> Bool {
        let manager = FileManager.default
        if manager.fileExists(atPath: unlockSentinelURL.path) {
            return (try? Data(contentsOf: unlockSentinelURL)) != nil
        }
        return manager.createFile(
            atPath: unlockSentinelURL.path, contents: Data([1]),
            attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
    }

    static func directory(for id: UUID) -> URL {
        tripsRoot.appendingPathComponent(id.uuidString, isDirectory: true)
    }

    static func save(_ trip: TripRecord) {
        let directory = directory(for: trip.id)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try encoder.encode(trip).write(to: directory.appendingPathComponent("meta.json"), options: .atomic)
        } catch {
            NSLog("TripStore save failed: \(error)")
        }
    }

    static func loadAll() -> [TripRecord] {
        let directories =
            (try? FileManager.default.contentsOfDirectory(at: tripsRoot, includingPropertiesForKeys: nil)) ?? []
        return
            directories
            .compactMap { try? Data(contentsOf: $0.appendingPathComponent("meta.json")) }
            .compactMap { try? decoder.decode(TripRecord.self, from: $0) }
            .sorted { $0.wakeAt > $1.wakeAt }
    }

    /// A rejected candidate keeps its meta row for the false-wake count, not its samples.
    static func deleteSamples(for id: UUID) {
        for name in ["gps.csv", "imu.csv", "backfill.csv"] {
            try? FileManager.default.removeItem(at: directory(for: id).appendingPathComponent(name))
        }
    }

    static func loadParkedSpot() -> GeoPoint? {
        guard let data = try? Data(contentsOf: parkedURL) else { return nil }
        return try? decoder.decode(GeoPoint.self, from: data)
    }

    static func saveParkedSpot(_ point: GeoPoint) {
        try? encoder.encode(point).write(to: parkedURL, options: .atomic)
    }

    static func loadInstalls() -> [InstallEntry] {
        guard let data = try? Data(contentsOf: installsURL) else { return [] }
        return (try? decoder.decode([InstallEntry].self, from: data)) ?? []
    }

    static func saveInstalls(_ installs: [InstallEntry]) {
        try? encoder.encode(installs).write(to: installsURL, options: .atomic)
    }

    /// Writes the coordinate-free summary that can be pulled off the phone for the results doc.
    static func writeSummary(trips: [TripRecord], installs: [InstallEntry]) {
        let summary = SpikeSummary(
            generatedAt: Date(), installs: installs, trips: trips.map(TripSummary.init), wakes: WakeLog.entries())
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        try? encoder.encode(summary).write(to: summaryURL, options: .atomic)
    }

    /// Builds the raw JSON and GPX exports. These hold real coordinates and stay off the repo.
    static func export(_ trip: TripRecord) throws -> [URL] {
        try FileManager.default.createDirectory(at: exportsRoot, withIntermediateDirectories: true)
        let directory = directory(for: trip.id)

        func table(_ name: String, _ columns: [String]) -> SampleTable {
            let csv = (try? String(contentsOf: directory.appendingPathComponent(name), encoding: .utf8)) ?? ""
            return SampleTable(columns: columns, rows: CSVColumns.rows(csv: csv, columns: columns.count))
        }

        let gps = table("gps.csv", CSVColumns.gps)
        let fixture = TripFixture(
            trip: trip, gps: gps, imu: table("imu.csv", CSVColumns.imu),
            backfill: table("backfill.csv", CSVColumns.backfill))

        let stem = "RAW-DO-NOT-COMMIT-\(trip.id.uuidString.prefix(8))"
        let jsonURL = exportsRoot.appendingPathComponent("\(stem).json")
        let gpxURL = exportsRoot.appendingPathComponent("\(stem).gpx")
        try encoder.encode(fixture).write(to: jsonURL, options: .atomic)
        try GPXExporter.gpx(name: stem, gps: gps).write(to: gpxURL, atomically: true, encoding: .utf8)
        return [jsonURL, gpxURL]
    }
}

/// Reads the embedded profile so the app can warn before the 7-day Personal Team expiry.
struct ProvisioningInfo {
    var createdAt: Date?
    var expiresAt: Date?

    static func read() -> ProvisioningInfo? {
        guard let url = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
            let data = try? Data(contentsOf: url),
            let start = data.range(of: Data("<?xml".utf8)),
            let end = data.range(of: Data("</plist>".utf8)),
            let plist = try? PropertyListSerialization.propertyList(
                from: data[start.lowerBound..<end.upperBound], format: nil) as? [String: Any]
        else { return nil }
        return ProvisioningInfo(createdAt: plist["CreationDate"] as? Date, expiresAt: plist["ExpirationDate"] as? Date)
    }
}
