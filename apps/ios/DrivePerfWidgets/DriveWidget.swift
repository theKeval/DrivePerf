import DPDesignSystem
import SwiftUI
import WidgetKit

struct DriveWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: DriveWidgetSnapshot.widgetKind, provider: DriveWidgetProvider()) { entry in
            DriveWidgetView(snapshot: entry.snapshot)
                .containerBackground(DPColor.surface, for: .widget)
        }
        .configurationDisplayName("DrivePerf")
        .description("Start recording and see the Smoothness of your last drive.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular])
    }
}

private struct DriveWidgetEntry: TimelineEntry {
    let date: Date
    let snapshot: DriveWidgetSnapshot
}

private struct DriveWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> DriveWidgetEntry {
        DriveWidgetEntry(
            date: .now, snapshot: .init(latestDrive: .init(partOfDay: "Morning", distanceM: 37_900, smoothness: 91, baseline: 84)))
    }

    func getSnapshot(in context: Context, completion: @escaping (DriveWidgetEntry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : DriveWidgetEntry(date: .now, snapshot: .read()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<DriveWidgetEntry>) -> Void) {
        completion(Timeline(entries: [DriveWidgetEntry(date: .now, snapshot: .read())], policy: .never))
    }
}

private struct DriveWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let snapshot: DriveWidgetSnapshot

    var body: some View {
        switch family {
        case .accessoryCircular:
            circular
        case .accessoryRectangular:
            HStack(spacing: 6) {
                accessorySummary
                    .frame(maxWidth: .infinity, alignment: .leading)
                actionButton(iconOnly: true)
            }
            .widgetAccentable()
        default:
            ViewThatFits(in: .horizontal) {
                if family == .systemMedium, let latest = idleDrive, latest.smoothness != nil {
                    HStack(spacing: DPSpace.s4) {
                        scoreRing(latest, size: .l).frame(width: 112, height: 112)
                        homeDetails(showsScore: false)
                    }
                } else if family == .systemSmall, let latest = idleDrive, latest.smoothness != nil {
                    VStack(alignment: .leading, spacing: DPSpace.s2) {
                        lastDriveLabel
                        HStack {
                            scoreRing(latest, size: .m).frame(width: 64, height: 64)
                            Spacer(minLength: 0)
                            actionButton(iconOnly: true)
                        }
                        Text(LocalizedStringKey(latest.partOfDay)).font(DPFont.meta)
                        if let delta = latest.delta { comparison(delta, compact: false) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                homeDetails(showsScore: true)
            }
            .foregroundStyle(DPColor.text)
        }
    }

    private var idleDrive: DriveWidgetSnapshot.LatestDrive? {
        snapshot.recording == nil && snapshot.canRecord != false ? snapshot.latestDrive : nil
    }

    private var circular: some View {
        VStack(spacing: 0) {
            if let active = snapshot.recording {
                Text(active.startedAt, style: .timer)
                    .font(.caption2.monospacedDigit())
                    .minimumScaleFactor(0.65)
            } else if let score = idleDrive?.smoothness {
                Text(score, format: .number)
                    .font(.caption.monospacedDigit().bold())
                    .accessibilityLabel("Smoothness \(score)")
                if let delta = idleDrive?.delta { comparison(delta, compact: true) }
            }
            actionButton(iconOnly: true)
        }
        .widgetAccentable()
    }

    private var lastDriveLabel: some View {
        Label {
            Text("Last drive").font(DPFont.caption).foregroundStyle(DPColor.text2)
        } icon: {
            LoopGlyph(size: 14).foregroundStyle(DPColor.accent)
        }
    }

    private var accessorySummary: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let active = snapshot.recording {
                Text("Recording").font(.caption.bold())
                HStack(spacing: 3) {
                    Text(timerInterval: active.startedAt...Date.distantFuture, countsDown: false).monospacedDigit()
                    distance(active.distanceM)
                }
                .font(.caption2)
            } else if let latest = idleDrive {
                if let score = latest.smoothness {
                    Text("Last drive · \(score)").font(.caption.bold())
                } else {
                    Text("Last drive").font(.caption.bold())
                }
                HStack(spacing: 3) {
                    Text(LocalizedStringKey(latest.partOfDay)).font(.caption2)
                    if let delta = latest.delta { comparison(delta, compact: true) }
                }
            } else {
                Text(snapshot.canRecord == false ? "Location needed" : "No drives yet").font(.caption.bold())
            }
        }
        .lineLimit(1)
        .minimumScaleFactor(0.7)
        .accessibilityElement(children: .combine)
    }

    private func homeDetails(showsScore: Bool) -> some View {
        VStack(alignment: .leading, spacing: DPSpace.s2) {
            VStack(alignment: .leading, spacing: 2) {
                if let active = snapshot.recording {
                    Label("Recording", systemImage: "record.circle").font(DPFont.caption)
                    Text(timerInterval: active.startedAt...Date.distantFuture, countsDown: false)
                        .dpNumeral(.l)
                        .multilineTextAlignment(.leading)
                    distance(active.distanceM).font(DPFont.caption)
                } else if snapshot.canRecord == false {
                    Label("Location needed", systemImage: "location.slash")
                        .font(DPFont.cardTitle)
                } else if let latest = idleDrive {
                    lastDriveLabel
                    Text(LocalizedStringKey(latest.partOfDay)).font(DPFont.cardTitle)
                    if showsScore, let score = latest.smoothness {
                        Text("Smoothness \(score)").font(DPFont.caption)
                    } else {
                        distance(latest.distanceM).font(DPFont.caption)
                    }
                    if let delta = latest.delta {
                        comparison(delta, compact: false)
                    } else if latest.smoothness == nil {
                        Text("Smoothness unavailable").font(DPFont.caption)
                    } else {
                        Text("Baseline pending").font(DPFont.caption)
                    }
                } else {
                    LoopGlyph(size: 18).foregroundStyle(DPColor.accent)
                    Text("No drives yet").font(DPFont.cardTitle)
                }
            }
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .accessibilityElement(children: .combine)
            .frame(maxWidth: .infinity, alignment: .leading)
            actionButton(iconOnly: family == .systemSmall && snapshot.recording != nil)
                .frame(maxWidth: .infinity, alignment: family == .systemMedium ? .trailing : .leading)
        }
    }

    private func scoreRing(_ drive: DriveWidgetSnapshot.LatestDrive, size: DPNumeralSize) -> some View {
        let score = drive.smoothness ?? 0
        return ZStack {
            Circle().strokeBorder(DPColor.fillStrong, lineWidth: 6)
            Circle().trim(from: 0, to: Double(min(max(score, 0), 100)) / 100)
                .stroke(DPColor.accent.gradient, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .padding(3)
            if let baseline = drive.baseline {
                VStack(spacing: 0) {
                    Capsule().fill(DPColor.text2).frame(width: 2, height: 10)
                    Spacer(minLength: 0)
                }
                .rotationEffect(.degrees(Double(baseline) * 3.6))
            }
            Text(score, format: .number).dpNumeral(size).minimumScaleFactor(0.6)
        }
        .accessibilityLabel("Smoothness \(score)")
    }

    private func comparison(_ delta: Int, compact: Bool) -> some View {
        let change = delta.formatted(.number.sign(strategy: .always(includingZero: false)))
        return Text(compact ? "\(change) usual" : "\(change) vs usual")
            .font(compact ? .system(size: 9, weight: .semibold) : DPFont.caption.weight(.semibold))
            .foregroundStyle(delta > 0 ? DPColor.positive : DPColor.text2)
            .accessibilityLabel("\(change) compared with your usual Smoothness")
    }

    private func distance(_ metres: Int) -> Text {
        Text("\((Double(metres) / 1000).formatted(.number.precision(.fractionLength(1)))) km")
    }

    @ViewBuilder
    private func actionButton(iconOnly: Bool) -> some View {
        let action = snapshot.action
        let isAccessory = family == .accessoryCircular || family == .accessoryRectangular
        switch action {
        case .stop(let id):
            Button(intent: StopTripIntent(tripID: id)) { actionLabel(action, iconOnly: iconOnly) }
                .buttonStyle(.bordered)
        case .showLive(let id):
            Button(intent: StartTripIntent(tripID: id)) { actionLabel(action, iconOnly: iconOnly) }
                .buttonStyle(.bordered)
        case .start, .fixLocation:
            if isAccessory {
                Button(intent: StartTripIntent()) { actionLabel(action, iconOnly: true) }
                    .buttonStyle(.bordered)
            } else {
                Button(intent: StartTripIntent()) { actionLabel(action, iconOnly: iconOnly) }
                    .buttonStyle(.borderedProminent)
                    .tint(action == .start ? DPColor.accent : DPColor.warning)
            }
        }
    }

    private func actionLabel(_ action: DriveWidgetSnapshot.Action, iconOnly: Bool) -> some View {
        Label(action == .start && !iconOnly ? "Start" : action.title, systemImage: action.symbol)
            .labelStyle(DriveWidgetLabelStyle(iconOnly: iconOnly))
            .font(DPFont.caption.weight(.semibold))
            .lineLimit(1)
            .accessibilityLabel(Text(action.title))
    }
}

private struct DriveWidgetLabelStyle: LabelStyle {
    let iconOnly: Bool

    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 4) {
            configuration.icon
            if !iconOnly { configuration.title }
        }
    }
}
