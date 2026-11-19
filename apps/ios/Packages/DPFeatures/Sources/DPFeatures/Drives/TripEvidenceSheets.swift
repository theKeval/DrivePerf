import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// S-RPL-02 (with S-SCO-03) and S-SCO-04 opened from outside Replay (DRIVE-87 AC2, DRIVE-55, DRIVE-102, DRIVE-39 AC1):
/// trip detail's event rows and tip card, the breakdown's contributor events, and Analysis's ticks and G-G peaks. The
/// sheet and the tip screen are Replay's own (`EvidenceSheetView`, `TipDetailView`) over B's presenter factories. With
/// no replay underneath, stepping between events stays inside the sheet. S-SCO-04 is pushed; its "See the moment" pops
/// back and opens that event's evidence.
@MainActor
@Observable
public final class TripEvidenceSheets {
    /// S-RPL-02 while it is open.
    public private(set) var evidence: EvidenceViewModel?
    /// S-SCO-04 while it is pushed.
    public private(set) var tip: TipDetailViewModel?
    /// "Thanks. This event won't count. …" and the other confirmations.
    public private(set) var toast: String?
    /// Feedback changed and the trip re-scored: the screen underneath reads its data again (Analysis).
    @ObservationIgnored public var onFeedbackChanged: (() -> Void)?

    @ObservationIgnored private let makeEvidence: (_ eventId: String) -> any EvidencePresenting
    @ObservationIgnored private let makeTip: (_ tipId: String?, _ eventId: String?) -> any TipDetailPresenting
    @ObservationIgnored private var evidenceTask: Task<Void, Never>?
    /// The event S-SCO-04's "See the moment" asked for, opened once the tip screen has gone.
    @ObservationIgnored private var pendingEvidence: String?

    public init(
        evidence: @escaping (_ eventId: String) -> any EvidencePresenting,
        tip: @escaping (_ tipId: String?, _ eventId: String?) -> any TipDetailPresenting
    ) {
        makeEvidence = evidence
        makeTip = tip
    }

    /// The sheets of [tripId] from Replay's presenter factories.
    public convenience init(presenters: any ReplayPresenterMaking, tripId: String) {
        self.init(
            evidence: { presenters.evidencePresenter(tripId: tripId, eventId: $0) },
            tip: { presenters.tipPresenter(tripId: tripId, tipId: $0, eventId: $1) })
    }

    isolated deinit {
        evidenceTask?.cancel()
        evidence?.close()
        tip?.close()
    }

    /// Opens S-RPL-02 for [eventId], replacing any open one.
    public func openEvidence(_ eventId: String) {
        closeEvidence()
        let model = EvidenceViewModel(
            presenter: makeEvidence(eventId), tips: { [makeTip] tipId, eventId in makeTip(tipId, eventId) })
        evidence = model
        evidenceTask = Task { [weak self] in
            for await event in model.navigationEvents() {
                guard let self else { return }
                self.onEvidence(event)
            }
        }
    }

    /// S-RPL-02 closed.
    public func closeEvidence() {
        evidenceTask?.cancel()
        evidenceTask = nil
        evidence?.close()
        evidence = nil
    }

    /// Pushes S-SCO-04 for the trip's own tip.
    public func openTip() {
        tip?.close()
        tip = TipDetailViewModel(presenter: makeTip(nil, nil))
    }

    /// S-SCO-04 asked to leave: only "See the moment" does that on this push, so its event's evidence follows.
    func tipFinished() {
        pendingEvidence = tip?.state.eventId
        tipClosed()
    }

    /// S-SCO-04 was popped.
    public func tipClosed() {
        tip?.close()
        tip = nil
    }

    /// The tip screen has finished leaving: now the evidence it asked for can rise.
    func tipDisappeared() {
        guard let eventId = pendingEvidence else { return }
        pendingEvidence = nil
        openEvidence(eventId)
    }

    func toastShown() { toast = nil }

    private func onEvidence(_ event: EvidenceNavigation) {
        switch event {
        // The sheet steps by itself; there is no replay marker underneath to move.
        case .showEvent: break
        case .feedbackChanged: onFeedbackChanged?()
        case .openTip(let tipId, let eventId): evidence?.openTip(tipId: tipId, eventId: eventId)
        case .toast(let text): toast = text
        case .close: closeEvidence()
        }
    }
}

extension View {
    /// Presents [sheets]' S-RPL-02 as a sheet and pushes its S-SCO-04, with their confirmations as a toast. Nil (no
    /// Replay available: previews, tests) presents nothing.
    func tripEvidenceSheets(_ sheets: TripEvidenceSheets?) -> some View {
        modifier(TripEvidenceSheetsModifier(sheets: sheets))
    }
}

private struct TripEvidenceSheetsModifier: ViewModifier {
    let sheets: TripEvidenceSheets?

    func body(content: Content) -> some View {
        content
            .sheet(isPresented: Binding(
                get: { sheets?.evidence != nil }, set: { if !$0 { sheets?.closeEvidence() } })
            ) {
                if let evidence = sheets?.evidence { EvidenceSheetView(model: evidence) }
            }
            .navigationDestination(isPresented: Binding(
                get: { sheets?.tip != nil }, set: { if !$0 { sheets?.tipClosed() } })
            ) {
                if let sheets, let tip = sheets.tip {
                    TipDetailView(model: tip, onDone: sheets.tipFinished)
                        .onDisappear { sheets.tipDisappeared() }
                }
            }
            .overlay(alignment: .bottom) {
                if let sheets, let text = sheets.toast {
                    Text(text).font(DPFont.bodySecondary).foregroundStyle(DPColor.text)
                        .padding(.horizontal, DPSpace.s4).padding(.vertical, DPSpace.s3)
                        .glassEffect(.regular, in: Capsule())
                        .padding(.bottom, DPSpace.s6)
                        .transition(.opacity)
                        .accessibilityAddTraits(.isStaticText)
                        .task(id: text) {
                            AccessibilityNotification.Announcement(text).post()
                            try? await Task.sleep(for: .seconds(3))
                            sheets.toastShown()
                        }
                }
            }
    }
}
