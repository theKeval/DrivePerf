import DPSharedBridge
import DrivePerfShared
import Foundation

// The Kotlin Replay overlay and the shared evidence and tip presenters (DRIVE-34, DRIVE-39, DRIVE-97, DRIVE-102,
// DRIVE-203) as the `DPSharedBridge` protocols. Every word comes from the shared presenters.

/// The time of day in the phone's time zone and locale: the only formatting the shared Replay copy leaves to the app.
final class CoreReplayTimeFormat: NSObject, DrivePerfShared.ReplayTimeFormat, @unchecked Sendable {
    private let style = Date.FormatStyle(date: .omitted, time: .shortened)

    func time(epochMs: Int64) -> String {
        Date(timeIntervalSince1970: Double(epochMs) / 1000).formatted(style)
    }
}

/// Main-actor: the Kotlin types' `accessibilityLabel` shares its name with UIKit's main-actor property.
@MainActor
enum CoreEvidenceMapping {
    static func swift(_ p: DrivePerfShared.Provenance) -> ReplayProvenance {
        switch p {
        case .observed: .observed
        case .derived: .derived
        case .estimated: .estimated
        }
    }

    static func swift(_ r: DrivePerfShared.InaccurateReason) -> EvidenceInaccurateReason {
        switch r {
        case .phoneMoved: .phoneMoved
        case .pothole: .pothole
        case .notMe: .notMe
        case .other: .other
        }
    }

    static func kotlin(_ r: EvidenceInaccurateReason) -> DrivePerfShared.InaccurateReason {
        switch r {
        case .phoneMoved: .phoneMoved
        case .pothole: .pothole
        case .notMe: .notMe
        case .other: .other
        }
    }

    static func swift(_ pin: DrivePerfShared.ReplayEventPin) -> ReplayEventPinInfo {
        let m = pin.marker
        return ReplayEventPinInfo(
            id: m.eventId, kind: KotlinMapping.swift(m.type), tPeakS: m.tPeakS, lat: m.lat?.doubleValue,
            lng: m.lng?.doubleValue, needed: m.needed, pinned: m.pinned, accessibilityLabel: pin.accessibilityLabel)
    }

    static func swift(_ stop: DrivePerfShared.StopPin) -> ReplayStopPinInfo {
        ReplayStopPinInfo(
            number: Int(stop.number), atS: stop.atS, durationS: stop.durationS, lat: stop.lat?.doubleValue,
            lng: stop.lng?.doubleValue)
    }
}

/// One Kotlin `ReplayOverlay` as `ReplayOverlayReading`. The Kotlin object is immutable.
final class CoreReplayOverlay: ReplayOverlayReading, @unchecked Sendable {
    private let overlay: DrivePerfShared.ReplayOverlay
    let eventPins: [ReplayEventPinInfo]
    let ticks: [ReplayEventPinInfo]
    let stopPins: [ReplayStopPinInfo]
    let stopTicks: [ReplayStopPinInfo]
    let stopCount: Int
    let maxSpeed: ReplayMaxSpeedPinInfo?
    let summary: String
    let noEventsText: String?
    let elevationText: String?
    let elevation: [ReplayElevationSample]
    let mapSummary: String
    let speedUnit: String
    let distanceUnit: String

    @MainActor
    init(_ overlay: DrivePerfShared.ReplayOverlay) {
        self.overlay = overlay
        eventPins = overlay.eventPins.map(CoreEvidenceMapping.swift)
        ticks = overlay.ticks.map(CoreEvidenceMapping.swift)
        stopPins = overlay.stopPins.map(CoreEvidenceMapping.swift)
        stopTicks = overlay.details.stops.map(CoreEvidenceMapping.swift)
        stopCount = overlay.details.stops.count
        maxSpeed = overlay.maxSpeed.map { ReplayMaxSpeedPinInfo(tS: $0.tS, lat: $0.lat, lng: $0.lng) }
        summary = overlay.summary
        noEventsText = overlay.noEventsText
        elevationText = overlay.elevationText
        if let profile = overlay.details.elevation {
            let range = profile.maxM - profile.minM
            elevation = profile.points.map {
                ReplayElevationSample(tS: $0.tS, fraction: range > 0 ? ($0.elevM - profile.minM) / range : 0.5)
            }
        } else {
            elevation = []
        }
        mapSummary = overlay.mapSummary
        speedUnit = overlay.speedUnit
        distanceUnit = overlay.distanceUnit
    }

    func distanceText(at tS: Double) -> String { overlay.distanceText(tS: tS) }

    func speedText(_ speedMps: Double?) -> String {
        overlay.speedText(speedMps: speedMps.map { KotlinDouble(value: $0) })
    }

    func elevationFraction(at tS: Double) -> Double {
        overlay.details.elevation?.heightFractionAt(tS: tS) ?? 0.5
    }

    func crossed(from fromS: Double, to toS: Double) -> [String] {
        overlay.details.crossed(fromS: fromS, toS: toS, maxJumpS: DrivePerfShared.ReplayDetails.companion.MAX_PULSE_JUMP_S)
            .map(\.eventId)
    }
}

/// One Kotlin `EvidencePresenter` (main-thread scope) as `EvidencePresenting`.
@MainActor
final class CoreEvidencePresenter: EvidencePresenting {
    private let presenter: DrivePerfShared.EvidencePresenter

    init(_ presenter: DrivePerfShared.EvidencePresenter) {
        self.presenter = presenter
    }

    var state: EvidenceScreenState { Self.swift(presenter.state.value) }

    func states() -> AsyncStream<EvidenceScreenState> {
        let flow = UncheckedSendable(presenter.state)
        return AsyncStream { continuation in
            let task = Task { @MainActor in
                for await value in flow.value { continuation.yield(Self.swift(value)) }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func navigation() -> AsyncStream<EvidenceNavigation> {
        let flow = UncheckedSendable(presenter.effects)
        return AsyncStream { continuation in
            let task = Task { @MainActor in
                for await effect in flow.value {
                    if let effect, let next = Self.swift(effect) { continuation.yield(next) }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func dispatch(_ action: EvidenceAction) {
        presenter.dispatch(intent: Self.kotlin(action))
    }

    func close() { presenter.close() }

    static func kotlin(_ action: EvidenceAction) -> any DrivePerfShared.EvidenceIntent {
        switch action {
        case .askNeeded: EvidenceIntentAskNeeded.shared
        case .confirmNeeded: EvidenceIntentConfirmNeeded.shared
        case .askCountAgain: EvidenceIntentAskCountAgain.shared
        case .confirmCountAgain: EvidenceIntentConfirmCountAgain.shared
        case .dismissNeeded: EvidenceIntentDismissNeeded.shared
        case .openReasons: EvidenceIntentOpenReasons.shared
        case .selectReason(let reason): EvidenceIntentSelectReason(reason: CoreEvidenceMapping.kotlin(reason))
        case .sendReason: EvidenceIntentSendReason.shared
        case .cancelReasons: EvidenceIntentCancelReasons.shared
        case .previous: EvidenceIntentPrevious.shared
        case .next: EvidenceIntentNext.shared
        case .openTip: EvidenceIntentOpenTip.shared
        case .close: EvidenceIntentClose.shared
        }
    }

    static func swift(_ effect: any DrivePerfShared.EvidenceEffect) -> EvidenceNavigation? {
        if let show = effect as? EvidenceEffectShowEvent { return .showEvent(show.eventId) }
        if effect is EvidenceEffectFeedbackChanged { return .feedbackChanged }
        if let tip = effect as? EvidenceEffectOpenTip { return .openTip(tipId: tip.tipId, eventId: tip.eventId) }
        if let toast = effect as? EvidenceEffectToast { return .toast(toast.text) }
        if effect is EvidenceEffectClose { return .close }
        return nil
    }

    static func swift(_ value: DrivePerfShared.EvidenceState?) -> EvidenceScreenState {
        guard let s = value else { return EvidenceScreenState(eventId: "") }
        let copy = EvidenceCopy.shared
        return EvidenceScreenState(
            eventId: s.eventId, loading: s.loading, missing: s.missing, kind: s.type.map(KotlinMapping.swift),
            needed: s.needed, title: s.title, subtitle: s.subtitle,
            banners: s.banners.map { banner in
                EvidenceBannerInfo(kind: kind(banner.kind), text: banner.text, action: banner.action)
            },
            trace: s.trace.map { trace(trace: $0, perMps: s.speedPerMps) }, traceSummary: s.traceSummary,
            facts: s.facts.map {
                EvidenceFactInfo(
                    label: $0.label, value: $0.value, unit: $0.unit, provenance: CoreEvidenceMapping.swift($0.provenance),
                    struck: $0.struck, spoken: $0.spoken)
            },
            tip: s.tip.map {
                EvidenceTipInfo(tipId: $0.tipId, kicker: $0.kicker, title: $0.title, text: $0.text, thisDrive: $0.thisDrive)
            },
            position: s.position, hasPrevious: s.hasPrevious, hasNext: s.hasNext,
            neededSheet: s.neededSheet.map(needed), reasonsOpen: s.reasonsOpen,
            reasons: s.reasons.map { (reason: CoreEvidenceMapping.swift($0.reason), label: $0.label) },
            selectedReason: s.selectedReason.map(CoreEvidenceMapping.swift), busy: s.busy,
            accessibilityLabel: s.accessibilityLabel, speedUnit: s.speedUnit,
            words: EvidenceWords(
                wasItNeeded: copy.WAS_IT_NEEDED, notAccurate: copy.NOT_ACCURATE, reasonsTitle: copy.REASONS_TITLE,
                reasonsBody: copy.REASONS_BODY, send: copy.SEND, cancel: copy.CANCEL, whyThisTip: copy.WHY_THIS_TIP))
    }

    private static func kind(_ k: DrivePerfShared.EvidenceBannerKind) -> EvidenceBannerInfo.Kind {
        switch k {
        case .lowConfidence: .lowConfidence
        case .needed: .needed
        case .traceRemoved: .traceRemoved
        }
    }

    private static func trace(trace: DrivePerfShared.EventEvidence, perMps: Double) -> EvidenceTraceInfo {
        EvidenceTraceInfo(
            t0S: trace.t0S, t1S: trace.t1S, tStartS: trace.tStartS, tPeakS: trace.tPeakS, tEndS: trace.tEndS,
            g: trace.g.map { EvidenceTraceInfo.G(tS: $0.tS, lonG: $0.lonG, latG: $0.latG) },
            speed: trace.speed.map { EvidenceTraceInfo.Speed(tS: $0.tS, value: $0.speedMps * perMps) })
    }

    private static func needed(_ sheet: DrivePerfShared.NeededSheet) -> NeededSheetInfo {
        let step: NeededSheetInfo.Step = switch sheet.step {
        case .ask: .ask
        case .done: .done
        case .countAgain: .countAgain
        }
        return NeededSheetInfo(
            step: step, title: sheet.title, body: sheet.body, primary: sheet.primary, secondary: sheet.secondary,
            score: sheet.score.map { Int(truncating: $0) }, capNote: sheet.capNote)
    }
}

/// One Kotlin `TipDetailPresenter` (main-thread scope) as `TipDetailPresenting`.
@MainActor
final class CoreTipDetailPresenter: TipDetailPresenting {
    private let presenter: DrivePerfShared.TipDetailPresenter

    init(_ presenter: DrivePerfShared.TipDetailPresenter) {
        self.presenter = presenter
    }

    var state: TipDetailScreenState { Self.swift(presenter.state.value) }

    func states() -> AsyncStream<TipDetailScreenState> {
        let flow = UncheckedSendable(presenter.state)
        return AsyncStream { continuation in
            let task = Task { @MainActor in
                for await value in flow.value { continuation.yield(Self.swift(value)) }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func navigation() -> AsyncStream<TipDetailNavigation> {
        let flow = UncheckedSendable(presenter.effects)
        return AsyncStream { continuation in
            let task = Task { @MainActor in
                for await effect in flow.value {
                    if let open = effect as? TipDetailEffectOpenEvidence {
                        continuation.yield(.openEvidence(eventId: open.eventId))
                    } else if effect is TipDetailEffectClose {
                        continuation.yield(.close)
                    }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func dispatch(_ action: TipDetailAction) {
        switch action {
        case .seeTheMoment: presenter.dispatch(intent: TipDetailIntentSeeTheMoment.shared)
        case .back: presenter.dispatch(intent: TipDetailIntentBack.shared)
        }
    }

    func close() { presenter.close() }

    static func swift(_ value: DrivePerfShared.TipDetailState?) -> TipDetailScreenState {
        guard let s = value else { return TipDetailScreenState() }
        let copy = EvidenceCopy.shared
        return TipDetailScreenState(
            loading: s.loading, title: s.title, tipId: s.tipId, cleanest: s.cleanest, kicker: s.kicker,
            tipText: s.tipText, why: s.why, sparkline: s.sparkline.map(\.doubleValue),
            sparklinePeakIndex: Int(s.sparklinePeakIndex), eventId: s.eventId, steps: s.steps, footer: s.footer,
            noTip: s.noTip,
            words: TipWords(
                cleanest: copy.CLEANEST, whyThisOne: copy.WHY_THIS_ONE, howToTry: copy.HOW_TO_TRY,
                seeTheMoment: copy.SEE_THE_MOMENT, noTipTitle: copy.NO_TIP_TITLE))
    }
}

/// Before the core has started, or in the fixture: there is no event to show.
@MainActor
final class UnavailableEvidencePresenter: EvidencePresenting {
    let state: EvidenceScreenState

    init(eventId: String) {
        state = EvidenceScreenState(eventId: eventId, loading: false, missing: true)
    }

    func states() -> AsyncStream<EvidenceScreenState> {
        let value = state
        return AsyncStream { $0.yield(value); $0.finish() }
    }
    func navigation() -> AsyncStream<EvidenceNavigation> { AsyncStream { $0.finish() } }
    func dispatch(_ action: EvidenceAction) {}
    func close() {}
}

/// Before the core has started, or in the fixture: no tip.
@MainActor
final class UnavailableTipPresenter: TipDetailPresenting {
    let state = TipDetailScreenState(loading: false, title: "One thing for next time", noTip: "")

    func states() -> AsyncStream<TipDetailScreenState> {
        let value = state
        return AsyncStream { $0.yield(value); $0.finish() }
    }
    func navigation() -> AsyncStream<TipDetailNavigation> { AsyncStream { $0.finish() } }
    func dispatch(_ action: TipDetailAction) {}
    func close() {}
}
