import DPDesignSystem
import DPSharedBridge
import SwiftUI

// The score components of the Drives screens (components.md §3, §4, §9.2, §12, §18). They live in DPFeatures/Drives,
// not the design system: the token files are frozen in wave 1 and each session keeps its additions in its own folder.

/// The Smoothness ring (components.md §3): track, gradient arc from 12 o'clock, the baseline notch. A nil `score` draws
/// the unscored ring: a dashed track with "—" and no arc.
struct ScoreRing: View {
    let score: Int?
    let baseline: Int?
    var diameter: CGFloat = 124
    var showsLabel = false

    private var stroke: CGFloat { diameter * 0.1 }

    var body: some View {
        ZStack {
            Circle()
                .stroke(
                    DPColor.fillStrong,
                    style: StrokeStyle(lineWidth: stroke, dash: score == nil ? [stroke, stroke] : []))
            if let score {
                Circle()
                    .trim(from: 0, to: CGFloat(score) / 100)
                    .stroke(
                        AngularGradient(colors: [DPColor.accent.opacity(0.65), DPColor.accent, DPColor.accent.opacity(0.85)], center: .center),
                        style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                if let baseline {
                    Capsule()
                        .fill(DPColor.text2)
                        .frame(width: 2, height: stroke * 1.5)
                        .offset(y: -diameter / 2)
                        .rotationEffect(.degrees(Double(baseline) / 100 * 360))
                }
            }
            VStack(spacing: 0) {
                Text(score.map(String.init) ?? "—")
                    .dpNumeral(diameter >= 160 ? .hero : diameter >= 96 ? .xl : .m)
                    .foregroundStyle(DPColor.text)
                if showsLabel {
                    Text("Smoothness").font(DPFont.caption).foregroundStyle(DPColor.text2)
                }
            }
        }
        .padding(stroke / 2)
        .frame(width: diameter, height: diameter)
        .accessibilityHidden(true)
    }
}

/// The comparison chip (components.md §4): better in Glide, lower neutral grey (never red), dots while calibrating.
struct ComparisonChip: View {
    let comparison: ScoreComparison

    private var isBetter: Bool { comparison.tone == .better }

    var body: some View {
        HStack(spacing: DPSpace.s1_5) {
            if !comparison.glyph.isEmpty {
                Text(comparison.glyph).font(DPFont.caption).accessibilityHidden(true)
            }
            Text(comparison.text).font(DPFont.caption.weight(.semibold))
                .fixedSize(horizontal: false, vertical: true)
            if comparison.tone == .calibrating, comparison.calibratingOf > 0 {
                HStack(spacing: 3) {
                    ForEach(0..<comparison.calibratingOf, id: \.self) { i in
                        Circle().fill(i < comparison.calibratingK ? DPColor.accent : DPColor.fillStrong).frame(width: 6, height: 6)
                    }
                }
                .accessibilityHidden(true)
            }
        }
        .foregroundStyle(isBetter ? DPColor.accentText : DPColor.text2)
        .padding(.horizontal, DPSpace.s2)
        .padding(.vertical, DPSpace.s1)
        .frame(minHeight: 28)
        .background(isBetter ? DPColor.accentContainer.opacity(0.4) : DPColor.fill, in: Capsule())
    }
}

/// A trip card's score badge (components.md §12): the mini ring with the score, the mini delta under it.
struct ScoreBadgeView: View {
    let badge: ScoreBadge

    var body: some View {
        if let score = badge.score {
            VStack(spacing: 2) {
                ZStack {
                    Circle().stroke(DPColor.fillStrong, lineWidth: 4)
                    if let value = Int(score) {
                        Circle().trim(from: 0, to: CGFloat(value) / 100)
                            .stroke(DPColor.accent, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                    }
                    Text(score).dpNumeral(.s).foregroundStyle(DPColor.text)
                }
                .frame(width: 46, height: 46)
                HStack(spacing: 2) {
                    if badge.isNewRecord {
                        Image(systemName: "rosette").font(DPFont.caption).foregroundStyle(DPColor.record)
                    }
                    if let delta = badge.delta {
                        Text(delta).font(DPFont.caption.weight(.semibold)).monospacedDigit()
                            .foregroundStyle(badge.tone == .better ? DPColor.accentText : DPColor.text2)
                    }
                }
            }
            .accessibilityHidden(true)
        } else if let text = badge.notScoredText {
            Text(text).font(DPFont.caption.weight(.semibold)).foregroundStyle(DPColor.text2).accessibilityHidden(true)
        }
    }
}

/// A contributor row (components.md §18): label, band chip ▲/●/▼ with words, reason.
struct ContributorRowView: View {
    let contributor: ScoreContributor

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s1) {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: DPSpace.s2) { label; chip }
                VStack(alignment: .leading, spacing: DPSpace.s1) { label; chip }
            }
            Text(contributor.reason).font(DPFont.meta).foregroundStyle(DPColor.text2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var label: some View {
        Text(contributor.label).font(DPFont.bodySecondary.weight(.semibold)).foregroundStyle(DPColor.text)
    }

    @ViewBuilder private var chip: some View {
        if let text = contributor.bandText {
            let better = contributor.band == .smoother
            Text("\(contributor.bandGlyph ?? "") \(text)")
                .font(DPFont.caption.weight(.semibold))
                .foregroundStyle(better ? DPColor.accentText : DPColor.text2)
                .padding(.horizontal, DPSpace.s2).padding(.vertical, 2)
                .background(better ? DPColor.accentContainer.opacity(0.4) : DPColor.fill, in: Capsule())
        }
    }
}

/// An event row (components.md §9.2): glyph by shape, label and time, peak g and duration, its tag.
struct ScoreEventRowView: View {
    let event: ScoreEventRow

    var body: some View {
        HStack(spacing: DPSpace.s3) {
            DPEventGlyph(event.type.glyph, needed: event.isNeeded, size: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(event.label).font(DPFont.body).foregroundStyle(DPColor.text)
                Text(event.time).font(DPFont.meta).foregroundStyle(DPColor.text2)
            }
            .opacity(event.isExcluded ? 0.6 : 1)
            Spacer(minLength: DPSpace.s2)
            VStack(alignment: .trailing, spacing: 2) {
                Text(event.meta).dpNumeral(.xs).foregroundStyle(DPColor.text)
                    .strikethrough(event.isNeeded)
                if let tag = event.tag {
                    Text(tag).font(DPFont.caption).foregroundStyle(DPColor.text2)
                }
            }
        }
        .frame(minHeight: DPLayout.touchMin)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(event.accessibilityLabel)
    }
}

/// The tip card (components.md §18): "One thing for next time", the tip, why it helps.
struct TipCardView: View {
    let tip: ScoreTip

    var body: some View {
        VStack(alignment: .leading, spacing: DPSpace.s1_5) {
            Text(tip.kicker).font(DPFont.caption.weight(.semibold)).foregroundStyle(DPColor.text2)
            Text(tip.text).font(DPFont.body).foregroundStyle(DPColor.text)
                .fixedSize(horizontal: false, vertical: true)
            Text(tip.why).font(DPFont.meta).foregroundStyle(DPColor.text2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(DPSpace.s4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DPColor.accent.opacity(0.1), in: RoundedRectangle(cornerRadius: DPRadius.card))
        .accessibilityElement(children: .combine)
    }
}

extension DrivingEventType {
    var glyph: DPEventGlyph.Kind {
        switch self {
        case .brake: .brake
        case .accel: .accel
        case .corner: .corner
        }
    }
}
