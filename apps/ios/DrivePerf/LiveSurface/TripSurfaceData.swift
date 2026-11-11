import DPSharedBridge
import DrivePerfShared
import Foundation
import OSLog
import WidgetKit

@MainActor
final class TripSurfaceData {
    private let trips: UncheckedSendable<any TripRepository>
    private let logger = Logger(subsystem: "com.driveperf.app", category: "WidgetSnapshot")
    private var snapshot = DriveWidgetSnapshot.read()
    private var lastDistanceUpdate = Date.distantPast

    init(trips: any TripRepository) { self.trips = UncheckedSendable(trips) }

    func updateLocation(canRecord: Bool) {
        var next = snapshot
        next.canRecord = canRecord
        publish(next)
    }

    func updateRecording(status: DPSharedBridge.RecorderStatus, offersLiveActivity: Bool, now: Date = .now) {
        var next = snapshot
        if status.isDriving, let id = status.tripId {
            let existing = snapshot.recording
            let sameTrip = existing?.tripID == id
            let distance = Int((max(0, status.distanceM) / 100).rounded(.down)) * 100
            let updateDistance = !sameTrip || now.timeIntervalSince(lastDistanceUpdate) >= 60
            let previousDistance = sameTrip ? existing?.distanceM : nil
            if updateDistance, distance != previousDistance { lastDistanceUpdate = now }
            next.recording = .init(
                tripID: id,
                startedAt: (sameTrip ? existing?.startedAt : nil) ?? now.addingTimeInterval(-max(0, status.elapsedS)),
                distanceM: updateDistance ? distance : previousDistance ?? 0,
                offersLiveActivity: offersLiveActivity)
        } else {
            next.recording = nil
        }
        publish(next)
    }

    func updateLatestDrive(_ history: [TripSummary]) async {
        do {
            let latest = try await Self.latestDrive(in: history) { id in
                try await Self.readDrive(trips, tripID: id)
            }
            var next = snapshot
            next.latestDrive = latest
            publish(next)
        } catch {
            logger.error("Could not read the latest drive for widgets: \(error.localizedDescription, privacy: .private)")
        }
    }

    static func latestDrive(
        in history: [TripSummary], readDrive: (String) async throws -> DriveWidgetSnapshot.LatestDrive?
    ) async rethrows -> DriveWidgetSnapshot.LatestDrive? {
        for summary in history where summary.status == .finalised || summary.status == .short {
            if let drive = try await readDrive(summary.id) { return drive }
        }
        return nil
    }

    func decodedTrip(tripID: String) async -> DriveWidgetSnapshot.LatestDrive? {
        do {
            return try await Self.readDrive(trips, tripID: tripID)
        } catch {
            logger.error("Could not read the decoded drive: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }

    // The Kotlin repository dispatches its suspend reads to its own IO dispatcher.
    nonisolated private static func readDrive(
        _ trips: UncheckedSendable<any TripRepository>, tripID: String
    ) async throws -> DriveWidgetSnapshot.LatestDrive? {
        guard let trip = try await trips.value.trip(id: tripID), trip.status == .finalised, trip.kind == .driver else {
            return nil
        }
        let score = try await trips.value.score(tripId: tripID)
        return .init(
            partOfDay: partOfDay(trip, startedAt: Date(timeIntervalSince1970: Double(trip.startedAt) / 1000)),
            distanceM: trip.distanceM.map { Int(truncating: $0) } ?? 0,
            smoothness: score?.smoothness.map { Int(truncating: $0) },
            baseline: score?.baseline.map { Int(truncating: $0) })
    }

    private func publish(_ next: DriveWidgetSnapshot) {
        guard next != snapshot, let url = DriveWidgetSnapshot.fileURL else { return }
        do {
            try next.write(to: url)
        } catch {
            logger.error("Could not write the widget snapshot: \(error.localizedDescription, privacy: .private)")
            return
        }
        let actionChanged = snapshot.action != next.action
        snapshot = next
        WidgetCenter.shared.reloadTimelines(ofKind: DriveWidgetSnapshot.widgetKind)
        if actionChanged { ControlCenter.shared.reloadControls(ofKind: DriveWidgetSnapshot.controlKind) }
    }

    nonisolated private static func partOfDay(_ trip: Trip, startedAt: Date) -> String {
        switch trip.partOfDay {
        case .morning: return "Morning"
        case .midday: return "Midday"
        case .afternoon: return "Afternoon"
        case .evening: return "Evening"
        case .night: return "Night"
        default: break
        }
        // Older capture rows may have no part of day. Use the same cutoffs as the pipeline.
        var calendar = Calendar.current
        if let zone = TimeZone(secondsFromGMT: Int(trip.startTzOffsetMin) * 60) { calendar.timeZone = zone }
        switch calendar.component(.hour, from: startedAt) {
        case 5..<11: return "Morning"
        case 11..<14: return "Midday"
        case 14..<17: return "Afternoon"
        case 17..<21: return "Evening"
        default: return "Night"
        }
    }
}
