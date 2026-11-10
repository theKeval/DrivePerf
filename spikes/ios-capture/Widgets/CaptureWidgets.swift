import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

@main
struct CaptureWidgetsBundle: WidgetBundle {
    var body: some Widget {
        ManualTripControl()
        TripLiveActivity()
    }
}

struct ManualTripControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "com.thekeval.spike.capture.manual") {
            ControlWidgetButton(action: ToggleManualTripIntent()) {
                Label("Spike drive", systemImage: "car.fill")
            }
        }
        .displayName("Spike drive")
        .description("Start or stop a manual spike recording.")
    }
}

struct TripLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CaptureActivityAttributes.self) { context in
            HStack {
                VStack(alignment: .leading) {
                    Text("Spike recording").font(.headline)
                    Text(context.state.startedAt, style: .timer).monospacedDigit()
                }
                Spacer()
                Button(intent: ToggleManualTripIntent()) {
                    Label("Stop", systemImage: "stop.fill")
                }
            }
            .padding()
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label("Spike", systemImage: "car.fill")
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(context.state.startedAt, style: .timer).monospacedDigit()
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Button(intent: ToggleManualTripIntent()) {
                        Label("Stop", systemImage: "stop.fill")
                    }
                }
            } compactLeading: {
                Image(systemName: "car.fill")
            } compactTrailing: {
                Text(context.state.startedAt, style: .timer).monospacedDigit().frame(maxWidth: 44)
            } minimal: {
                Image(systemName: "car.fill")
            }
        }
    }
}
