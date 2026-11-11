import Foundation

/// The app writes one privacy-safe snapshot for WidgetKit and Control Center.
struct DriveWidgetSnapshot: Codable, Equatable, Sendable {
    static let groupID = "group.com.driveperf.app"
    static let widgetKind = "com.driveperf.widget.drive"
    static let controlKind = "com.driveperf.control.drive"

    struct Recording: Codable, Equatable, Sendable {
        let tripID: String
        let startedAt: Date
        let distanceM: Int
        let offersLiveActivity: Bool
    }

    struct LatestDrive: Codable, Equatable, Sendable {
        let partOfDay: String
        let distanceM: Int
        let smoothness: Int?
        let baseline: Int?

        var delta: Int? {
            guard let smoothness, let baseline else { return nil }
            return smoothness - baseline
        }
    }

    enum Action: Equatable, Sendable {
        case start, showLive(String), stop(String), fixLocation
    }

    var canRecord: Bool?
    var recording: Recording?
    var latestDrive: LatestDrive?

    var action: Action {
        if let recording {
            return recording.offersLiveActivity ? .showLive(recording.tripID) : .stop(recording.tripID)
        }
        return canRecord == false ? .fixLocation : .start
    }

    static var fileURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID)?
            .appendingPathComponent("drive-widget-v1.json")
    }

    static func read(from url: URL? = fileURL) -> Self {
        guard let url, let data = try? Data(contentsOf: url),
            let snapshot = try? JSONDecoder().decode(Self.self, from: data)
        else { return Self() }
        return snapshot
    }

    func write(to url: URL) throws {
        try JSONEncoder().encode(self).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}
