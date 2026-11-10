import Foundation

public enum CSVColumns {
    public static let gps = ["t", "lat", "lon", "alt", "hAcc", "vAcc", "speed", "speedAcc", "course"]
    public static let imu = ["t", "ax", "ay", "az", "rx", "ry", "rz", "gx", "gy", "gz", "qx", "qy", "qz", "qw"]
    public static let backfill = ["t", "ax", "ay", "az"]

    /// Parses a numeric CSV body (no header) into rows, skipping torn lines from a killed process.
    public static func rows(csv: String, columns: Int) -> [[Double]] {
        csv.split(separator: "\n").compactMap { line in
            let values = line.split(separator: ",").compactMap { Double($0) }
            return values.count == columns ? values : nil
        }
    }
}

public struct SampleTable: Codable, Equatable, Sendable {
    public var columns: [String]
    public var rows: [[Double]]

    public init(columns: [String], rows: [[Double]]) {
        self.columns = columns
        self.rows = rows
    }
}

/// Full-resolution trip export for later shared-core fixtures. Contains REAL coordinates:
/// it must be privacy-trimmed before it goes anywhere near the repo (ADR-008).
public struct TripFixture: Codable, Sendable {
    public var trip: TripRecord
    public var gps: SampleTable
    public var imu: SampleTable
    public var backfill: SampleTable

    public init(trip: TripRecord, gps: SampleTable, imu: SampleTable, backfill: SampleTable) {
        self.trip = trip
        self.gps = gps
        self.imu = imu
        self.backfill = backfill
    }
}

public enum GPXExporter {
    public static func gpx(name: String, gps: SampleTable) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        var out = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- RAW real location data. Never commit (ADR-008). -->
            <gpx version="1.1" creator="CaptureSpike" xmlns="http://www.topografix.com/GPX/1/1">
            <trk><name>\(name)</name><trkseg>

            """
        for row in gps.rows where row.count >= 4 {
            let time = formatter.string(from: Date(timeIntervalSince1970: row[0]))
            out += "<trkpt lat=\"\(row[1])\" lon=\"\(row[2])\"><ele>\(row[3])</ele><time>\(time)</time></trkpt>\n"
        }
        out += "</trkseg></trk></gpx>\n"
        return out
    }
}

/// One line of the coordinate-free benchmark summary. It is built field by field, and no
/// coordinate type appears here, so the summary is safe to move into the public results doc.
public struct TripSummary: Codable, Equatable, Sendable {
    public var id: UUID
    public var mode: TripMode
    public var status: TripStatus
    public var trigger: WakeTrigger
    public var launchKind: LaunchKind
    public var wakeAt: Date
    public var appStateAtWake: String
    public var triggerDelays: [String: Double]
    public var legacyKeeperEnabled: Bool
    public var lowPowerModeAtStart: Bool
    public var relaunchCount: Int
    public var motionStartLeadSeconds: Double?
    public var confirmedAfterSeconds: Double?
    public var confirmation: String?
    public var rejection: String?
    public var endReason: String?
    public var endAfterLastMovingSeconds: Double?
    public var fixesLegacy: Int
    public var fixesLiveUpdates: Int
    public var backfillSamples: Int
    public var backfillWindowSeconds: Double?
    public var hadParkingSpot: Bool
    public var notes: [String]
    public var metrics: TripMetrics?

    public init(_ trip: TripRecord) {
        id = trip.id
        mode = trip.mode
        status = trip.status
        trigger = trip.wakeTrigger
        launchKind = trip.launchKind
        wakeAt = trip.wakeAt
        appStateAtWake = trip.appStateAtWake
        triggerDelays = Dictionary(
            trip.triggers.map { ($0.kind.rawValue, $0.at.timeIntervalSince(trip.wakeAt)) },
            uniquingKeysWith: { first, _ in first }
        )
        legacyKeeperEnabled = trip.legacyKeeperEnabled
        lowPowerModeAtStart = trip.lowPowerModeAtStart
        relaunchCount = trip.relaunchCount
        motionStartLeadSeconds = trip.motionStartAt.map { trip.wakeAt.timeIntervalSince($0) }
        confirmedAfterSeconds = trip.confirmedAt.map { $0.timeIntervalSince(trip.wakeAt) }
        confirmation = trip.confirmation
        rejection = trip.rejection
        endReason = trip.endReason
        if let end = trip.endedAt, let moving = trip.lastMovingAt {
            endAfterLastMovingSeconds = end.timeIntervalSince(moving)
        }
        fixesLegacy = trip.fixesLegacy
        fixesLiveUpdates = trip.fixesLiveUpdates
        backfillSamples = trip.backfillSamples
        backfillWindowSeconds = trip.backfillWindowSeconds
        hadParkingSpot = trip.parkingSpot != nil
        notes = trip.notes
        metrics = trip.metrics
    }
}

public struct InstallEntry: Codable, Equatable, Sendable {
    public var firstLaunchAt: Date
    public var profileCreatedAt: Date?
    public var profileExpiresAt: Date?

    public init(firstLaunchAt: Date, profileCreatedAt: Date?, profileExpiresAt: Date?) {
        self.firstLaunchAt = firstLaunchAt
        self.profileCreatedAt = profileCreatedAt
        self.profileExpiresAt = profileExpiresAt
    }
}

public struct WakeEntry: Codable, Equatable, Sendable {
    public var at: Date
    public var kind: String
    public var detail: String

    public init(at: Date, kind: String, detail: String) {
        self.at = at
        self.kind = kind
        self.detail = detail
    }
}

public struct SpikeSummary: Codable, Sendable {
    public var generatedAt: Date
    public var installs: [InstallEntry]
    public var trips: [TripSummary]
    public var wakes: [WakeEntry]

    public init(generatedAt: Date, installs: [InstallEntry], trips: [TripSummary], wakes: [WakeEntry]) {
        self.generatedAt = generatedAt
        self.installs = installs
        self.trips = trips
        self.wakes = wakes
    }
}
