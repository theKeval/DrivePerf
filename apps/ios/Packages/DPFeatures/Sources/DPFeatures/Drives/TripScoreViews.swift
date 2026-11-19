import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// Car chip (or "Passenger") · how it started · start–end time, under trip detail's title.
struct TripMetaRow: View {
    let meta: TripMeta

    var body: some View {
        HStack(spacing: DPSpace.s2) {
            if meta.isPassenger {
                TagLabel(text: "Passenger")
            } else if let car = meta.car {
                TagLabel(text: car)
            }
            Text("\(meta.source) · \(meta.timeRange)").font(DPFont.meta).foregroundStyle(DPColor.text2)
        }
        .accessibilityElement(children: .combine)
    }
}

/// S-TRP-01 Smoothness card: ring 124 with its comparison and lines, "How scores work", the four contributors (one with
/// events expands to its event rows, each opening its evidence: DRIVE-87 AC2) and "Smoothness breakdown ›".
struct TripScoreCard: View {
    let score: ScoreSummary
    let trips: TripListViewModel
    let tripId: String
    var links = TripLinkWords()
    /// Opens S-RPL-02 for an event; nil without Replay, and then the rows are not buttons.
    var onEvent: ((String) -> Void)?
    @State private var open: ScoreContributorID?

    init(
        score: ScoreSummary, trips: TripListViewModel, tripId: String, links: TripLinkWords = TripLinkWords(),
        onEvent: ((String) -> Void)? = nil
    ) {
        self.score = score
        self.trips = trips
        self.tripId = tripId
        self.links = links
        self.onEvent = onEvent
    }

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s4) {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: DPSpace.s4) { ring; lines }
                VStack(alignment: .leading, spacing: DPSpace.s3) { ring; lines }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(score.accessibilityLabel)
            NavigationLink {
                HowScoresWorkView(trips: trips, source: .tripDetail)
            } label: {
                Label("How scores work", systemImage: "info.circle").font(DPFont.meta.weight(.semibold))
                    .foregroundStyle(DPColor.accentText)
                    .frame(minHeight: DPLayout.touchMin)
            }
            ForEach(score.contributors) { contributor in
                ExpandableContributorRow(
                    contributor: contributor, isOpen: open == contributor.id, links: links, padding: 0,
                    onToggle: { opening in
                        withAnimation(.spring) { open = opening ? contributor.id : nil }
                        if opening { trips.contributorTapped(contributor.id) }
                    },
                    onEvent: onEvent)
            }
            Rectangle().fill(DPColor.separator).frame(height: 0.5)
            NavigationLink {
                ScoreBreakdownView(trips: trips, tripId: tripId)
            } label: {
                HStack {
                    Text("Smoothness breakdown").font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.text)
                    Spacer()
                    DPIcon.chevronRight.image.resizable().scaledToFit().frame(width: 12, height: 12)
                        .foregroundStyle(DPColor.text3)
                }
                .frame(minHeight: DPLayout.touchMin)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("tripDetail.breakdown")
        }
        .padding(DPSpace.s4)
        .dpCard()
    }

    private var ring: some View { ScoreRing(score: score.smoothness, baseline: score.baseline, diameter: 124) }

    private var lines: some View {
        VStack(alignment: .leading, spacing: DPSpace.s1_5) {
            Text("Smoothness").font(DPFont.caption).foregroundStyle(DPColor.text2)
            if !score.isScored {
                Text("Not scored").font(DPFont.cardTitle).foregroundStyle(DPColor.text)
                if let reason = score.notScoredReason {
                    Text(reason).font(DPFont.meta).foregroundStyle(DPColor.text2).fixedSize(horizontal: false, vertical: true)
                }
            }
            if let comparison = score.comparison { ComparisonChip(comparison: comparison) }
            if let record = score.recordLine {
                Label(record, systemImage: "rosette").font(DPFont.meta).foregroundStyle(DPColor.record)
            }
            if let based = score.basedOn { Text(based).font(DPFont.caption).foregroundStyle(DPColor.text2) }
            if let pace = score.paceNote { Text(pace).font(DPFont.caption).foregroundStyle(DPColor.text2) }
        }
    }
}

/// "Events · n": counts by type and the rows; "No abrupt inputs on this drive." without any. Each row, pinned or not,
/// opens its evidence (DRIVE-55); one reported "Not accurate?" is left out and opens nothing.
struct TripEventsSection: View {
    let events: TripEvents
    var links = TripLinkWords()
    var onEvent: ((String) -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            Text(events.title).font(DPFont.sectionTitle).foregroundStyle(DPColor.text).accessibilityAddTraits(.isHeader)
            if let note = events.note {
                Text(note).font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
            } else if events.rows.isEmpty {
                VStack(alignment: .leading, spacing: DPSpace.s1) {
                    Text(events.emptyTitle).font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.text)
                    Text(events.emptyBody).font(DPFont.meta).foregroundStyle(DPColor.text2)
                }
                .padding(DPSpace.s4)
                .frame(maxWidth: .infinity, alignment: .leading)
                .dpCard()
                .accessibilityElement(children: .combine)
            } else {
                HStack(spacing: DPSpace.s2) {
                    ForEach(events.counts, id: \.type) { count in
                        HStack(spacing: DPSpace.s1) {
                            DPEventGlyph(count.type.glyph, size: 16)
                            Text("\(count.label) \(count.count)").font(DPFont.caption.weight(.semibold))
                        }
                        .padding(.horizontal, DPSpace.s2).padding(.vertical, DPSpace.s1)
                        .background(DPColor.fill, in: Capsule())
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("\(count.label), \(count.count)")
                    }
                }
                VStack(spacing: 0) {
                    ForEach(Array(events.rows.enumerated()), id: \.element.id) { index, row in
                        if index > 0 { Rectangle().fill(DPColor.separator).frame(height: 0.5).padding(.leading, 40) }
                        EventRowButton(event: row, links: links, onEvent: onEvent)
                    }
                }
                .padding(.horizontal, DPSpace.s4).padding(.vertical, DPSpace.s1)
                .dpCard()
            }
        }
    }
}

/// The positive card first (✦ Cleanest stretch), then the tip card: it pushes S-SCO-04 ([onTip]) and "See the
/// moment ›" opens its event's evidence ([onTipEvidence], DRIVE-102).
struct TripHighlights: View {
    let cleanest: String?
    let tip: ScoreTip?
    var links = TripLinkWords()
    var onTip: (() -> Void)?
    var onTipEvidence: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            Text("Highlights").font(DPFont.sectionTitle).foregroundStyle(DPColor.text).accessibilityAddTraits(.isHeader)
            if let cleanest {
                HStack(alignment: .top, spacing: DPSpace.s3) {
                    Image(systemName: "sparkle").foregroundStyle(DPColor.accent).accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Cleanest stretch").font(DPFont.caption.weight(.semibold)).foregroundStyle(DPColor.text2)
                        Text(cleanest).font(DPFont.body).foregroundStyle(DPColor.text)
                    }
                }
                .padding(DPSpace.s4)
                .frame(maxWidth: .infinity, alignment: .leading)
                .dpCard()
                .accessibilityElement(children: .combine)
            }
            if let tip { tipCard(tip) }
        }
    }

    @ViewBuilder
    private func tipCard(_ tip: ScoreTip) -> some View {
        if let onTip {
            Button(action: onTip) { TipCardView(tip: tip).contentShape(Rectangle()) }
                .buttonStyle(.plain)
                .accessibilityHint(links.openTip)
                .accessibilityIdentifier("tripDetail.tip")
        } else {
            TipCardView(tip: tip)
        }
        if tip.eventId != nil, let onTipEvidence {
            Button(action: onTipEvidence) {
                HStack(spacing: DPSpace.s1) {
                    Text(links.seeTheMoment).font(DPFont.body.weight(.semibold))
                    Image(systemName: "chevron.right").font(DPFont.caption)
                }
                .foregroundStyle(DPColor.accentText)
                .frame(minHeight: DPLayout.touchMin)
            }
            .accessibilityHint(links.openEvidence)
            .accessibilityIdentifier("tripDetail.tipMoment")
        }
    }
}

/// An event row that opens its evidence ([onEvent]), unless it was reported "Not accurate?" and left out, or there is
/// no Replay to open it with.
struct EventRowButton: View {
    let event: ScoreEventRow
    let links: TripLinkWords
    let onEvent: ((String) -> Void)?

    var body: some View {
        if let onEvent, !event.isExcluded {
            Button { onEvent(event.id) } label: { ScoreEventRowView(event: event).contentShape(Rectangle()) }
                .buttonStyle(.plain)
                .accessibilityLabel(event.accessibilityLabel)
                .accessibilityHint(links.openEvidence)
                .accessibilityIdentifier("event.\(event.id)")
        } else {
            ScoreEventRowView(event: event)
        }
    }
}

/// A contributor row; one with events toggles them open, and each event opens its evidence (DRIVE-87 AC2). Shared by
/// trip detail and S-SCO-01.
struct ExpandableContributorRow: View {
    let contributor: ScoreContributor
    let isOpen: Bool
    let links: TripLinkWords
    var padding: CGFloat = DPSpace.s4
    let onToggle: (Bool) -> Void
    let onEvent: ((String) -> Void)?

    var body: some View {
        let hasEvents = !contributor.events.isEmpty
        VStack(alignment: .leading, spacing: 0) {
            Button {
                guard hasEvents else { return }
                onToggle(!isOpen)
            } label: {
                HStack {
                    ContributorRowView(contributor: contributor)
                    if hasEvents {
                        Image(systemName: isOpen ? "chevron.up" : "chevron.down").font(DPFont.caption).foregroundStyle(DPColor.text3)
                    }
                }
                .padding(padding)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!hasEvents)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(contributor.accessibilityLabel)
            .accessibilityHint(hasEvents ? (isOpen ? links.hideEvents : links.showEvents) : "")
            .accessibilityAddTraits(hasEvents ? .isButton : [])
            .accessibilityIdentifier("contributor.\(contributor.id)")
            if hasEvents && isOpen {
                ForEach(contributor.events) { event in
                    EventRowButton(event: event, links: links, onEvent: onEvent).padding(.horizontal, padding)
                }
                .padding(.bottom, DPSpace.s2)
            }
        }
    }
}

/// S-SCO-01 Smoothness breakdown (DRIVE-87, DRIVE-82): the hero ring with its comparison and basis, "Compared with",
/// the four contributors (one with events expands to its event rows), the route's last runs and the links.
struct ScoreBreakdownView: View {
    let trips: TripListViewModel
    let tripId: String
    @State private var open: ScoreContributorID?
    @State private var didPickInitial = false
    @Environment(\.replayServices) private var replayServices
    /// S-RPL-02 for a contributor's event (DRIVE-87 AC2); nil without Replay.
    @State private var evidence: TripEvidenceSheets?

    var body: some View {
        ScrollView {
            if let detail = trips.detail(id: tripId), let score = detail.score, let breakdown = detail.breakdown {
                VStack(alignment: .leading, spacing: DPLayout.sectionGap) {
                    hero(score, breakdown)
                    if score.isScored {
                        if !breakdown.options.isEmpty { comparedWith(breakdown) }
                        whatMovedIt(score.contributors, footer: breakdown.contributorsFooter)
                        runs(breakdown)
                    } else if let footer = breakdown.notScoredFooter {
                        Text(footer).font(DPFont.caption).foregroundStyle(DPColor.text2)
                    }
                    links(scored: score.isScored)
                }
                .padding(.horizontal, DPLayout.screenMargin)
                .padding(.vertical, DPSpace.s2)
                .onAppear {
                    guard !didPickInitial else { return }
                    didPickInitial = true
                    open = score.contributors.first { !$0.events.isEmpty }?.id
                }
            }
        }
        .dpScreenBackground()
        .navigationTitle("Smoothness")
        .navigationSubtitle(trips.detail(id: tripId)?.subtitle ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink { HowScoresWorkView(trips: trips, source: .breakdown) } label: {
                    Image(systemName: "info.circle")
                }
                .accessibilityLabel("How scores work")
            }
        }
        .onAppear { trips.showBreakdown(tripId) }
        .onAppear {
            guard evidence == nil, let replayServices else { return }
            evidence = TripEvidenceSheets(presenters: replayServices.presenters, tripId: tripId)
        }
        .tripEvidenceSheets(evidence)
    }

    private var openEvent: ((String) -> Void)? {
        guard let evidence else { return nil }
        return { [trips, tripId] eventId in
            trips.evidenceOpened(tripId: tripId, eventId: eventId)
            evidence.openEvidence(eventId)
        }
    }

    private func hero(_ score: ScoreSummary, _ breakdown: ScoreBreakdown) -> some View {
        VStack(spacing: DPSpace.s3) {
            ScoreRing(score: score.smoothness, baseline: score.baseline, diameter: 168, showsLabel: true)
            if !score.isScored { Text("Not scored").font(DPFont.cardTitle).foregroundStyle(DPColor.text) }
            if let comparison = score.comparison { ComparisonChip(comparison: comparison) }
            if let record = score.recordLine { Label(record, systemImage: "rosette").font(DPFont.meta).foregroundStyle(DPColor.record) }
            if let pace = score.paceNote { Text(pace).font(DPFont.caption).foregroundStyle(DPColor.text2) }
            Text(score.isScored ? breakdown.basisLine : breakdown.notScoredText ?? "")
                .font(DPFont.bodySecondary).foregroundStyle(DPColor.text)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(DPSpace.s5)
        .frame(maxWidth: .infinity)
        .dpCard(radius: DPRadius.cardHero)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([score.accessibilityLabel, score.isScored ? breakdown.basisLine : breakdown.notScoredText ?? ""].joined(separator: " "))
    }

    private func comparedWith(_ breakdown: ScoreBreakdown) -> some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            sectionTitle("Compared with")
            VStack(spacing: 0) {
                ForEach(Array(breakdown.options.enumerated()), id: \.offset) { index, option in
                    if index > 0 { Rectangle().fill(DPColor.separator).frame(height: 0.5).padding(.leading, DPSpace.s4) }
                    HStack(spacing: DPSpace.s3) {
                        Image(systemName: option.isSelected ? "largecircle.fill.circle" : "circle")
                            .foregroundStyle(option.isSelected ? DPColor.accent : DPColor.text3)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(option.title).font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.text)
                            Text(option.detail).font(DPFont.meta).foregroundStyle(DPColor.text2)
                        }
                        Spacer(minLength: DPSpace.s2)
                        if let value = option.value { Text(value).font(DPFont.meta.weight(.semibold)).foregroundStyle(DPColor.text) }
                    }
                    .padding(DPSpace.s4)
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(option.isSelected ? .isSelected : [])
                }
            }
            .dpCard()
            Text(breakdown.optionsFooter).font(DPFont.caption).foregroundStyle(DPColor.text2)
        }
    }

    private func whatMovedIt(_ contributors: [ScoreContributor], footer: String) -> some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            sectionTitle("What moved it")
            VStack(spacing: 0) {
                ForEach(Array(contributors.enumerated()), id: \.element.id) { index, contributor in
                    if index > 0 { Rectangle().fill(DPColor.separator).frame(height: 0.5).padding(.leading, DPSpace.s4) }
                    contributorRow(contributor)
                }
            }
            .dpCard()
            Text(footer).font(DPFont.caption).foregroundStyle(DPColor.text2)
        }
    }

    private func contributorRow(_ contributor: ScoreContributor) -> some View {
        ExpandableContributorRow(
            contributor: contributor, isOpen: open == contributor.id,
            links: trips.detail(id: tripId)?.links ?? TripLinkWords(),
            onToggle: { opening in
                withAnimation(.spring) { open = opening ? contributor.id : nil }
                if opening { trips.contributorTapped(contributor.id) }
            },
            onEvent: openEvent)
    }

    @ViewBuilder
    private func runs(_ breakdown: ScoreBreakdown) -> some View {
        if let title = breakdown.runsTitle, breakdown.runs.count >= 2 {
            VStack(alignment: .leading, spacing: DPSpace.s2) {
                sectionTitle(title)
                RunsSparkline(runs: breakdown.runs)
                    .frame(height: 64)
                    .padding(DPSpace.s4)
                    .dpCard()
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(breakdown.runsSummary ?? title)
            }
        }
    }

    private func links(scored: Bool) -> some View {
        VStack(alignment: .leading, spacing: DPSpace.s3) {
            NavigationLink { HowScoresWorkView(trips: trips, source: .breakdown) } label: {
                Label("How scores work", systemImage: "info.circle").font(DPFont.body.weight(.semibold))
                    .foregroundStyle(DPColor.accentText).frame(minHeight: DPLayout.touchMin)
            }
            if scored {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Braked for a good reason?").font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.text)
                    Text("Open the event and choose “Was it needed?”. It won’t count.").font(DPFont.meta).foregroundStyle(DPColor.text2)
                }
                .accessibilityElement(children: .combine)
            }
        }
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text).font(DPFont.caption.weight(.semibold)).textCase(.uppercase).foregroundStyle(DPColor.text2)
            .accessibilityAddTraits(.isHeader)
    }
}

/// The route's last runs as a line ending at this drive.
private struct RunsSparkline: View {
    let runs: [Int]

    var body: some View {
        GeometryReader { geo in
            let low = Double((runs.min() ?? 0) - 3)
            let high = Double((runs.max() ?? 100) + 3)
            let step = geo.size.width / CGFloat(max(runs.count - 1, 1))
            let points = runs.enumerated().map { i, v in
                CGPoint(x: CGFloat(i) * step, y: geo.size.height * CGFloat(1 - (Double(v) - low) / (high - low)))
            }
            Path { path in
                guard let first = points.first else { return }
                path.move(to: first)
                points.dropFirst().forEach { path.addLine(to: $0) }
            }
            .stroke(DPColor.accent, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            if let last = points.last {
                Circle().fill(DPColor.accent).frame(width: 8, height: 8).position(last)
            }
        }
    }
}

/// S-SCO-02 How scores work (DRIVE-113, DRIVE-108): the example ring, each section in order, the version with its date.
struct HowScoresWorkView: View {
    let trips: TripListViewModel
    let source: ExplainerSource

    var body: some View {
        ScrollView {
            if let explainer = trips.explainer {
                VStack(alignment: .leading, spacing: DPLayout.sectionGap) {
                    hero(explainer)
                    ForEach(Array(explainer.sections.enumerated()), id: \.offset) { _, section in
                        sectionView(section, explainer: explainer)
                    }
                    Text(explainer.versionLine).font(DPFont.caption).foregroundStyle(DPColor.text3)
                        .frame(maxWidth: .infinity)
                        .accessibilityIdentifier("howScores.version")
                }
                .padding(.horizontal, DPLayout.screenMargin)
                .padding(.vertical, DPSpace.s2)
            }
        }
        .dpScreenBackground()
        .navigationTitle("How scores work")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { trips.explainerOpened(from: source) }
    }

    private func hero(_ explainer: HowScoresWork) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: DPSpace.s4) { heroRing(explainer); heroText(explainer) }
            VStack(alignment: .leading, spacing: DPSpace.s3) { heroRing(explainer); heroText(explainer) }
        }
        .padding(DPSpace.s4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .dpCard(radius: DPRadius.cardHero)
    }

    private func heroRing(_ explainer: HowScoresWork) -> some View {
        ScoreRing(score: explainer.exampleScore, baseline: explainer.exampleBaseline, diameter: 132)
            .accessibilityHidden(false)
            .accessibilityLabel(explainer.exampleAccessibility)
    }

    private func heroText(_ explainer: HowScoresWork) -> some View {
        VStack(alignment: .leading, spacing: DPSpace.s1_5) {
            Text(explainer.heroTitle).font(DPFont.cardTitle).foregroundStyle(DPColor.text).accessibilityAddTraits(.isHeader)
            Text(explainer.heroBody).font(DPFont.bodySecondary).foregroundStyle(DPColor.text)
                .fixedSize(horizontal: false, vertical: true)
            Text(explainer.notchKey).font(DPFont.caption).foregroundStyle(DPColor.text2)
        }
    }

    private func sectionView(_ section: ExplainerSection, explainer: HowScoresWork) -> some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            Text(section.title).font(DPFont.caption.weight(.semibold)).textCase(.uppercase).foregroundStyle(DPColor.text2)
                .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: DPSpace.s2) {
                ForEach(section.paragraphs, id: \.self) { paragraph in
                    Text(paragraph).font(DPFont.bodySecondary).foregroundStyle(DPColor.text)
                        .fixedSize(horizontal: false, vertical: true)
                }
                switch section.kind {
                case .events:
                    ForEach(explainer.thresholds, id: \.type) { threshold in
                        HStack(spacing: DPSpace.s3) {
                            DPEventGlyph(threshold.type.glyph, size: 28)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(threshold.label).font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.text)
                                Text(threshold.text).font(DPFont.meta).foregroundStyle(DPColor.text2)
                            }
                        }
                        .accessibilityElement(children: .combine)
                    }
                case .quality:
                    HStack(spacing: DPSpace.s3) {
                        ForEach([QualityBadge.good, .usable, .limited, .unscorable], id: \.self) { badge in
                            QualityBadgeLabel(badge: badge, word: badge.word)
                                .accessibilityElement(children: .combine)
                        }
                    }
                case .provenance:
                    ForEach(explainer.provenance, id: \.provenance) { line in
                        HStack(alignment: .top, spacing: DPSpace.s3) {
                            DPProvenanceGlyph(line.provenance.glyph, size: 18).foregroundStyle(DPColor.text)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(line.label).font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.text)
                                Text(line.meaning).font(DPFont.meta).foregroundStyle(DPColor.text2)
                            }
                        }
                        .accessibilityElement(children: .combine)
                    }
                case .text:
                    EmptyView()
                }
                if let note = section.note {
                    Text(note).font(DPFont.caption).foregroundStyle(DPColor.text2).fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(DPSpace.s4)
            .frame(maxWidth: .infinity, alignment: .leading)
            .dpCard()
        }
    }
}

extension QualityBadge {
    /// "Good"
    var word: String {
        switch self {
        case .good: "Good"
        case .usable: "Usable"
        case .limited: "Limited"
        case .unscorable: "Unscorable"
        }
    }
}
