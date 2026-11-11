import DPSharedBridge
import Foundation

/// Interim trip repository: processed-trip summaries and the active-trip checkpoint as JSON, protected until first
/// unlock. Replaced by the SQLDelight `TripRepository` from `:shared:data:local` (DRIVE-62). No coordinates are
/// stored here; the raw samples live in the trip files.
final class TripIndexStore: @unchecked Sendable {
    /// Written every checkpoint while a trip runs, for kill-and-relaunch recovery (§1.7).
    struct Checkpoint: Codable, Equatable {
        var tripId: String
        var source: TripSource
        var confirmed: Bool
        var startedAt: Date
        var lastCheckpointAt: Date
        var distanceM: Double
        var samples: SampleCounts
    }

    private let summariesURL: URL
    private let checkpointURL: URL
    private let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .millisecondsSince1970
        return encoder
    }()
    private let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .millisecondsSince1970
        return decoder
    }()

    init(root: URL) {
        summariesURL = root.appendingPathComponent("trips.json")
        checkpointURL = root.appendingPathComponent("active-trip.json")
    }

    func loadSummaries() -> [TripSummary] {
        guard let data = try? Data(contentsOf: summariesURL),
            let trips = try? decoder.decode([TripSummary].self, from: data)
        else { return [] }
        return trips.sorted { $0.startedAt > $1.startedAt }
    }

    func append(_ summary: TripSummary) {
        var trips = loadSummaries().filter { $0.id != summary.id }
        trips.append(summary)
        write(trips.sorted { $0.startedAt > $1.startedAt }, to: summariesURL)
    }

    func remove(tripId: String) {
        write(loadSummaries().filter { $0.id != tripId }, to: summariesURL)
    }

    func loadCheckpoint() -> Checkpoint? {
        guard let data = try? Data(contentsOf: checkpointURL) else { return nil }
        return try? decoder.decode(Checkpoint.self, from: data)
    }

    func save(_ checkpoint: Checkpoint) {
        write(checkpoint, to: checkpointURL)
    }

    func clearCheckpoint() {
        try? FileManager.default.removeItem(at: checkpointURL)
    }

    private func write(_ value: some Encodable, to url: URL) {
        guard let data = try? encoder.encode(value) else { return }
        try? FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}
