import DPSharedBridge
import DrivePerfShared
import Foundation

/// `TripCorrecting` over the shared core's `TripCorrections` (DRIVE-100). Like `CoreTripRecorder` it exists before
/// the core starts; until `attach` it lists no trips and drops answers (no trip can be listed before then either).
@MainActor
final class CoreTripCorrections: TripCorrecting {
    private var corrections: (any DrivePerfShared.TripCorrections)?
    private(set) var notDrives: [TripSummary] = [] {
        didSet { observers.values.forEach { $0.yield(notDrives) } }
    }
    private var observers: [UUID: AsyncStream<[TripSummary]>.Continuation] = [:]

    /// Connects the started core. Later calls are ignored.
    func attach(_ corrections: any DrivePerfShared.TripCorrections) {
        guard self.corrections == nil else { return }
        self.corrections = corrections
        let flow = UncheckedSendable(corrections.observeNotDrives())
        Task { [weak self] in
            for await trips in flow.value { self?.notDrives = trips.compactMap(KotlinMapping.summary) }
        }
    }

    func observeNotDrives() -> AsyncStream<[TripSummary]> {
        let id = UUID()
        return AsyncStream { continuation in
            continuation.yield(notDrives)
            observers[id] = continuation
            continuation.onTermination = { [weak self] _ in
                Task { @MainActor in self?.observers[id] = nil }
            }
        }
    }

    func answer(tripId: String, yes: Bool) async {
        guard let corrections else { return }
        await Self.answer(UncheckedSendable(corrections), tripId: tripId, yes: yes)
    }

    func correct(tripId: String, to correction: DPSharedBridge.TripCorrection) async {
        guard let corrections else { return }
        await Self.correct(UncheckedSendable(corrections), tripId: tripId, to: KotlinMapping.kotlin(correction))
    }

    // The Kotlin port is thread-safe (its work runs on the core's IO context); these hop off the main actor to call it.
    private nonisolated static func answer(
        _ port: UncheckedSendable<any DrivePerfShared.TripCorrections>, tripId: String, yes: Bool
    ) async {
        _ = try? await port.value.answerSuspect(tripId: tripId, answer: yes ? .yes : .no)
    }

    private nonisolated static func correct(
        _ port: UncheckedSendable<any DrivePerfShared.TripCorrections>, tripId: String,
        to correction: DrivePerfShared.TripCorrection
    ) async {
        _ = try? await port.value.correct(tripId: tripId, to: correction)
    }
}
