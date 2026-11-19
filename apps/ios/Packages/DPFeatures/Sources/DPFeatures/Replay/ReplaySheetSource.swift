import DPSharedBridge
import Foundation

/// Where Replay gets its sheets' presenters: S-RPL-02 for an event and S-SCO-04 for a tip, for one trip.
@MainActor
public struct ReplaySheetSource {
    let evidence: (_ eventId: String) -> any EvidencePresenting
    let tip: (_ tipId: String, _ eventId: String) -> any TipDetailPresenting

    public init(
        evidence: @escaping (_ eventId: String) -> any EvidencePresenting,
        tip: @escaping (_ tipId: String, _ eventId: String) -> any TipDetailPresenting
    ) {
        self.evidence = evidence
        self.tip = tip
    }

    /// The sheets for `tripId` from `presenters`.
    public init(presenters: any ReplayPresenterMaking, tripId: String) {
        self.init(
            evidence: { presenters.evidencePresenter(tripId: tripId, eventId: $0) },
            tip: { presenters.tipPresenter(tripId: tripId, tipId: $0, eventId: $1) })
    }
}
