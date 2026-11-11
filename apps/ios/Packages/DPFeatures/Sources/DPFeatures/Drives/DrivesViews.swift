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
                    if trips.trips.isEmpty {
                        EmptyDrivesCard(onHowItWorks: onCaptureStatus)
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
                            TripCardStack(rows: trips.latest)
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
                        TripCardStack(rows: section.rows)
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
    }
}

/// Trip cards stacked in one card with 0.5 pt separators (components.md §12).
struct TripCardStack: View {
    let rows: [TripRow]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                if index > 0 {
                    Rectangle().fill(DPColor.separator).frame(height: 0.5).padding(.leading, 76)
                }
                TripRowView(row: row)
            }
        }
        .dpCard()
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
                if let note = row.note {
                    Text(note).font(DPFont.caption).foregroundStyle(DPColor.text3).padding(.top, DPSpace.s0_5)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(DPSpace.s4)
        .frame(minHeight: DPLayout.listRowMin)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(row.accessibilityLabel)
    }
}
