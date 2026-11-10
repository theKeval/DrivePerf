import ActivityKit
import AppIntents
import DPDesignSystem
import SwiftUI
import WidgetKit

/// Widget extension: the manual-trip Live Activity (S-LIVE-01 Lock Screen, S-LIVE-02 Dynamic Island). It links the
/// design system only, never the Kotlin framework (extension memory limits, ADR-002 §2). The Control Center control
/// and widgets (S-LIVE-03/04) arrive with DRIVE-133.
@main
struct DrivePerfWidgetsBundle: WidgetBundle {
    var body: some Widget {
        RecordingLiveActivity()
    }
}

struct RecordingLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: RecordingActivityAttributes.self) { context in
            LockScreenView(state: context.state)
                .activityBackgroundTint(.black.opacity(0.55))
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Text(context.state.ended ? "Drive decoded." : "Recording")
                        .font(.system(size: 15, weight: .semibold))
                }
                DynamicIslandExpandedRegion(.trailing) {
                    if !context.state.ended {
                        Text(timerInterval: context.state.startedAt...Date.distantFuture, countsDown: false)
                            .font(.system(size: 28, weight: .semibold).width(.expanded))
                            .monospacedDigit()
                            .multilineTextAlignment(.trailing)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    if !context.state.ended {
                        HStack {
                            DistanceText(state: context.state, size: 28)
                            Spacer()
                            StopButton()
                        }
                    }
                }
            } compactLeading: {
                LoopGlyph(size: 20).foregroundStyle(DPColor.accent)
            } compactTrailing: {
                if context.state.ended {
                    Image(systemName: "checkmark").foregroundStyle(DPColor.accent)
                } else {
                    Text(timerInterval: context.state.startedAt...Date.distantFuture, countsDown: false)
                        .font(.system(size: 15, weight: .semibold).width(.expanded))
                        .monospacedDigit()
                        .foregroundStyle(DPColor.accent)
                        .frame(maxWidth: 64)
                }
            } minimal: {
                LoopGlyph(size: 18).foregroundStyle(DPColor.accent)
            }
            .keylineTint(DPColor.accent)
        }
    }
}

private struct LockScreenView: View {
    let state: RecordingActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                LoopGlyph(size: 22).foregroundStyle(DPColor.accent)
                Text(state.ended ? "Drive decoded." : "Recording").font(.system(size: 15, weight: .semibold))
                Spacer()
                if !state.ended { StopButton() }
            }
            if state.ended {
                Text("Open to see your drive.").font(.subheadline).foregroundStyle(.white.opacity(0.7))
            } else {
                HStack(alignment: .firstTextBaseline, spacing: 20) {
                    Text(timerInterval: state.startedAt...Date.distantFuture, countsDown: false)
                        .font(.system(size: 32, weight: .semibold).width(.expanded))
                        .monospacedDigit()
                    DistanceText(state: state, size: 32)
                }
            }
        }
        .foregroundStyle(.white)
        .padding(16)
    }
}

private struct DistanceText: View {
    let state: RecordingActivityAttributes.ContentState
    let size: CGFloat

    var body: some View {
        if state.hasFix {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(String(format: "%.1f", state.distanceKm))
                    .font(.system(size: size, weight: .semibold).width(.expanded))
                    .monospacedDigit()
                Text("km").font(.system(size: size * 0.5, weight: .medium)).opacity(0.65)
            }
        } else {
            Text("Finding GPS…").font(.subheadline).opacity(0.7)
        }
    }
}

private struct StopButton: View {
    var body: some View {
        Button(intent: StopTripIntent()) {
            Label("Stop", systemImage: "stop.fill")
                .font(.system(size: 14, weight: .semibold))
                .padding(.horizontal, 12)
                .frame(minHeight: 44)
        }
        .buttonStyle(.plain)
        .background(.white.opacity(0.17), in: Capsule())
        .accessibilityLabel("Stop the drive")
    }
}
