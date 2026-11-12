import DPDesignSystem
import DPSharedBridge
import SwiftUI

/// S-HOME-01 Drives tab root: the first-run empty state, then the latest drives and "All drives".
struct DrivesHomeView: View {
    let trips: TripListViewModel
    let capture: CaptureViewModel
    let onProfile: () -> Void
    let onCaptureStatus: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: DPLayout.sectionGap) {
                    if case .attention(let reason) = capture.accessory {
                        AutoDetectAttentionBanner(reason: reason, onFix: onCaptureStatus)
                    }
                    if trips.trips.isEmpty {
                        EmptyDrivesCard(onHowItWorks: onCaptureStatus)
                        if !trips.notDrives.isEmpty {
                            // All drives (and its Not drives link) needs a drive; marked trips must stay restorable.
                            NavigationLink { NotDrivesView(trips: trips) } label: {
                                HStack {
                                    Text("Not drives").font(DPFont.body.weight(.semibold))
                                    Spacer()
                                    Text("\(trips.notDrives.count)").font(DPFont.body).foregroundStyle(DPColor.text2)
                                    Image(systemName: "chevron.right").font(DPFont.caption).foregroundStyle(DPColor.text3)
                                        .accessibilityHidden(true)
                                }
                                .foregroundStyle(DPColor.text)
                                .padding(.horizontal, DPSpace.s4)
                                .frame(minHeight: DPLayout.touchMin)
                                .dpCard()
                            }
                            .accessibilityHint("Trips you marked as not a drive, kept for 30 days")
                        }
                    } else {
                        VStack(alignment: .leading, spacing: DPSpace.s3) {
                            HStack {
                                Text("Latest drives").font(DPFont.sectionTitle).foregroundStyle(DPColor.text)
                                    .accessibilityAddTraits(.isHeader)
                                Spacer()
                                NavigationLink("All drives") { AllDrivesView(trips: trips) }
                                    .font(DPFont.body.weight(.semibold))
                                    .foregroundStyle(DPColor.accentText)
                            }
                            TripCardStack(rows: trips.latest, trips: trips)
                        }
                    }
                }
                .padding(.horizontal, DPLayout.screenMargin)
                .padding(.vertical, DPSpace.s2)
            }
            .dpScreenBackground()
            .navigationTitle("Drives")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { ProfileAvatarButton(action: onProfile) }
            }
        }
    }
}

/// S-HOME-01 state banner "needs attention": what stopped auto-detect, and the way to fix it (the status sheet).
private struct AutoDetectAttentionBanner: View {
    let reason: AttentionReason
    let onFix: () -> Void

    var body: some View {
        HStack(spacing: DPSpace.s3) {
            GlyphDisc(.info, tone: .warning, size: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text("Auto-detect needs attention").font(DPFont.body.weight(.semibold)).foregroundStyle(DPColor.text)
                Text(reason.shortText).font(DPFont.bodySecondary).foregroundStyle(DPColor.text2)
            }
            Spacer(minLength: DPSpace.s2)
            Button("Fix", action: onFix)
                .font(DPFont.body.weight(.semibold))
                .foregroundStyle(DPColor.accentText)
                .frame(minHeight: DPLayout.touchMin)
        }
        .padding(DPSpace.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DPColor.warningContainer, in: RoundedRectangle(cornerRadius: DPRadius.md))
        .accessibilityElement(children: .contain)
    }
}

private struct EmptyDrivesCard: View {
    let onHowItWorks: () -> Void

    var body: some View {
        VStack(spacing: DPSpace.s4) {
            LoopMark(size: 88)
            Text("Your drives will appear here.").font(DPFont.sectionTitle).foregroundStyle(DPColor.text)
            Text("Just drive. DrivePerf notices when you set off and decodes each drive when you park.")
                .font(DPFont.bodySecondary)
                .foregroundStyle(DPColor.text2)
            Button("How capture works", action: onHowItWorks)
                .font(DPFont.body.weight(.semibold))
                .foregroundStyle(DPColor.accentText)
                .frame(minHeight: DPLayout.touchMin)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, DPSpace.s6)
        .padding(.vertical, DPSpace.s10)
        .frame(maxWidth: .infinity)
        .dpCard(radius: DPRadius.cardHero)
    }
}

/// S-HIS-01 All drives: grouped by week, newest first.
struct AllDrivesView: View {
    let trips: TripListViewModel

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: DPLayout.sectionGap) {
                ForEach(trips.sections) { section in
                    VStack(alignment: .leading, spacing: DPSpace.s2) {
                        HStack {
                            Text(section.title).font(DPFont.caption.weight(.semibold)).textCase(.uppercase)
                            Spacer()
                            Text(section.meta).font(DPFont.caption)
                        }
                        .foregroundStyle(DPColor.text2)
                        .padding(.horizontal, DPSpace.s1)
                        .accessibilityElement(children: .combine)
                        TripCardStack(rows: section.rows, trips: trips)
                    }
                }
                if let first = trips.trips.last {
                    Text("That’s every drive since \(first.startedAt.formatted(.dateTime.day().month(.wide))).")
                        .font(DPFont.meta).foregroundStyle(DPColor.text3)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, DPLayout.screenMargin)
            .padding(.vertical, DPSpace.s2)
        }
        .dpScreenBackground()
        .navigationTitle("All drives")
        .navigationSubtitle(trips.countText)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if trips.canCorrect {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink("Not drives") { NotDrivesView(trips: trips) }
                        .accessibilityHint("Trips you marked as not a drive, kept for 30 days")
                }
            }
        }
    }
}

/// The "Not drives" filter (F-CAP-04 AC3): trips the user marked as a bus, bike, walk or other. Out of scores and
/// history; each is deleted 30 days after it was marked, and can be restored as a drive until then.
struct NotDrivesView: View {
    let trips: TripListViewModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DPSpace.s3) {
                Text("Trips you marked as not a drive. They don’t count anywhere and are deleted 30 days after you "
                    + "marked them. Restore one if it was a drive after all.")
                    .font(DPFont.meta).foregroundStyle(DPColor.text2)
                if trips.notDrives.isEmpty {
                    Text("Nothing here.").font(DPFont.body).foregroundStyle(DPColor.text3)
                        .frame(maxWidth: .infinity, minHeight: DPLayout.touchMin * 2)
                } else {
                    TripCardStack(rows: trips.notDriveRows, trips: trips)
                }
            }
            .padding(.horizontal, DPLayout.screenMargin)
            .padding(.vertical, DPSpace.s2)
        }
        .dpScreenBackground()
        .navigationTitle("Not drives")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Trip cards stacked in one card with 0.5 pt separators (components.md §12). Each card opens the trip detail; an open
/// "Was this a bus or train?" question is answered under its card, and long-press offers "What was this?".
struct TripCardStack: View {
    let rows: [TripRow]
    let trips: TripListViewModel

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                if index > 0 {
                    Rectangle().fill(DPColor.separator).frame(height: 0.5).padding(.leading, 76)
                }
                NavigationLink {
                    TripDetailView(trips: trips, tripId: row.id)
                } label: {
                    TripRowView(row: row)
                }
                .buttonStyle(.plain)
                .contextMenu {
                    if trips.canCorrect { CorrectionMenu(trips: trips, tripId: row.id) }
                }
                if let question = row.question, trips.canCorrect {
                    TripQuestionBar(question: question) { yes in
                        Task { await trips.answer(tripId: row.id, yes: yes) }
                    }
                    .padding(.horizontal, DPSpace.s4)
                    .padding(.bottom, DPSpace.s3)
                }
            }
        }
        .dpCard()
    }
}

/// "What was this?" (S-TRP-02 type picker, F-CAP-04): one tap corrects the trip.
struct CorrectionMenu: View {
    let trips: TripListViewModel
    let tripId: String

    var body: some View {
        Menu("What was this?") {
            ForEach(TripCorrection.allCases, id: \.self) { correction in
                Button(Self.title(correction)) {
                    Task { await trips.correct(tripId: tripId, to: correction) }
                }
            }
        }
    }

    static func title(_ c: TripCorrection) -> String {
        switch c {
        case .driver: "I was driving"
        case .passenger: "I was a passenger"
        case .transit: "Bus or train"
        case .bike: "Bike ride"
        case .walk: "Walk"
        case .other: "Something else"
        }
    }
}

/// "Was this a bus or train?" with Yes / No (F-CAP-04 AC2–3).
struct TripQuestionBar: View {
    let question: TripQuestion
    let onAnswer: (Bool) -> Void

    var body: some View {
        HStack(spacing: DPSpace.s3) {
            Text(question == .transit ? "Was this a bus or train?" : "Was this a bike ride?")
                .font(DPFont.bodySecondary.weight(.semibold)).foregroundStyle(DPColor.text)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button("Yes") { onAnswer(true) }
                .buttonStyle(.bordered)
                .accessibilityHint("Takes it out of your scores and history")
            Button("No") { onAnswer(false) }
                .buttonStyle(.borderedProminent)
                .accessibilityHint("Keeps it as a drive")
        }
        .frame(minHeight: DPLayout.touchMin)
        .padding(DPSpace.s3)
        .background(DPColor.fill, in: RoundedRectangle(cornerRadius: DPRadius.md))
    }
}

struct TripRowView: View {
    let row: TripRow

    var body: some View {
        HStack(alignment: .top, spacing: DPSpace.s3) {
            DPIcon.drives.image.resizable().scaledToFit()
                .frame(width: 26, height: 26)
                .foregroundStyle(DPColor.accent)
                .frame(width: 48, height: 48)
                .background(DPColor.accentContainer.opacity(0.5), in: RoundedRectangle(cornerRadius: DPRadius.md))
            VStack(alignment: .leading, spacing: DPSpace.s0_5) {
                Text(row.title).font(DPFont.cardTitle).foregroundStyle(DPColor.text)
                Text(row.subtitle).font(DPFont.meta).foregroundStyle(DPColor.text2)
                HStack(spacing: DPSpace.s2) {
                    Text(row.distance)
                    Text("·")
                    Text(row.duration)
                }
                .font(.footnote.weight(.semibold).width(.expanded))
                .monospacedDigit()
                .foregroundStyle(DPColor.text)
                HStack(spacing: DPSpace.s2) {
                    if let badge = row.quality { QualityBadgeLabel(badge: badge) }
                    if row.isInterrupted { TagLabel(text: "Interrupted") }
                    if let kind = row.kindLabel { TagLabel(text: kind) }
                }
                .padding(.top, DPSpace.s1)
                if let note = row.note {
                    Text(note).font(DPFont.caption).foregroundStyle(DPColor.text3).padding(.top, DPSpace.s0_5)
                }
            }
            Spacer(minLength: 0)
            DPIcon.chevronRight.image.resizable().scaledToFit().frame(width: 12, height: 12)
                .foregroundStyle(DPColor.text3).padding(.top, DPSpace.s4)
        }
        .padding(DPSpace.s4)
        .frame(minHeight: DPLayout.listRowMin)
        .opacity(row.isDimmed ? 0.6 : 1)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(row.accessibilityLabel)
    }
}

/// The compact quality badge on cards: glyph + word, never colour alone (components.md §10.1).
struct QualityBadgeLabel: View {
    let badge: QualityBadge

    var body: some View {
        HStack(spacing: DPSpace.s1) {
            DPQualityGlyph(badge.glyph, size: 14)
            Text(TripPresenter.badgeWord(badge)).font(DPFont.caption.weight(.semibold)).foregroundStyle(DPColor.text2)
        }
    }
}

struct TagLabel: View {
    let text: String

    var body: some View {
        Text(text).font(DPFont.caption.weight(.semibold)).foregroundStyle(DPColor.text2)
            .padding(.horizontal, DPSpace.s2).padding(.vertical, DPSpace.s0_5)
            .background(DPColor.fill, in: Capsule())
    }
}

extension QualityBadge {
    var glyph: DPQualityGlyph.Kind {
        switch self {
        case .good: .good
        case .usable: .usable
        case .limited: .limited
        case .unscorable: .unscorable
        }
    }
}

extension Provenance {
    var glyph: DPProvenanceGlyph.Kind {
        switch self {
        case .observed: .observed
        case .derived: .derived
        case .estimated: .estimated
        }
    }
}
