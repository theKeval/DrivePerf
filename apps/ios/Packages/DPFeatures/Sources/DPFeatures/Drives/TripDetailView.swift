import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// S-TRP-01 Trip detail, pushed from a trip card (DRIVE-55): state banners (question, passenger or not a drive,
/// interrupted, short, unscorable), the meta row, Replay (→ S-RPL-01) and Analysis (→ S-ANL-01), the Smoothness card
/// with its comparison and contributors (→ S-SCO-01, S-SCO-02), the stat tiles with provenance on long-press
/// (F-DQ-05), events by type, the positive card and the tip, then data quality (→ S-DQ-01) and "What was this?".
/// Scores are hidden on passenger and not-a-drive trips. Story, Share and Export wait for their epics; the Pro lock is
/// deferred. The map hero (drawn from the preview track, no tiles) opens Replay; event rows, contributor events and
/// the tip's moment open S-RPL-02, and the tip card pushes S-SCO-04 (DRIVE-55, DRIVE-87, DRIVE-102).
struct TripDetailView: View {
    let trips: TripListViewModel
    let tripId: String
    @Environment(\.replayServices) private var replayServices
    @Environment(\.analysisPresenters) private var analysisPresenters
    @Environment(CaptureViewModel.self) private var capture: CaptureViewModel?
    @State private var shownProvenance: String?
    @State private var replay: ReplayViewModel?
    /// The Replay the presenter handed over: its subtitle and map summary.
    @State private var replayRequest: TripListViewModel.ReplayRequest?
    @State private var showsReplay = false
    /// Replay was asked for while the in-drive lock held it (S-DRV-05); it opens when the lock lifts.
    @State private var replayHeld = false
    @State private var analysisTitle = ""
    @State private var showsAnalysis = false
    @State private var analysisHeld = false
    /// Replay asked for from Analysis: it is pushed over Analysis.
    @State private var showsReplayOverAnalysis = false
    /// S-RPL-02 and S-SCO-04 over trip detail; nil without Replay (previews, tests).
    @State private var evidence: TripEvidenceSheets?
    /// The detail was opened once; coming back from a pushed screen re-opens it only if another trip took its place.
    @State private var hasOpened = false

    var body: some View {
        // A ZStack, not a Group: its lifecycle is its own, so `onAppear` fires even before the detail is open (a
        // Group hands `onAppear` to its children, and while the detail is nil there may be none).
        ZStack {
            if let detail = trips.detail(id: tripId) {
                content(detail)
            } else if trips.contains(tripId) {
                // The presenter opens the detail within the frame; this never stays on screen.
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                Text("This drive was deleted.").font(DPFont.body).foregroundStyle(DPColor.text2)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .dpScreenBackground()
        .navigationBarTitleDisplayMode(.inline)
        .tripEditing(trips: trips, tripId: tripId)
        .onAppear {
            // `open` records `trip_detail_opened`, so popping back from Replay or the breakdown doesn't send it again.
            if !hasOpened || trips.detail(id: tripId) == nil {
                hasOpened = true
                trips.open(tripId)
            }
            if evidence == nil, let replayServices {
                evidence = TripEvidenceSheets(presenters: replayServices.presenters, tripId: tripId)
            }
        }
        .tripEvidenceSheets(evidence)
        .sheet(isPresented: Binding(
            get: { trips.dataQuality(id: tripId) != nil }, set: { if !$0 { trips.closeQuality() } })
        ) {
            if let quality = trips.dataQuality(id: tripId) { DataQualitySheet(detail: quality) }
        }
        .navigationDestination(isPresented: $showsReplay) {
            if let replay, let replayRequest {
                ReplayView(
                    model: replay, title: "Replay", subtitle: replayRequest.title, mapSummary: replayRequest.mapSummary)
            }
        }
        .onChange(of: trips.replayRequest) { _, request in
            // The presenter handed this trip to Replay (DRIVE-200).
            guard let request, request.tripId == tripId, let replayServices else { return }
            trips.replayOpened()
            replayRequest = request
            replay = replayServices.model(tripId: tripId, source: .tripDetail)
            // From Analysis, Replay goes on top of it; from trip detail, on top of trip detail.
            if showsAnalysis { showsReplayOverAnalysis = true } else { showsReplay = true }
        }
        .navigationDestination(isPresented: $showsAnalysis) {
            if let analysisPresenters {
                // Asks for the presenter once, and shows Analysis's Replay button (#85 × #87).
                AnalysisView(
                    presenters: analysisPresenters, tripId: tripId, title: analysisTitle,
                    onReplay: replayServices == nil ? nil : { _ in requestReplay() }
                )
                .navigationDestination(isPresented: $showsReplayOverAnalysis) {
                    if let replay, let replayRequest {
                        ReplayView(
                            model: replay, title: "Replay", subtitle: replayRequest.title, mapSummary: replayRequest.mapSummary)
                    }
                }
            }
        }
        .onChange(of: trips.analysisRequest) { _, request in
            guard let request, request.tripId == tripId, analysisPresenters != nil else { return }
            trips.analysisOpened()
            analysisTitle = request.title
            showsAnalysis = true
        }
        .onChange(of: capture?.heldContent) { old, new in
            // The lock lifted and released what it held: open what was asked for.
            guard new == nil else { return }
            if replayHeld, old == .replay {
                replayHeld = false
                if capture?.isContentLocked(now: Date()) == false { openReplay() }
            }
            if analysisHeld, old == .analysis {
                analysisHeld = false
                if capture?.isContentLocked(now: Date()) == false { trips.requestAnalysis(tripId) }
            }
        }
    }

    /// The map hero; it opens Replay when Replay is available.
    private func mapHero(_ map: TripMap, _ detail: TripDetail) -> some View {
        let replay: (() -> Void)? = replayServices == nil ? nil : { requestReplay() }
        return TripMapHero(
            map: map, quality: detail.quality, qualityWord: detail.qualityWord, links: detail.links, onReplay: replay)
    }

    /// The positive card and the tip, whose card and moment open S-SCO-04 and S-RPL-02 when Replay is available.
    private func highlights(_ detail: TripDetail) -> some View {
        let tip: (() -> Void)? = evidence == nil ? nil : { openTip() }
        let moment: (() -> Void)? = evidence == nil ? nil : { openTipEvidence() }
        return TripHighlights(
            cleanest: detail.cleanest, tip: detail.tip, links: detail.links, onTip: tip, onTipEvidence: moment)
    }

    /// An event row or a contributor's event: S-RPL-02 (DRIVE-87 AC2, DRIVE-55). Nil without Replay.
    private var openEvent: ((String) -> Void)? {
        guard let evidence else { return nil }
        return { [trips, tripId] eventId in
            trips.evidenceOpened(tripId: tripId, eventId: eventId)
            evidence.openEvidence(eventId)
        }
    }

    /// The tip card: S-SCO-04 for the trip's own tip (DRIVE-102).
    private func openTip() {
        trips.tipOpened(tripId)
        evidence?.openTip()
    }

    /// The tip's "See the moment": `tip_evidence_opened`, then S-RPL-02 for its event (DRIVE-102).
    private func openTipEvidence() {
        guard let eventId = trips.detail(id: tripId)?.tip?.eventId else { return }
        trips.tipEvidenceOpened(tripId)
        evidence?.openEvidence(eventId)
    }

    /// D1: during a driver trip (or the calm minute after it) Analysis waits behind S-DRV-05 too.
    private func requestAnalysis() {
        if let capture, capture.isContentLocked(now: Date()) {
            analysisHeld = true
            capture.requestContent(.analysis)
            return
        }
        trips.requestAnalysis(tripId)
    }

    /// AC6: during a driver trip (or the calm minute after it) Replay waits behind S-DRV-05.
    private func requestReplay() {
        if let capture, capture.isContentLocked(now: Date()) {
            replayHeld = true
            capture.requestContent(.replay)
            return
        }
        openReplay()
    }

    private func openReplay() {
        guard replayServices != nil, trips.contains(tripId) else { return }
        trips.requestReplay(tripId)
    }

    private var replayButton: some View {
        Button(action: requestReplay) {
            Label {
                Text("Replay").font(DPFont.button)
            } icon: {
                Image(dp: .replay).resizable().scaledToFit().frame(width: 20, height: 20)
            }
            .frame(maxWidth: .infinity, minHeight: DPLayout.touchMin)
        }
        .buttonStyle(.glassProminent)
        .tint(DPColor.accent)
        .foregroundStyle(DPColor.onAccent)
        .accessibilityHint("Watch this drive on a map")
        .accessibilityIdentifier("tripDetail.replay")
    }

    private func analysisButton(enabled: Bool) -> some View {
        Button(action: requestAnalysis) {
            Label {
                Text("Analysis").font(DPFont.button)
            } icon: {
                Image(systemName: "waveform.path.ecg")
            }
            .frame(maxWidth: .infinity, minHeight: DPLayout.touchMin)
        }
        .buttonStyle(.glass)
        .disabled(!enabled)
        .accessibilityHint(enabled ? "Speed and G over the drive" : "Detailed data removed for this drive")
        .accessibilityIdentifier("tripDetail.analysis")
    }

    /// S-TRP-01 action row: Replay (primary) · Analysis. Two rows at accessibility sizes.
    @ViewBuilder
    private func actions(_ detail: TripDetail) -> some View {
        let hasReplay = replayServices != nil
        let hasAnalysis = analysisPresenters != nil
        if hasReplay || hasAnalysis || trips.canEdit {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: DPSpace.s2) {
                    if hasReplay { replayButton }
                    if hasAnalysis { analysisButton(enabled: detail.canAnalyse) }
                    if trips.canEdit { TripEditButton(trips: trips, tripId: detail.id) }
                }
                VStack(spacing: DPSpace.s2) {
                    if hasReplay { replayButton }
                    if hasAnalysis { analysisButton(enabled: detail.canAnalyse) }
                    if trips.canEdit { TripEditButton(trips: trips, tripId: detail.id) }
                }
            }
        }
    }

    private func content(_ detail: TripDetail) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DPLayout.sectionGap) {
                if !detail.banners.isEmpty {
                    VStack(spacing: DPSpace.s2) {
                        ForEach(Array(detail.banners.enumerated()), id: \.offset) { _, banner in bannerView(banner) }
                    }
                }
                if let map = detail.map { mapHero(map, detail) }
                if let meta = detail.meta { TripMetaRow(meta: meta) }
                actions(detail)
                if !detail.scoresHidden, let score = detail.score {
                    TripScoreCard(score: score, trips: trips, tripId: tripId, links: detail.links, onEvent: openEvent)
                }
                tiles(detail.tiles)
                Text("Touch and hold a number to see how it was measured.")
                    .font(DPFont.caption).foregroundStyle(DPColor.text3)
                if !detail.scoresHidden {
                    if let events = detail.events { TripEventsSection(events: events, links: detail.links, onEvent: openEvent) }
                    if detail.cleanest != nil || detail.tip != nil {
                        highlights(detail)
                    }
                }
                VStack(spacing: 0) {
                    if let badge = detail.quality {
                        Button { trips.openQuality(tripId) } label: {
                            HStack {
                                Text("Data quality").font(DPFont.body).foregroundStyle(DPColor.text)
                                Spacer()
                                QualityBadgeLabel(badge: badge, word: detail.qualityWord ?? "")
                                DPIcon.chevronRight.image.resizable().scaledToFit().frame(width: 12, height: 12)
                                    .foregroundStyle(DPColor.text3)
                            }
                            .frame(minHeight: DPLayout.listRowMin)
                            .padding(.horizontal, DPSpace.s4)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Data quality, \(detail.qualityWord ?? "")")
                        Rectangle().fill(DPColor.separator).frame(height: 0.5).padding(.leading, DPSpace.s4)
                    }
                    if trips.canCorrect {
                        CorrectionPicker(trips: trips, tripId: tripId, current: detail.correction)
                            .frame(minHeight: DPLayout.listRowMin)
                            .padding(.horizontal, DPSpace.s4)
                    }
                }
                .dpCard()
            }
            .padding(.horizontal, DPLayout.screenMargin)
            .padding(.vertical, DPSpace.s2)
        }
        .navigationTitle(detail.title)
        .navigationSubtitle(detail.subtitle)
    }

    @ViewBuilder
    private func bannerView(_ banner: TripBanner) -> some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            Text(banner.text).font(DPFont.bodySecondary).foregroundStyle(DPColor.text)
                .fixedSize(horizontal: false, vertical: true)
            switch banner.action {
            case .answer(let question):
                HStack(spacing: DPSpace.s3) {
                    Button("Yes") { trips.answer(tripId: tripId, yes: true) }
                        .buttonStyle(.bordered)
                    Button("No") { trips.answer(tripId: tripId, yes: false) }
                        .buttonStyle(.borderedProminent)
                }
                .accessibilityLabel(question == .transit ? "Was this a bus or train?" : "Was this a bike ride?")
            case .restore:
                Button("Restore as a drive") { trips.correct(tripId: tripId, to: .driver) }
                    .font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.accentText)
                    .frame(minHeight: DPLayout.touchMin)
            case nil:
                EmptyView()
            }
        }
        .padding(DPSpace.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            banner.tone == .attention ? DPColor.warningContainer : DPColor.fill,
            in: RoundedRectangle(cornerRadius: DPRadius.md))
    }

    /// 3 columns, 2 at accessibility sizes (S-TRP-01 XL).
    private func tiles(_ tiles: [ProvenancedValue]) -> some View {
        ViewThatFits(in: .horizontal) {
            grid(tiles, columns: 3)
            grid(tiles, columns: 2)
        }
    }

    private func grid(_ tiles: [ProvenancedValue], columns: Int) -> some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: DPSpace.s2), count: columns), spacing: DPSpace.s2) {
            ForEach(tiles) { tile in
                StatTile(value: tile)
                    .popover(isPresented: Binding(
                        get: { shownProvenance == tile.id }, set: { shownProvenance = $0 ? tile.id : nil })
                    ) {
                        ProvenancePopover(value: tile).presentationCompactAdaptation(.popover)
                    }
                    .onLongPressGesture { shownProvenance = tile.id }
                    .accessibilityAction(named: "Show how this was measured") { shownProvenance = tile.id }
            }
        }
    }
}

/// "What was this?" as a menu row (S-TRP-02 type picker).
private struct CorrectionPicker: View {
    let trips: TripListViewModel
    let tripId: String
    let current: TripCorrection

    var body: some View {
        Menu {
            ForEach(trips.corrections, id: \.correction) { choice in
                Button {
                    trips.correct(tripId: tripId, to: choice.correction)
                } label: {
                    if choice.correction == current {
                        Label(choice.title, systemImage: "checkmark")
                    } else {
                        Text(choice.title)
                    }
                }
            }
        } label: {
            HStack {
                Text("What was this?").font(DPFont.body).foregroundStyle(DPColor.text)
                Spacer()
                Text(trips.correctionTitle(current)).font(DPFont.body).foregroundStyle(DPColor.text2)
            }
            .contentShape(Rectangle())
        }
    }
}

/// One number tile (components.md §5): label, value + unit, provenance glyph top-trailing; estimated values use the
/// estimated token.
struct StatTile: View {
    let value: ProvenancedValue

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s1) {
            HStack(alignment: .top) {
                Text(value.label).font(DPFont.caption).foregroundStyle(DPColor.text2)
                Spacer(minLength: DPSpace.s1)
                DPProvenanceGlyph(value.provenance.glyph).foregroundStyle(DPColor.text3)
            }
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(value.isEstimated ? DPEstimated.text(value.value) : value.value).dpNumeral(.m)
                if !value.unit.isEmpty {
                    Text(value.unit).font(DPFont.caption.weight(.medium)).foregroundStyle(DPColor.text2)
                }
            }
            .foregroundStyle(DPColor.text)
            .dpEstimated(value.isEstimated)
            if let note = value.note {
                Text(note).font(DPFont.caption).foregroundStyle(DPColor.text3)
            }
        }
        .padding(DPSpace.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DPColor.surface2, in: RoundedRectangle(cornerRadius: DPRadius.md))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(value.accessibilityLabel)
    }
}

/// The provenance popover (components.md §10.2): glyph + word + the legend line + the metric's own line.
struct ProvenancePopover: View {
    let value: ProvenancedValue

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            HStack(spacing: DPSpace.s2) {
                DPProvenanceGlyph(value.provenance.glyph, size: 14)
                Text(value.provenance.label).font(DPFont.cardTitle)
            }
            Text(value.provenanceMeaning).font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
            if !value.meaning.isEmpty {
                Text(value.meaning).font(DPFont.bodySecondary).foregroundStyle(DPColor.text)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: 280, alignment: .leading)
        .padding(DPSpace.s4)
    }
}
